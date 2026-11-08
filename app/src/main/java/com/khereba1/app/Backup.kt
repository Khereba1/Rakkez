package com.khereba1.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup file contains (read from its manifest). */
data class BackupInfo(val basic: Boolean, val ledger: Boolean, val createdAt: Long)

/**
 * Manual (and optional automatic-on-open) backups.
 * Files go to Download/Rakkez/backups through MediaStore (no storage permission needed).
 * Restore uses the system file picker (after a reinstall the app can no longer list its old files).
 */
object Backup {
    const val DIR = "Download/Rakkez/backups"
    private const val BASIC_FILE = "basic.json"
    private const val LEDGER_FILE = "ledger.json"
    private const val MANIFEST = "manifest.json"
    private val EXCLUDED_KEYS = setOf("timer", "last_auto")

    // ---------------- sizes shown in Settings ----------------

    fun basicSize(c: Context): Long = basicJson(c).toString().toByteArray(Charsets.UTF_8).size.toLong()

    fun ledgerSize(c: Context): Long = LedgerStore.fileSize(c)

    fun fmtSize(bytes: Long): String {
        return if (bytes >= 1024L * 1024L) {
            String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        } else {
            String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        }
    }

    // ---------------- create ----------------

    private fun basicJson(c: Context): JSONObject {
        val out = JSONObject()
        val all = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
        for ((k, v) in all) {
            if (EXCLUDED_KEYS.contains(k)) continue
            val o = JSONObject()
            when (v) {
                is String -> { o.put("t", "s"); o.put("v", v) }
                is Int -> { o.put("t", "i"); o.put("v", v) }
                is Long -> { o.put("t", "l"); o.put("v", v) }
                is Boolean -> { o.put("t", "b"); o.put("v", v) }
                is Float -> { o.put("t", "f"); o.put("v", v.toDouble()) }
                else -> continue
            }
            out.put(k, o)
        }
        return out
    }

    /** Returns the display name of the file that was written. Throws on failure. */
    fun create(c: Context, basic: Boolean, ledger: Boolean, auto: Boolean): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val name = (if (auto) "rakkez-auto-" else "rakkez-backup-") + stamp + ".zip"
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, DIR)
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val resolver = c.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create the backup file")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IllegalStateException("Could not open the backup file")
            ZipOutputStream(out).use { zip ->
                val manifest = JSONObject()
                manifest.put("app", "rakkez")
                manifest.put("v", 1)
                manifest.put("createdAt", System.currentTimeMillis())
                manifest.put("basic", basic)
                manifest.put("ledger", ledger)
                putEntry(zip, MANIFEST, manifest.toString().toByteArray(Charsets.UTF_8))
                if (basic) putEntry(zip, BASIC_FILE, basicJson(c).toString().toByteArray(Charsets.UTF_8))
                if (ledger) {
                    val f = File(c.filesDir, "ledger.json")
                    if (f.exists()) putEntry(zip, LEDGER_FILE, f.readBytes())
                }
            }
            val done = ContentValues()
            done.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, done, null, null)
        } catch (e: Throwable) {
            try {
                resolver.delete(uri, null, null)
            } catch (e2: Throwable) {
                // ignore
            }
            throw e
        }
        return name
    }

    private fun putEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    // ---------------- restore ----------------

    fun readInfo(c: Context, uri: Uri): BackupInfo? {
        return try {
            val input = c.contentResolver.openInputStream(uri) ?: return null
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                var info: BackupInfo? = null
                while (entry != null) {
                    if (entry.name == MANIFEST) {
                        val o = JSONObject(String(zip.readBytes(), Charsets.UTF_8))
                        if (o.optString("app") == "rakkez") {
                            info = BackupInfo(o.optBoolean("basic", false), o.optBoolean("ledger", false), o.optLong("createdAt", 0L))
                        }
                    }
                    entry = zip.nextEntry
                }
                info
            }
        } catch (e: Throwable) {
            AppLog.log("BACKUP", "Could not read backup", e)
            null
        }
    }

    /** Replaces the chosen parts with what is inside the backup. Returns true on success. */
    fun restore(c: Context, uri: Uri, basic: Boolean, ledger: Boolean): Boolean {
        return try {
            var basicText: String? = null
            var ledgerBytes: ByteArray? = null
            val input = c.contentResolver.openInputStream(uri) ?: return false
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == BASIC_FILE) basicText = String(zip.readBytes(), Charsets.UTF_8)
                    if (entry.name == LEDGER_FILE) ledgerBytes = zip.readBytes()
                    entry = zip.nextEntry
                }
            }
            if (ledger) {
                val lb = ledgerBytes
                if (lb != null) {
                    val tmp = File(c.filesDir, "ledger.json.restore")
                    tmp.writeBytes(lb)
                    val dst = File(c.filesDir, "ledger.json")
                    if (!tmp.renameTo(dst)) {
                        dst.writeBytes(lb)
                        tmp.delete()
                    }
                }
            }
            if (basic) {
                val bt = basicText
                if (bt != null) {
                    val o = JSONObject(bt)
                    val ed = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    val keys = o.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (EXCLUDED_KEYS.contains(k)) continue
                        val item = o.getJSONObject(k)
                        when (item.optString("t")) {
                            "s" -> ed.putString(k, item.optString("v", ""))
                            "i" -> ed.putInt(k, item.optInt("v", 0))
                            "l" -> ed.putLong(k, item.optLong("v", 0L))
                            "b" -> ed.putBoolean(k, item.optBoolean("v", false))
                            "f" -> ed.putFloat(k, item.optDouble("v", 0.0).toFloat())
                        }
                    }
                    ed.remove("timer")
                    ed.commit()
                }
            }
            true
        } catch (e: Throwable) {
            AppLog.log("BACKUP", "Restore failed", e)
            false
        }
    }

    // ---------------- optional automatic backup (runs when the app opens, at most once a day) ----------------

    fun autoIfDue(c: Context) {
        try {
            val st = Store.loadSettings(c)
            if (!st.autoBackup) return
            val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = p.getLong("last_auto", 0L)
            val now = System.currentTimeMillis()
            if (now - last < 24L * 60L * 60L * 1000L) return
            create(c, true, true, true)
            p.edit().putLong("last_auto", now).commit()
            pruneAuto(c, 7)
        } catch (e: Throwable) {
            AppLog.log("BACKUP", "Auto backup failed", e)
        }
    }

    private fun pruneAuto(c: Context, keep: Int) {
        val resolver = c.contentResolver
        val cursor = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?",
            arrayOf("rakkez-auto-%"),
            MediaStore.MediaColumns.DATE_ADDED + " DESC",
        ) ?: return
        val ids = ArrayList<Long>()
        cursor.use {
            while (it.moveToNext()) ids.add(it.getLong(0))
        }
        var i = keep
        while (i < ids.size) {
            try {
                resolver.delete(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ids[i]), null, null)
            } catch (e: Throwable) {
                // ignore
            }
            i += 1
        }
    }
}

package com.khereba1.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

const val MK_IMAGE = 0
const val MK_AUDIO = 1
const val MK_VIDEO = 2
const val MK_FILE = 3

// type filters of the notes list
const val NF_ALL = -1
const val NF_TEXT = 0
const val NF_LINK = 1
const val NF_IMAGE = 2
const val NF_AUDIO = 3
const val NF_VIDEO = 4

/** An attachment. [file] is a file name inside files/media. */
data class Media(
    val id: Long,
    val file: String,
    val kind: Int,
    val mime: String,
    val label: String,
    val durMs: Long,
)

/** collectionId 0 = the Inbox. */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val archived: Boolean,
    val collectionId: Long,
    val media: List<Media>,
)

data class NoteCollection(val id: Long, val name: String)

data class NotesData(val notes: List<Note>, val collections: List<NoteCollection>)

object NoteTools {
    private val URL_RE = Regex("https?://[^\\s]+")

    fun firstUrl(s: String): String? = URL_RE.find(s)?.value

    fun domain(url: String): String {
        return try {
            (Uri.parse(url).host ?: url).removePrefix("www.")
        } catch (e: Throwable) {
            url
        }
    }

    fun isBlank(n: Note): Boolean = n.title.isBlank() && n.body.isBlank() && n.media.isEmpty()

    /** Automatic classification by what the note contains. */
    fun matches(n: Note, f: Int): Boolean {
        return when (f) {
            NF_TEXT -> n.media.isEmpty() && firstUrl(n.body) == null
            NF_LINK -> firstUrl(n.body) != null
            NF_IMAGE -> n.media.any { it.kind == MK_IMAGE }
            NF_AUDIO -> n.media.any { it.kind == MK_AUDIO }
            NF_VIDEO -> n.media.any { it.kind == MK_VIDEO }
            else -> true
        }
    }

    fun searchMatch(n: Note, q: String): Boolean {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return true
        if (n.title.lowercase().contains(s)) return true
        if (n.body.lowercase().contains(s)) return true
        if (n.media.any { it.label.lowercase().contains(s) }) return true
        return false
    }

    fun headline(n: Note): String {
        if (n.title.isNotBlank()) return n.title.trim()
        for (line in n.body.lines()) {
            if (line.isNotBlank()) return line.trim().take(80)
        }
        return ""
    }

    fun preview(n: Note): String {
        if (n.title.isNotBlank()) return n.body.trim()
        val lines = n.body.lines()
        var skipped = false
        val rest = ArrayList<String>()
        for (line in lines) {
            if (!skipped && line.isNotBlank()) {
                skipped = true
                continue
            }
            if (skipped) rest.add(line)
        }
        return rest.joinToString("\n").trim()
    }

    fun mimeForExt(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "application/octet-stream"
    }

    fun kindForMime(mime: String): Int {
        return when {
            mime.startsWith("image/") -> MK_IMAGE
            mime.startsWith("audio/") -> MK_AUDIO
            mime.startsWith("video/") -> MK_VIDEO
            else -> MK_FILE
        }
    }
}

/** notes.json (text and structure) + files/media (attachments). */
object NoteStore {
    private fun file(c: Context) = File(c.filesDir, "notes.json")

    fun mediaDir(c: Context): File {
        val d = File(c.filesDir, "media")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun mediaFile(c: Context, m: Media): File = File(mediaDir(c), m.file)

    fun newMediaFile(c: Context, ext: String): File {
        val name = "m" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(6) + "." + ext
        return File(mediaDir(c), name)
    }

    fun notesSize(c: Context): Long {
        val f = file(c)
        return if (f.exists()) f.length() else 0L
    }

    fun mediaSize(c: Context): Long {
        var sum = 0L
        val list = mediaDir(c).listFiles() ?: return 0L
        for (f in list) sum += f.length()
        return sum
    }

    @Synchronized
    fun load(c: Context): NotesData {
        val f = file(c)
        if (!f.exists()) return NotesData(emptyList(), emptyList())
        return try {
            val root = JSONObject(f.readText())
            val cols = ArrayList<NoteCollection>()
            val ca = root.optJSONArray("collections") ?: JSONArray()
            for (i in 0 until ca.length()) {
                val o = ca.getJSONObject(i)
                cols.add(NoteCollection(o.getLong("id"), o.optString("name", "")))
            }
            val notes = ArrayList<Note>()
            val na = root.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until na.length()) {
                val o = na.getJSONObject(i)
                val media = ArrayList<Media>()
                val ma = o.optJSONArray("media") ?: JSONArray()
                for (j in 0 until ma.length()) {
                    val mo = ma.getJSONObject(j)
                    media.add(
                        Media(
                            id = mo.getLong("id"),
                            file = mo.optString("file", ""),
                            kind = mo.optInt("kind", MK_FILE),
                            mime = mo.optString("mime", ""),
                            label = mo.optString("label", ""),
                            durMs = mo.optLong("dur", 0L),
                        )
                    )
                }
                notes.add(
                    Note(
                        id = o.getLong("id"),
                        title = o.optString("title", ""),
                        body = o.optString("body", ""),
                        createdAt = o.optLong("c", 0L),
                        updatedAt = o.optLong("u", 0L),
                        pinned = o.optBoolean("pin", false),
                        archived = o.optBoolean("arch", false),
                        collectionId = o.optLong("col", 0L),
                        media = media,
                    )
                )
            }
            NotesData(notes, cols)
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not read notes.json", e)
            NotesData(emptyList(), emptyList())
        }
    }

    @Synchronized
    fun save(c: Context, d: NotesData) {
        try {
            val root = JSONObject()
            root.put("v", 1)
            val ca = JSONArray()
            for (col in d.collections) {
                val o = JSONObject()
                o.put("id", col.id)
                o.put("name", col.name)
                ca.put(o)
            }
            root.put("collections", ca)
            val na = JSONArray()
            for (n in d.notes) {
                val o = JSONObject()
                o.put("id", n.id)
                o.put("title", n.title)
                o.put("body", n.body)
                o.put("c", n.createdAt)
                o.put("u", n.updatedAt)
                o.put("pin", n.pinned)
                o.put("arch", n.archived)
                o.put("col", n.collectionId)
                val ma = JSONArray()
                for (m in n.media) {
                    val mo = JSONObject()
                    mo.put("id", m.id)
                    mo.put("file", m.file)
                    mo.put("kind", m.kind)
                    mo.put("mime", m.mime)
                    mo.put("label", m.label)
                    mo.put("dur", m.durMs)
                    ma.put(mo)
                }
                o.put("media", ma)
                na.put(o)
            }
            root.put("notes", na)
            val tmp = File(c.filesDir, "notes.json.tmp")
            tmp.writeText(root.toString())
            val dst = file(c)
            if (!tmp.renameTo(dst)) {
                dst.writeText(root.toString())
                tmp.delete()
            }
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not save notes.json", e)
        }
    }

    @Synchronized
    fun update(c: Context, change: (NotesData) -> NotesData): NotesData {
        val next = change(load(c))
        save(c, next)
        return next
    }

    fun deleteFiles(c: Context, list: List<Media>) {
        for (m in list) {
            try {
                mediaFile(c, m).delete()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    private fun displayName(c: Context, uri: Uri): String {
        try {
            val cur = c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cur != null) {
                cur.use {
                    if (it.moveToFirst()) {
                        val n = it.getString(0)
                        if (n != null) return n
                    }
                }
            }
        } catch (e: Throwable) {
            // ignore
        }
        return ""
    }

    /** Copies a picked/shared file into the app's media folder. */
    fun copyIn(c: Context, uri: Uri): Media? {
        return try {
            val mime = c.contentResolver.getType(uri) ?: "application/octet-stream"
            val label = displayName(c, uri)
            var ext = label.substringAfterLast('.', "")
            if (ext.isEmpty() || ext.length > 5) {
                ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
            }
            val f = newMediaFile(c, ext)
            val input = c.contentResolver.openInputStream(uri) ?: return null
            input.use { ins ->
                FileOutputStream(f).use { out -> ins.copyTo(out) }
            }
            Media(Store.newId(), f.name, NoteTools.kindForMime(mime), mime, label, 0L)
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not copy attachment", e)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun streamOf(i: Intent): Uri? = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamsOf(i: Intent): List<Uri> = i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: arrayListOf()

    /** Used by the share target: saves what another app shared as a new note in the Inbox. */
    fun createFromShare(c: Context, i: Intent): Boolean {
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT) ?: i.getStringExtra(Intent.EXTRA_TITLE) ?: ""
        val uris = ArrayList<Uri>()
        if (i.action == Intent.ACTION_SEND_MULTIPLE) {
            uris.addAll(streamsOf(i))
        } else {
            val u = streamOf(i)
            if (u != null) uris.add(u)
        }
        val media = ArrayList<Media>()
        for (u in uris) {
            val m = copyIn(c, u)
            if (m != null) media.add(m)
        }
        if (text.isBlank() && media.isEmpty()) return false
        val now = System.currentTimeMillis()
        val n = Note(Store.newId(), subject.trim(), text.trim(), now, now, false, false, 0L, media)
        update(c) { d -> NotesData(listOf(n) + d.notes, d.collections) }
        return true
    }
}

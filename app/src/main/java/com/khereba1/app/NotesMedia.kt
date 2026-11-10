package com.khereba1.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object NoteMedia {
    fun uriFor(c: Context, f: File): Uri = FileProvider.getUriForFile(c, c.packageName + ".files", f)

    fun fmtDuration(ms: Long): String {
        val total = ms / 1000L
        val m = total / 60L
        val s = total % 60L
        return m.toString() + ":" + (if (s < 10L) "0" else "") + s.toString()
    }

    fun durationOf(f: File): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0").toLong()
        } catch (e: Throwable) {
            0L
        } finally {
            try {
                r.release()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    /** Decodes an image at roughly [target] px (keeps memory small) and applies the camera rotation. */
    fun decodeImage(f: File, target: Int): Bitmap? {
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, o)
            var s = 1
            while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            val bmp = BitmapFactory.decodeFile(f.absolutePath, o2) ?: return null
            var deg = 0
            try {
                val ex = ExifInterface(f.absolutePath)
                deg = when (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } catch (e: Throwable) {
                deg = 0
            }
            if (deg == 0) {
                bmp
            } else {
                val m = Matrix()
                m.postRotate(deg.toFloat())
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun videoFrame(f: File): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            r.getFrameAtTime(0L)
        } catch (e: Throwable) {
            null
        } finally {
            try {
                r.release()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    /** Opens an attachment in whatever app the phone has for it. */
    fun open(c: Context, m: Media): Boolean {
        return try {
            val f = NoteStore.mediaFile(c, m)
            if (!f.exists()) return false
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uriFor(c, f), if (m.mime.isEmpty()) "*/*" else m.mime)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
            true
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not open attachment", e)
            false
        }
    }

    fun shareMedia(c: Context, m: Media) {
        try {
            val f = NoteStore.mediaFile(c, m)
            if (!f.exists()) return
            val i = Intent(Intent.ACTION_SEND)
            i.type = if (m.mime.isEmpty()) "*/*" else m.mime
            i.putExtra(Intent.EXTRA_STREAM, uriFor(c, f))
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(i, null)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(chooser)
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not share attachment", e)
        }
    }

    fun openUrl(c: Context, url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not open link", e)
        }
    }

    /** Shares the text of a note, with its attachments if it has any. */
    fun share(c: Context, n: Note) {
        try {
            val text = (if (n.title.isNotBlank()) n.title.trim() + "\n\n" else "") + n.body.trim()
            val files = ArrayList<Uri>()
            for (m in n.media) {
                val f = NoteStore.mediaFile(c, m)
                if (f.exists()) files.add(uriFor(c, f))
            }
            val i: Intent
            if (files.isEmpty()) {
                i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, text)
            } else if (files.size == 1) {
                i = Intent(Intent.ACTION_SEND)
                i.type = if (n.media[0].mime.isEmpty()) "*/*" else n.media[0].mime
                i.putExtra(Intent.EXTRA_STREAM, files[0])
                if (text.isNotBlank()) i.putExtra(Intent.EXTRA_TEXT, text)
            } else {
                i = Intent(Intent.ACTION_SEND_MULTIPLE)
                i.type = "*/*"
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
                if (text.isNotBlank()) i.putExtra(Intent.EXTRA_TEXT, text)
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(i, null)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(chooser)
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not share note", e)
        }
    }
}

/** Voice recorder (AAC in an .m4a file). Needs the RECORD_AUDIO permission, asked by the screen. */
class Recorder(private val c: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var segStart = 0L
    private var accum = 0L
    private var paused = false

    private fun newRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= 31) MediaRecorder(c) else legacyRecorder()
    }

    @Suppress("DEPRECATION")
    private fun legacyRecorder(): MediaRecorder = MediaRecorder()

    fun start(): Boolean {
        return try {
            val f = NoteStore.newMediaFile(c, "m4a")
            val r = newRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = f
            segStart = System.currentTimeMillis()
            accum = 0L
            paused = false
            true
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not start recording", e)
            cancel()
            false
        }
    }

    fun elapsedMs(): Long = accum + (if (rec != null && !paused) System.currentTimeMillis() - segStart else 0L)

    fun pause() {
        val r = rec ?: return
        if (paused) return
        try {
            r.pause()
            accum += System.currentTimeMillis() - segStart
            paused = true
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not pause recording", e)
        }
    }

    fun resume() {
        val r = rec ?: return
        if (!paused) return
        try {
            r.resume()
            segStart = System.currentTimeMillis()
            paused = false
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not resume recording", e)
        }
    }

    /** Stops and returns the new attachment, or null if nothing usable was recorded. */
    fun stop(): Media? {
        val r = rec ?: return null
        val f = file
        val dur = elapsedMs()
        rec = null
        file = null
        return try {
            r.stop()
            r.release()
            if (f == null || !f.exists() || f.length() == 0L) {
                null
            } else {
                Media(Store.newId(), f.name, MK_AUDIO, "audio/mp4", "", dur)
            }
        } catch (e: Throwable) {
            try {
                r.release()
            } catch (e2: Throwable) {
                // ignore
            }
            if (f != null) f.delete()
            null
        }
    }

    fun cancel() {
        val r = rec
        val f = file
        rec = null
        file = null
        if (r != null) {
            try {
                r.stop()
            } catch (e: Throwable) {
                // ignore
            }
            try {
                r.release()
            } catch (e: Throwable) {
                // ignore
            }
        }
        if (f != null) {
            try {
                f.delete()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }
}

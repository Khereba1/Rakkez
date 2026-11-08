package com.khereba1.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val PREFS = "app_prefs"

/** App entry point: starts the in-app error log and creates the notification channel. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        Alerts.ensureChannel(this)
    }
}

/** True while MainActivity is visible (used to avoid a notification when the app is already open). */
object Foreground {
    @Volatile
    var on: Boolean = false
}

/** Small file-based error log (private to the app, never sent anywhere). Also records crashes. */
object AppLog {
    private var file: File? = null
    private val lock = Any()
    private const val MAX_BYTES = 200_000

    fun init(context: Context) {
        if (file != null) return
        file = File(context.filesDir, "app.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                log("CRASH", "Uncaught exception in thread " + thread.name, error)
            } catch (e: Throwable) {
                // ignore
            }
            previous?.uncaughtException(thread, error)
        }
        log("APP", "Started. Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL)
    }

    fun log(tag: String, message: String, error: Throwable? = null) {
        Log.i("App/" + tag, message)
        val f = file ?: return
        synchronized(lock) {
            try {
                if (f.exists() && f.length() > MAX_BYTES) {
                    val text = f.readText()
                    f.writeText(text.takeLast(MAX_BYTES / 2))
                }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val sb = StringBuilder()
                sb.append(time).append(" [").append(tag).append("] ").append(message).append('\n')
                if (error != null) sb.append(Log.getStackTraceString(error)).append('\n')
                f.appendText(sb.toString())
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    fun read(): String {
        val f = file ?: return ""
        return try {
            if (f.exists()) f.readText() else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun clear() {
        try {
            file?.writeText("")
        } catch (e: Throwable) {
            // ignore
        }
    }
}

/** In-app language switch (system / Arabic / English). */
object LocaleHelper {
    fun wrap(base: Context): Context {
        val lang = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "system") ?: "system"
        if (lang == "system") return base
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}

/** Localized string for code that runs outside the activity (receivers, widgets, notifications). */
fun tr(c: Context, id: Int): String = LocaleHelper.wrap(c).getString(id)

/** Locale used for dates/times: keeps Western digits even in Arabic. */
fun dateLocale(): Locale {
    val loc = Locale.getDefault()
    return if (loc.language == "ar") Locale.forLanguageTag("ar-EG-u-nu-latn") else loc
}

/** "1h 25m" / "25m" using the localized templates (all numbers are passed as strings = Western digits). */
fun fmtMinutes(c: Context, totalMin: Long): String {
    val m = if (totalMin < 0L) 0L else totalMin
    val h = (m / 60L).toInt()
    val r = (m % 60L).toInt()
    val lc = LocaleHelper.wrap(c)
    return if (h > 0) lc.getString(R.string.dur_h_m, h.toString(), r.toString()) else lc.getString(R.string.dur_m, r.toString())
}

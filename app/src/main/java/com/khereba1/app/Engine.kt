package com.khereba1.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

const val PH_IDLE = 0
const val PH_FOCUS = 1
const val PH_SHORT = 2
const val PH_LONG = 3
const val PH_ASK = 4

/** Persisted timer state. Time is absolute (endAt) so it survives the process being killed. */
data class TimerState(
    val phase: Int = PH_IDLE,
    val running: Boolean = false,
    val endAt: Long = 0L,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val cycle: Int = 0,
    val taskId: Long = 0L,
    val extra: Boolean = false,
) {
    fun remaining(now: Long): Long {
        return if (running) {
            val r = endAt - now
            if (r < 0L) 0L else r
        } else {
            remainingMs
        }
    }
}

/** All timer logic. Safe to call from the UI, the alarm receiver and the widgets. */
object Engine {
    const val EV_NONE = 0
    const val EV_FOCUS_DONE = 1
    const val EV_BREAK_DONE = 2
    const val ACTION_END = "com.khereba1.rakkez.TIMER_END"
    private const val KEY = "timer"

    @Synchronized
    fun load(c: Context): TimerState {
        val raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return TimerState()
        return try {
            val o = JSONObject(raw)
            TimerState(
                phase = o.optInt("phase", 0),
                running = o.optBoolean("running", false),
                endAt = o.optLong("endAt", 0L),
                remainingMs = o.optLong("rem", 0L),
                totalMs = o.optLong("total", 0L),
                cycle = o.optInt("cycle", 0),
                taskId = o.optLong("task", 0L),
                extra = o.optBoolean("extra", false),
            )
        } catch (e: Throwable) {
            AppLog.log("TIMER", "Bad timer state, resetting", e)
            TimerState()
        }
    }

    @Synchronized
    private fun save(c: Context, s: TimerState) {
        val o = JSONObject()
        o.put("phase", s.phase)
        o.put("running", s.running)
        o.put("endAt", s.endAt)
        o.put("rem", s.remainingMs)
        o.put("total", s.totalMs)
        o.put("cycle", s.cycle)
        o.put("task", s.taskId)
        o.put("extra", s.extra)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).commit()
    }

    private fun idle(s: TimerState): TimerState =
        s.copy(phase = PH_IDLE, running = false, endAt = 0L, remainingMs = 0L, totalMs = 0L, extra = false)

    @Synchronized
    fun selectTask(c: Context, id: Long) {
        save(c, load(c).copy(taskId = id))
    }

    @Synchronized
    fun startFocus(c: Context, minutes: Int, extra: Boolean) {
        var s = load(c)
        val now = System.currentTimeMillis()
        // A new day starts a fresh cycle.
        if (!extra && s.phase == PH_IDLE && Store.todayMs(c) == 0L) s = s.copy(cycle = 0)
        val total = minutes.coerceIn(1, 600) * 60_000L
        save(c, s.copy(phase = PH_FOCUS, running = true, endAt = now + total, remainingMs = total, totalMs = total, extra = extra))
        schedule(c, now + total)
        FocusService.refresh(c)
    }

    @Synchronized
    fun startBreak(c: Context) {
        val s = load(c)
        val st = Store.loadSettings(c)
        val isLong = st.cycleMode && s.cycle >= st.longEvery
        val minutes = if (isLong) st.longMin else st.shortMin
        val total = minutes * 60_000L
        val now = System.currentTimeMillis()
        save(
            c,
            s.copy(
                phase = if (isLong) PH_LONG else PH_SHORT,
                running = true,
                endAt = now + total,
                remainingMs = total,
                totalMs = total,
                cycle = if (isLong) 0 else s.cycle,
                extra = false,
            )
        )
        schedule(c, now + total)
        FocusService.refresh(c)
    }

    @Synchronized
    fun pause(c: Context) {
        val s = load(c)
        if (!s.running || s.phase < PH_FOCUS || s.phase > PH_LONG) return
        val rem = s.remaining(System.currentTimeMillis())
        save(c, s.copy(running = false, remainingMs = if (rem < 1000L) 1000L else rem))
        cancel(c)
        FocusService.refresh(c)
    }

    @Synchronized
    fun resume(c: Context) {
        val s = load(c)
        if (s.running || s.phase < PH_FOCUS || s.phase > PH_LONG) return
        val now = System.currentTimeMillis()
        save(c, s.copy(running = true, endAt = now + s.remainingMs))
        schedule(c, now + s.remainingMs)
        FocusService.refresh(c)
    }

    /** Ends the current period early. With keep = true a focus period of at least one minute is recorded (as partial). */
    @Synchronized
    fun stop(c: Context, keep: Boolean) {
        val s = load(c)
        if (s.phase == PH_FOCUS && keep) {
            val now = System.currentTimeMillis()
            val elapsed = s.totalMs - s.remaining(now)
            if (elapsed >= 60_000L) recordSession(c, now - elapsed, elapsed, s.taskId, s.extra, true)
        }
        cancel(c)
        save(c, idle(s))
        FocusService.refresh(c)
    }

    /** "That's enough" after a finished session. */
    @Synchronized
    fun finishAsk(c: Context) {
        cancel(c)
        save(c, idle(load(c)))
        FocusService.refresh(c)
    }

    /** Called by the UI tick, the alarm receiver and the widgets. Idempotent. */
    @Synchronized
    fun sync(c: Context): Int {
        val s = load(c)
        if (!s.running || s.phase == PH_IDLE || s.phase == PH_ASK) return EV_NONE
        val now = System.currentTimeMillis()
        if (now + 500L < s.endAt) return EV_NONE
        if (s.phase == PH_FOCUS) {
            recordSession(c, s.endAt - s.totalMs, s.totalMs, s.taskId, s.extra, false)
            save(
                c,
                s.copy(
                    phase = PH_ASK,
                    running = false,
                    endAt = 0L,
                    remainingMs = 0L,
                    totalMs = 0L,
                    cycle = if (s.extra) s.cycle else s.cycle + 1,
                )
            )
            FocusService.refresh(c)
            return EV_FOCUS_DONE
        }
        save(c, idle(s))
        FocusService.refresh(c)
        return EV_BREAK_DONE
    }

    private fun recordSession(c: Context, startAt: Long, ms: Long, taskId: Long, extra: Boolean, partial: Boolean) {
        Store.addSession(c, FocusSession(Store.newId(), startAt, ms, taskId, extra, partial))
        if (taskId != 0L) {
            val addSession = if (!extra && !partial) 1 else 0
            Store.editTasks(c) { list ->
                list.map { t -> if (t.id == taskId) t.copy(spent = t.spent + addSession, spentMs = t.spentMs + ms) else t }
            }
        }
    }

    // ---------------- alarm ----------------

    private fun alarmIntent(c: Context): PendingIntent {
        val i = Intent(c, TimerReceiver::class.java)
        i.action = ACTION_END
        return PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(c: Context, at: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(c)
        try {
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: Throwable) {
            AppLog.log("TIMER", "Exact alarm failed, using inexact", e)
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e2: Throwable) {
                AppLog.log("TIMER", "Could not schedule alarm", e2)
            }
        }
    }

    private fun cancel(c: Context) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmIntent(c))
        } catch (e: Throwable) {
            AppLog.log("TIMER", "Could not cancel alarm", e)
        }
    }
}

/** Fires when a period ends, even if the app process was killed. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FocusService.ACTION_REPOST) {
            // the user swiped the permanent notification away: put it back (only if a session is still running)
            FocusService.refresh(context)
            return
        }
        val pr = goAsync()
        try {
            val ev = Engine.sync(context)
            if (ev != Engine.EV_NONE) {
                // We play the sound ourselves (alarm stream + audio focus) so it is heard over other apps' audio.
                Alerts.ding(context) { pr.finish() }
                if (!Foreground.on) Alerts.notifyDone(context, ev)
                Widgets.updateAll(context)
            } else {
                pr.finish()
            }
        } catch (e: Throwable) {
            AppLog.log("TIMER", "Receiver failed", e)
            try {
                pr.finish()
            } catch (e2: Throwable) {
                // ignore
            }
        }
    }
}

object Alerts {
    const val CHANNEL = "timer_done"
    const val DONE_CHANNEL = "timer_done_v3"
    const val RUN_CHANNEL = "timer_running_v2"
    private const val NOTIF_ID = 1001
    val SOUND_KEYS = listOf("bell", "chime", "beep", "marimba", "system", "custom")

    fun soundKey(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("alert_sound2", "marimba") ?: "marimba"

    fun customUri(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("alert_custom", "") ?: ""

    fun setSound(c: Context, key: String, custom: String? = null) {
        val e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("alert_sound2", key)
        if (custom != null) e.putString("alert_custom", custom)
        e.commit()
        ensureChannel(c)
    }

    /** Resolved sound uri for the current choice. */
    fun soundUri(c: Context): android.net.Uri {
        val raw = fun(n: String): android.net.Uri = android.net.Uri.parse("android.resource://" + c.packageName + "/raw/" + n)
        return when (soundKey(c)) {
            "chime" -> raw("snd_chime")
            "beep" -> raw("snd_beep")
            "marimba" -> raw("snd_marimba")
            "system" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "custom" -> {
                val u = customUri(c)
                if (u.isEmpty()) raw("snd_marimba") else android.net.Uri.parse(u)
            }
            "bell" -> raw("snd_bell")
            else -> raw("snd_marimba")
        }
    }

    /** Short tag that changes with the chosen sound (channels are immutable, so each sound gets its own). */
    fun soundTag(c: Context): String {
        val k = soundKey(c)
        return if (k == "custom") "custom_" + customUri(c).hashCode().toString().replace('-', 'm') else k
    }

    private fun alarmAttrs(): android.media.AudioAttributes =
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    /**
     * Two quiet channels: the "done" one has no sound of its own (we play the sound ourselves, see [play]),
     * and the "running" one is the silent, always-present notification shown during a session.
     */
    fun ensureChannel(c: Context) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java)
            for (old in nm.notificationChannels) {
                if (old.id.startsWith(CHANNEL) && old.id != DONE_CHANNEL) nm.deleteNotificationChannel(old.id)
                if (old.id.startsWith("timer_running") && old.id != RUN_CHANNEL) nm.deleteNotificationChannel(old.id)
            }
            val done = NotificationChannel(DONE_CHANNEL, tr(c, R.string.channel_name), NotificationManager.IMPORTANCE_HIGH)
            done.description = tr(c, R.string.channel_desc)
            done.enableVibration(true)
            done.setSound(null, null)
            nm.createNotificationChannel(done)
            val run = NotificationChannel(RUN_CHANNEL, tr(c, R.string.run_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            run.setShowBadge(false)
            run.setSound(null, null)
            run.enableVibration(false)
            nm.createNotificationChannel(run)
        } catch (e: Throwable) {
            AppLog.log("ALERT", "Channel failed", e)
        }
    }

    fun notifyDone(c: Context, ev: Int) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(c)
            val focus = ev == Engine.EV_FOCUS_DONE
            val title = tr(c, if (focus) R.string.notif_focus_done_title else R.string.notif_break_done_title)
            val text = tr(c, if (focus) R.string.notif_focus_done_text else R.string.notif_break_done_text)
            val open = Intent(c, MainActivity::class.java)
            open.putExtra("tab", 1)
            open.data = android.net.Uri.parse("rakkez://open/notification")
            val pi = PendingIntent.getActivity(c, 11, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(c, DONE_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            c.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        } catch (e: Throwable) {
            AppLog.log("ALERT", "Notification failed", e)
        }
    }

    fun cancel(c: Context) {
        try {
            c.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        } catch (e: Throwable) {
            // ignore
        }
    }

    private var player: android.media.MediaPlayer? = null

    /**
     * Plays the chosen sound on the alarm stream (audible on silent) and asks for transient audio focus,
     * so music or videos from other apps are lowered while it plays. [onEnd] is called once, when it finishes.
     */
    fun play(c: Context, onEnd: () -> Unit = {}) {
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val am = c.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val focusReq = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(alarmAttrs())
            .setOnAudioFocusChangeListener { }
            .build()
        var mp: android.media.MediaPlayer? = null
        val finish: () -> Unit = {
            if (done.compareAndSet(false, true)) {
                try {
                    mp?.release()
                } catch (e: Throwable) {
                    // ignore
                }
                if (player === mp) player = null
                try {
                    am.abandonAudioFocusRequest(focusReq)
                } catch (e: Throwable) {
                    // ignore
                }
                onEnd()
            }
        }
        try {
            try {
                player?.release()
            } catch (e: Throwable) {
                // ignore
            }
            am.requestAudioFocus(focusReq)
            val p = android.media.MediaPlayer()
            mp = p
            player = p
            p.setAudioAttributes(alarmAttrs())
            p.setDataSource(c, soundUri(c))
            p.setOnCompletionListener { finish() }
            p.setOnErrorListener { _, _, _ ->
                finish()
                true
            }
            p.setOnPreparedListener { it.start() }
            p.prepareAsync()
            // safety net so a receiver is never held for long
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 8000L)
        } catch (e: Throwable) {
            AppLog.log("ALERT", "Sound failed", e)
            finish()
        }
    }

    /** Sound + vibration when a period ends. */
    fun ding(c: Context, onEnd: () -> Unit = {}) {
        play(c, onEnd)
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 250L, 150L, 250L), -1))
        } catch (e: Throwable) {
            AppLog.log("ALERT", "Vibration failed", e)
        }
    }
}

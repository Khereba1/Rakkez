package com.khereba1.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps a permanent (cannot be swiped away) notification with a live countdown while a focus session or break
 * is running or paused. It disappears when the period ends or is stopped. It also keeps the app process alive,
 * which makes the end-of-session sound and notification reliable.
 */
class FocusService : Service() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var wake: android.os.PowerManager.WakeLock? = null
    private val endRunnable = Runnable {
        try {
            Alerts.handleEnd(applicationContext)
        } catch (e: Throwable) {
            AppLog.log("FOCUS", "End handling failed", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * The service itself rings when the period ends (a wake lock keeps the CPU awake until then), so the end does not
     * depend only on the system alarm, which some phones drop when the app is swiped away.
     */
    private fun armEnd(s: TimerState) {
        handler.removeCallbacks(endRunnable)
        releaseWake()
        if (!s.running) return
        val delay = (s.endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wl = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "rakkez:focus")
            wl.setReferenceCounted(false)
            wl.acquire(delay + 60_000L)
            wake = wl
        } catch (e: Throwable) {
            AppLog.log("FOCUS", "Wake lock failed", e)
        }
        handler.postDelayed(endRunnable, delay + 200L)
    }

    private fun releaseWake() {
        try {
            wake?.let { if (it.isHeld) it.release() }
        } catch (e: Throwable) {
            // ignore
        }
        wake = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(endRunnable)
        releaseWake()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = Engine.load(this)
        if (!isActive(s)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            Alerts.ensureChannel(this)
            val n = build(this, s)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NOTIF_ID, n, 0x40000000)
            } else {
                startForeground(NOTIF_ID, n)
            }
            armEnd(s)
        } catch (e: Throwable) {
            AppLog.log("FOCUS", "startForeground failed", e)
            stopSelf()
        }
        return START_STICKY
    }

    companion object {
        const val NOTIF_ID = 1003
        const val ACTION_REPOST = "com.khereba1.rakkez.REPOST_RUNNING"

        fun isActive(s: TimerState): Boolean = s.phase >= PH_FOCUS && s.phase <= PH_LONG

        /** Call after any timer change: starts, updates or stops the permanent notification. */
        fun refresh(c: Context) {
            val app = c.applicationContext
            try {
                val s = Engine.load(app)
                val nm = app.getSystemService(NotificationManager::class.java)
                if (isActive(s)) {
                    try {
                        ContextCompat.startForegroundService(app, Intent(app, FocusService::class.java))
                    } catch (e: Throwable) {
                        // not allowed to start a service from the background right now: show a plain ongoing notification
                        AppLog.log("FOCUS", "Service start refused, using plain notification", e)
                        Alerts.ensureChannel(app)
                        nm.notify(NOTIF_ID, build(app, s))
                    }
                } else {
                    app.stopService(Intent(app, FocusService::class.java))
                    nm.cancel(NOTIF_ID)
                }
            } catch (e: Throwable) {
                AppLog.log("FOCUS", "refresh failed", e)
            }
        }

        fun build(c: Context, s: TimerState): Notification {
            val focus = s.phase == PH_FOCUS
            val title = tr(c, if (focus) R.string.run_focus_title else R.string.run_break_title)
            val open = Intent(c, MainActivity::class.java)
            open.putExtra("tab", 1)
            open.data = android.net.Uri.parse("rakkez://open/running")
            val pi = PendingIntent.getActivity(c, 12, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val del = Intent(c, TimerReceiver::class.java)
            del.action = ACTION_REPOST
            val delPi = PendingIntent.getBroadcast(c, 13, del, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val b = NotificationCompat.Builder(c, Alerts.RUN_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentIntent(pi)
                .setOngoing(true)
                .setDeleteIntent(delPi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            if (s.running) {
                b.setContentText(tr(c, if (focus) R.string.run_focus_text else R.string.run_break_text))
                b.setShowWhen(true)
                b.setUsesChronometer(true)
                b.setChronometerCountDown(true)
                b.setWhen(s.endAt)
            } else {
                val left = NoteMedia.fmtDuration(s.remainingMs)
                b.setContentText(tr(c, R.string.run_paused_text) + " · " + left)
                b.setShowWhen(false)
            }
            return b.build()
        }
    }
}

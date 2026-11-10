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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * A habit. quit = false: a habit to build, [days] are the epoch days it was done (only today can be ticked).
 * quit = true: a habit to stop, the counter is the days since [start] (reset by a relapse).
 * [target] is the goal in days (21 to build, 90 to quit, editable). [doneAt] = epoch day the goal was reached (0 = not yet).
 */
data class Habit(
    val id: Long,
    val name: String,
    val days: List<Long>,
    val quit: Boolean = false,
    val target: Int = 21,
    val start: Long = 0L,
    val best: Int = 0,
    val doneAt: Long = 0L,
    val relapses: Int = 0,
    val res: Long = 0L,
)

/** A course: [done] of [total] lessons. */
data class Course(val id: Long, val name: String, val total: Int, val done: Int)

object Trackers {
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun today(): Long = LocalDate.now().toEpochDay()

    @Synchronized
    fun loadHabits(c: Context): List<Habit> {
        val out = ArrayList<Habit>()
        try {
            val arr = JSONArray(prefs(c).getString("habits", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val da = o.optJSONArray("d") ?: JSONArray()
                val days = ArrayList<Long>()
                for (j in 0 until da.length()) days.add(da.getLong(j))
                out.add(
                    Habit(
                        o.getLong("id"),
                        o.optString("n", ""),
                        days,
                        o.optBoolean("q", false),
                        o.optInt("t", 21),
                        o.optLong("s", 0L),
                        o.optInt("b", 0),
                        o.optLong("dn", 0L),
                        o.optInt("rl", 0),
                        o.optLong("rs", 0L),
                    )
                )
            }
        } catch (e: Throwable) {
            AppLog.log("STORE", "Could not read habits", e)
        }
        return out
    }

    @Synchronized
    fun saveHabits(c: Context, list: List<Habit>) {
        val arr = JSONArray()
        for (h in list) {
            val o = JSONObject()
            o.put("id", h.id)
            o.put("n", h.name)
            o.put("q", h.quit)
            o.put("t", h.target)
            o.put("s", h.start)
            o.put("b", h.best)
            o.put("dn", h.doneAt)
            o.put("rl", h.relapses)
            o.put("rs", h.res)
            val da = JSONArray()
            for (d in h.days.sorted().takeLast(800)) da.put(d)
            o.put("d", da)
            arr.put(o)
        }
        prefs(c).edit().putString("habits", arr.toString()).commit()
    }

    @Synchronized
    fun loadCourses(c: Context): List<Course> {
        val out = ArrayList<Course>()
        try {
            val arr = JSONArray(prefs(c).getString("courses", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Course(o.getLong("id"), o.optString("n", ""), o.optInt("t", 1), o.optInt("d", 0)))
            }
        } catch (e: Throwable) {
            AppLog.log("STORE", "Could not read courses", e)
        }
        return out
    }

    @Synchronized
    fun saveCourses(c: Context, list: List<Course>) {
        val arr = JSONArray()
        for (x in list) {
            val o = JSONObject()
            o.put("id", x.id)
            o.put("n", x.name)
            o.put("t", x.total)
            o.put("d", x.done)
            arr.put(o)
        }
        prefs(c).edit().putString("courses", arr.toString()).commit()
    }

    /** Days in a row ending today (or yesterday, if today is not done yet). */
    fun streak(h: Habit): Int {
        val set = h.days.toHashSet()
        var d = today()
        if (!set.contains(d)) d -= 1L
        var n = 0
        while (set.contains(d)) {
            n++
            d -= 1L
        }
        return n
    }

    /** Current run: days in a row (build) or days since the last relapse (quit). */
    fun current(h: Habit): Int {
        if (h.quit) {
            val st = if (h.start == 0L) today() else h.start
            return (today() - st).toInt().coerceAtLeast(0)
        }
        return streak(h)
    }

    fun bestOf(h: Habit): Int = maxOf(h.best, current(h))

    /** Days since the last check that were neither ticked nor answered (a build habit needs asking about them). */
    fun pendingDays(h: Habit): List<Long> {
        if (h.quit || h.doneAt != 0L || h.res == 0L) return emptyList()
        val yesterday = today() - 1L
        if (h.res >= yesterday) return emptyList()
        val set = h.days.toHashSet()
        val out = ArrayList<Long>()
        var d = h.res + 1L
        while (d <= yesterday) {
            if (!set.contains(d)) out.add(d)
            d += 1L
        }
        return out.takeLast(60)
    }

    fun remindOn(c: Context): Boolean = prefs(c).getBoolean("habit_remind", true)

    fun setRemind(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("habit_remind", on).commit()
    }
}

/** Evening reminder (about 9 pm) when a habit has not been ticked today or a missed day is waiting for an answer. */
object HabitReminder {
    private const val CH = "habit_remind"
    private const val NOTIF_ID = 1002

    private fun pending(c: Context): PendingIntent {
        val i = Intent(c, HabitReceiver::class.java)
        return PendingIntent.getBroadcast(c, 21, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(c: Context) {
        try {
            val am = c.getSystemService(AlarmManager::class.java)
            if (!Trackers.remindOn(c)) {
                am.cancel(pending(c))
                return
            }
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 21)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis() + 60000L) cal.add(Calendar.DAY_OF_YEAR, 1)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending(c))
        } catch (e: Throwable) {
            AppLog.log("HABIT", "Could not schedule reminder", e)
        }
    }

    fun fire(c: Context) {
        try {
            if (!Trackers.remindOn(c)) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val today = Trackers.today()
            val list = Trackers.loadHabits(c).filter { !it.quit && it.doneAt == 0L }
            val missed = list.any { Trackers.pendingDays(it).isNotEmpty() }
            val left = list.count { !it.days.contains(today) }
            if (!missed && left == 0) return
            val nm = c.getSystemService(NotificationManager::class.java)
            // same sound as the timer, but a normal notification: follows the phone's silent mode
            val chId = CH + "_" + Alerts.soundTag(c)
            for (old in nm.notificationChannels) {
                if (old.id.startsWith(CH) && old.id != chId) nm.deleteNotificationChannel(old.id)
            }
            val ch = NotificationChannel(chId, tr(c, R.string.habit_channel), NotificationManager.IMPORTANCE_DEFAULT)
            ch.setSound(
                Alerts.soundUri(c),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            nm.createNotificationChannel(ch)
            val text = if (missed) tr(c, R.string.habit_notif_missed) else String.format(tr(c, R.string.habit_notif_left), left.toString())
            val open = Intent(c, MainActivity::class.java)
            open.putExtra("habits", true)
            open.data = android.net.Uri.parse("rakkez://open/habits")
            val pi = PendingIntent.getActivity(c, 22, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(c, chId)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(tr(c, R.string.habit_notif_title))
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, n)
        } catch (e: Throwable) {
            AppLog.log("HABIT", "Reminder failed", e)
        }
    }
}

class HabitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            HabitReminder.fire(context)
            HabitReminder.schedule(context)
        } catch (e: Throwable) {
            AppLog.log("HABIT", "Receiver failed", e)
        }
    }
}

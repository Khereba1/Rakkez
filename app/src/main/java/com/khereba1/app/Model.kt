package com.khereba1.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

data class Task(
    val id: Long,
    val title: String,
    val done: Boolean = false,
    val estimate: Int = 0,
    val spent: Int = 0,
    val createdAt: Long = 0L,
    val doneAt: Long = 0L,
    /** Planned total time in minutes (0 = not set). `estimate` is the planned number of sessions (0 = not set). */
    val planMin: Int = 0,
    /** Custom focus length for this task in minutes (0 = use the default). */
    val sessionMin: Int = 0,
    /** Total focused time spent on this task (milliseconds, partial sessions included). */
    val spentMs: Long = 0L,
)

/** One finished (or manually stopped = partial) focus period. */
data class FocusSession(
    val id: Long,
    val startAt: Long,
    val durationMs: Long,
    val taskId: Long,
    val extra: Boolean,
    val partial: Boolean,
)

/** A distracting thought parked during a focus session. */
data class Idea(
    val id: Long,
    val text: String,
    val at: Long,
)

data class Settings(
    val focusMin: Int = 25,
    val shortMin: Int = 5,
    val longMin: Int = 15,
    val longEvery: Int = 4,
    val goalMin: Int = 120,
    val keepOn: Boolean = true,
    /** false = a single session (default); true = cycle of sessions and breaks. */
    val cycleMode: Boolean = false,
    val autoBackup: Boolean = false,
)

/** Start of the day (00:00) of the given time, optionally shifted by whole days. */
fun dayStart(ms: Long, plusDays: Int = 0): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    if (plusDays != 0) c.add(Calendar.DAY_OF_YEAR, plusDays)
    return c.timeInMillis
}

/** All persistence lives here: small JSON blobs inside SharedPreferences (no database, no network). */
object Store {
    private val idGen = AtomicLong(0L)

    fun newId(): Long {
        val now = System.currentTimeMillis()
        while (true) {
            val last = idGen.get()
            val next = if (now > last) now else last + 1L
            if (idGen.compareAndSet(last, next)) return next
        }
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------- tasks ----------------

    @Synchronized
    fun loadTasks(c: Context): List<Task> {
        val out = ArrayList<Task>()
        try {
            val arr = JSONArray(prefs(c).getString("tasks", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Task(
                        id = o.getLong("id"),
                        title = o.optString("title", ""),
                        done = o.optBoolean("done", false),
                        estimate = o.optInt("est", 0),
                        spent = o.optInt("spent", 0),
                        createdAt = o.optLong("created", 0L),
                        doneAt = o.optLong("doneAt", 0L),
                        planMin = o.optInt("plan", 0),
                        sessionMin = o.optInt("smin", 0),
                        spentMs = o.optLong("spms", 0L),
                    )
                )
            }
        } catch (e: Throwable) {
            AppLog.log("STORE", "Could not read tasks", e)
        }
        return out
    }

    @Synchronized
    fun saveTasks(c: Context, list: List<Task>) {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("title", t.title)
            o.put("done", t.done)
            o.put("est", t.estimate)
            o.put("spent", t.spent)
            o.put("created", t.createdAt)
            o.put("doneAt", t.doneAt)
            o.put("plan", t.planMin)
            o.put("smin", t.sessionMin)
            o.put("spms", t.spentMs)
            arr.put(o)
        }
        prefs(c).edit().putString("tasks", arr.toString()).commit()
    }

    @Synchronized
    fun editTasks(c: Context, change: (List<Task>) -> List<Task>) {
        saveTasks(c, change(loadTasks(c)))
    }

    @Synchronized
    fun toggleTask(c: Context, id: Long) {
        editTasks(c) { list ->
            list.map { t ->
                if (t.id == id) {
                    val nowDone = !t.done
                    t.copy(done = nowDone, doneAt = if (nowDone) System.currentTimeMillis() else 0L)
                } else {
                    t
                }
            }
        }
    }

    // ---------------- sessions ----------------

    @Synchronized
    fun loadSessions(c: Context): List<FocusSession> {
        val out = ArrayList<FocusSession>()
        try {
            val arr = JSONArray(prefs(c).getString("sessions", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    FocusSession(
                        id = o.getLong("id"),
                        startAt = o.getLong("start"),
                        durationMs = o.getLong("ms"),
                        taskId = o.optLong("task", 0L),
                        extra = o.optBoolean("extra", false),
                        partial = o.optBoolean("partial", false),
                    )
                )
            }
        } catch (e: Throwable) {
            AppLog.log("STORE", "Could not read sessions", e)
        }
        return out
    }

    @Synchronized
    fun addSession(c: Context, s: FocusSession) {
        val keepFrom = System.currentTimeMillis() - 120L * 24L * 60L * 60L * 1000L
        val list = ArrayList<FocusSession>()
        for (old in loadSessions(c)) {
            if (old.startAt >= keepFrom) list.add(old)
        }
        list.add(s)
        val arr = JSONArray()
        for (x in list) {
            val o = JSONObject()
            o.put("id", x.id)
            o.put("start", x.startAt)
            o.put("ms", x.durationMs)
            o.put("task", x.taskId)
            o.put("extra", x.extra)
            o.put("partial", x.partial)
            arr.put(o)
        }
        prefs(c).edit().putString("sessions", arr.toString()).commit()
    }

    @Synchronized
    fun todayMs(c: Context): Long {
        val start = dayStart(System.currentTimeMillis())
        var sum = 0L
        for (s in loadSessions(c)) {
            if (s.startAt >= start) sum += s.durationMs
        }
        return sum
    }

    @Synchronized
    fun todaySessionCount(c: Context): Int {
        val start = dayStart(System.currentTimeMillis())
        var n = 0
        for (s in loadSessions(c)) {
            if (s.startAt >= start) n += 1
        }
        return n
    }

    // ---------------- ideas (parking lot) ----------------

    @Synchronized
    fun loadIdeas(c: Context): List<Idea> {
        val out = ArrayList<Idea>()
        try {
            val arr = JSONArray(prefs(c).getString("ideas", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Idea(o.getLong("id"), o.optString("text", ""), o.optLong("at", 0L)))
            }
        } catch (e: Throwable) {
            AppLog.log("STORE", "Could not read ideas", e)
        }
        return out
    }

    @Synchronized
    fun saveIdeas(c: Context, list: List<Idea>) {
        val keepFrom = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        val arr = JSONArray()
        for (x in list) {
            if (x.at < keepFrom) continue
            val o = JSONObject()
            o.put("id", x.id)
            o.put("text", x.text)
            o.put("at", x.at)
            arr.put(o)
        }
        prefs(c).edit().putString("ideas", arr.toString()).commit()
    }

    @Synchronized
    fun editIdeas(c: Context, change: (List<Idea>) -> List<Idea>) {
        saveIdeas(c, change(loadIdeas(c)))
    }

    // ---------------- settings ----------------

    @Synchronized
    fun loadSettings(c: Context): Settings {
        val p = prefs(c)
        val d = Settings()
        return Settings(
            focusMin = p.getInt("s_focus", d.focusMin).coerceIn(1, 180),
            shortMin = p.getInt("s_short", d.shortMin).coerceIn(1, 60),
            longMin = p.getInt("s_long", d.longMin).coerceIn(1, 90),
            longEvery = p.getInt("s_every", d.longEvery).coerceIn(2, 8),
            goalMin = p.getInt("s_goal", d.goalMin).coerceIn(15, 720),
            keepOn = p.getBoolean("s_keepon", d.keepOn),
            cycleMode = p.getBoolean("s_cycle", d.cycleMode),
            autoBackup = p.getBoolean("s_auto", d.autoBackup),
        )
    }

    @Synchronized
    fun saveSettings(c: Context, s: Settings) {
        prefs(c).edit()
            .putInt("s_focus", s.focusMin)
            .putInt("s_short", s.shortMin)
            .putInt("s_long", s.longMin)
            .putInt("s_every", s.longEvery)
            .putInt("s_goal", s.goalMin)
            .putBoolean("s_keepon", s.keepOn)
            .putBoolean("s_cycle", s.cycleMode)
            .putBoolean("s_auto", s.autoBackup)
            .commit()
    }
}

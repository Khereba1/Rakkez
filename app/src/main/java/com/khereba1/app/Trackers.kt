package com.khereba1.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A habit: [days] are epoch days (LocalDate.toEpochDay) on which it was done. */
data class Habit(val id: Long, val name: String, val days: List<Long>)

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
                out.add(Habit(o.getLong("id"), o.optString("n", ""), days))
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
}

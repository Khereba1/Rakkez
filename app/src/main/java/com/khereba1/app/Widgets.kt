package com.khereba1.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

object Widgets {
    const val ACTION_TOGGLE_TASK = "com.khereba1.rakkez.TOGGLE_TASK"
    const val ACTION_TOGGLE_TIMER = "com.khereba1.rakkez.TOGGLE_TIMER"

    fun updateAll(c0: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(c0)
            val t = mgr.getAppWidgetIds(ComponentName(c0, TasksWidget::class.java))
            if (t.isNotEmpty()) mgr.updateAppWidget(t, buildTasks(c0))
            val f = mgr.getAppWidgetIds(ComponentName(c0, FocusWidget::class.java))
            if (f.isNotEmpty()) mgr.updateAppWidget(f, buildFocus(c0))
        } catch (e: Throwable) {
            AppLog.log("WIDGET", "Update failed", e)
        }
    }

    private fun openApp(c: Context, tab: Int, add: Boolean, code: Int): PendingIntent {
        val i = Intent(c, MainActivity::class.java)
        i.putExtra("tab", tab)
        i.putExtra("add", add)
        i.data = Uri.parse("rakkez://open/" + tab + "/" + add)
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun togglePi(c: Context, taskId: Long): PendingIntent {
        val i = Intent(c, TasksWidget::class.java)
        i.action = ACTION_TOGGLE_TASK
        i.putExtra("id", taskId)
        i.data = Uri.parse("rakkez://toggle/" + taskId)
        return PendingIntent.getBroadcast(c, 50, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun timerPi(c: Context): PendingIntent {
        val i = Intent(c, FocusWidget::class.java)
        i.action = ACTION_TOGGLE_TIMER
        return PendingIntent.getBroadcast(c, 41, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun buildTasks(c0: Context): RemoteViews {
        val c = LocaleHelper.wrap(c0)
        val rv = RemoteViews(c.packageName, R.layout.widget_tasks)
        val open = Store.loadTasks(c0).filter { !it.done }
        rv.setTextViewText(R.id.w_title, c.getString(R.string.app_name))
        rv.setTextViewText(R.id.w_today, fmtMinutes(c, Store.todayMs(c0) / 60000L))
        val rows = intArrayOf(R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4)
        val checks = intArrayOf(R.id.w_check1, R.id.w_check2, R.id.w_check3, R.id.w_check4)
        val texts = intArrayOf(R.id.w_text1, R.id.w_text2, R.id.w_text3, R.id.w_text4)
        for (i in 0 until 4) {
            if (i < open.size) {
                val t = open[i]
                rv.setViewVisibility(rows[i], View.VISIBLE)
                rv.setTextViewText(texts[i], t.title)
                rv.setImageViewResource(checks[i], R.drawable.ic_w_check_off)
                rv.setOnClickPendingIntent(checks[i], togglePi(c, t.id))
                rv.setOnClickPendingIntent(texts[i], openApp(c, 0, false, 20 + i))
            } else {
                rv.setViewVisibility(rows[i], View.GONE)
            }
        }
        if (open.isEmpty()) {
            rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
            rv.setTextViewText(R.id.w_empty, c.getString(R.string.widget_empty))
        } else {
            rv.setViewVisibility(R.id.w_empty, View.GONE)
        }
        if (open.size > 4) {
            rv.setViewVisibility(R.id.w_more, View.VISIBLE)
            rv.setTextViewText(R.id.w_more, c.getString(R.string.widget_more, (open.size - 4).toString()))
        } else {
            rv.setViewVisibility(R.id.w_more, View.GONE)
        }
        rv.setOnClickPendingIntent(R.id.w_add, openApp(c, 0, true, 30))
        rv.setOnClickPendingIntent(R.id.w_title, openApp(c, 0, false, 31))
        rv.setOnClickPendingIntent(R.id.w_today, openApp(c, 2, false, 32))
        return rv
    }

    fun buildFocus(c0: Context): RemoteViews {
        val c = LocaleHelper.wrap(c0)
        val rv = RemoteViews(c.packageName, R.layout.widget_focus)
        val s = Engine.load(c0)
        val todayText = fmtMinutes(c, Store.todayMs(c0) / 60000L)
        val active = s.phase == PH_FOCUS || s.phase == PH_SHORT || s.phase == PH_LONG
        val stateRes = when (s.phase) {
            PH_FOCUS -> R.string.phase_focus
            PH_SHORT -> R.string.phase_short
            PH_LONG -> R.string.phase_long
            PH_ASK -> R.string.phase_ask
            else -> R.string.phase_idle
        }
        rv.setTextViewText(R.id.f_state, c.getString(stateRes))
        if (active) {
            val rem = s.remaining(System.currentTimeMillis())
            rv.setViewVisibility(R.id.f_chrono, View.VISIBLE)
            rv.setViewVisibility(R.id.f_total, View.GONE)
            rv.setChronometerCountDown(R.id.f_chrono, true)
            rv.setChronometer(R.id.f_chrono, SystemClock.elapsedRealtime() + rem, null, s.running)
            rv.setTextViewText(R.id.f_sub, c.getString(R.string.today_so_far, todayText))
        } else {
            rv.setViewVisibility(R.id.f_chrono, View.GONE)
            rv.setViewVisibility(R.id.f_total, View.VISIBLE)
            rv.setTextViewText(R.id.f_total, todayText)
            rv.setTextViewText(R.id.f_sub, c.getString(R.string.widget_today))
        }
        val label: String
        val pi: PendingIntent
        if (s.phase == PH_ASK) {
            label = c.getString(R.string.widget_open)
            pi = openApp(c, 1, false, 40)
        } else if (s.phase == PH_IDLE) {
            label = c.getString(R.string.btn_start)
            pi = timerPi(c)
        } else if (s.running) {
            label = c.getString(R.string.btn_pause)
            pi = timerPi(c)
        } else {
            label = c.getString(R.string.btn_resume)
            pi = timerPi(c)
        }
        rv.setTextViewText(R.id.f_action, label)
        rv.setOnClickPendingIntent(R.id.f_action, pi)
        rv.setOnClickPendingIntent(R.id.f_root, openApp(c, 1, false, 42))
        return rv
    }

    fun handleTimerToggle(c: Context) {
        try {
            Engine.sync(c)
            val s = Engine.load(c)
            if (s.phase == PH_IDLE) {
                Engine.startFocus(c, Store.loadSettings(c).focusMin, false)
            } else if (s.phase == PH_FOCUS || s.phase == PH_SHORT || s.phase == PH_LONG) {
                if (s.running) Engine.pause(c) else Engine.resume(c)
            }
        } catch (e: Throwable) {
            AppLog.log("WIDGET", "Timer toggle failed", e)
        }
        updateAll(c)
    }
}

class TasksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            appWidgetManager.updateAppWidget(appWidgetIds, Widgets.buildTasks(context))
        } catch (e: Throwable) {
            AppLog.log("WIDGET", "Tasks onUpdate failed", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Widgets.ACTION_TOGGLE_TASK) {
            try {
                val id = intent.getLongExtra("id", 0L)
                if (id != 0L) Store.toggleTask(context, id)
            } catch (e: Throwable) {
                AppLog.log("WIDGET", "Toggle failed", e)
            }
            Widgets.updateAll(context)
            return
        }
        super.onReceive(context, intent)
    }
}

class FocusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            appWidgetManager.updateAppWidget(appWidgetIds, Widgets.buildFocus(context))
        } catch (e: Throwable) {
            AppLog.log("WIDGET", "Focus onUpdate failed", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Widgets.ACTION_TOGGLE_TIMER) {
            Widgets.handleTimerToggle(context)
            return
        }
        super.onReceive(context, intent)
    }
}

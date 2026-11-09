package com.khereba1.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/** Quick-capture widget: text note, voice, photo, video in one tap. */
class NotesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            appWidgetManager.updateAppWidget(appWidgetIds, build(context))
        } catch (e: Throwable) {
            AppLog.log("WIDGET", "Notes widget update failed", e)
        }
    }

    private fun open(c: Context, code: Int): PendingIntent {
        val i = Intent(c, MainActivity::class.java)
        i.putExtra("note", code)
        i.data = Uri.parse("rakkez://note/" + code)
        return PendingIntent.getActivity(c, 60 + code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun build(c0: Context): RemoteViews {
        val c = LocaleHelper.wrap(c0)
        val rv = RemoteViews(c0.packageName, R.layout.widget_notes)
        rv.setTextViewText(R.id.n_title, c.getString(R.string.tab_notes))
        rv.setTextViewText(R.id.n_text, c.getString(R.string.wn_text))
        rv.setTextViewText(R.id.n_voice, c.getString(R.string.wn_voice))
        rv.setTextViewText(R.id.n_photo, c.getString(R.string.wn_photo))
        rv.setTextViewText(R.id.n_video, c.getString(R.string.wn_video))
        rv.setOnClickPendingIntent(R.id.n_title, open(c0, 0))
        rv.setOnClickPendingIntent(R.id.n_text, open(c0, 1))
        rv.setOnClickPendingIntent(R.id.n_voice, open(c0, 2))
        rv.setOnClickPendingIntent(R.id.n_photo, open(c0, 3))
        rv.setOnClickPendingIntent(R.id.n_video, open(c0, 4))
        return rv
    }
}

package com.hossa.redoble

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Agenda completa: los eventos de hoy y mañana, en lista, con el estilo del tema. */
class AgendaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { try { update(ctx, mgr, it) } catch (e: Exception) { ErrorLog.soft(ctx, e, "agenda") } }
    }

    companion object {
        fun placed(ctx: Context) = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, AgendaWidget::class.java)).isNotEmpty()

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, AgendaWidget::class.java))
            for (id in ids) try { update(ctx, mgr, id) } catch (e: Exception) { }
            @Suppress("DEPRECATION")
            if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids, R.id.agenda_list)
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val th = Themes.widget(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_agenda)
            v.setInt(R.id.agenda_root, "setBackgroundColor", (Themes.widgetColor(ctx, th) and 0x00FFFFFF) or 0xE6000000.toInt())
            v.setTextViewText(R.id.agenda_head, I18n.tr(ctx, "AGENDA"))
            v.setTextColor(R.id.agenda_head, th.accent(ctx))
            v.setTextColor(R.id.agenda_empty, th.dim)
            v.setTextViewText(R.id.agenda_empty, I18n.tr(ctx,
                if (Extras.hasCalendar(ctx)) "Nada en tu agenda hoy ni mañana" else "Toca para dar permiso al calendario"))
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.agenda_list, Intent(ctx, AgendaService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id); data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            })
            v.setEmptyView(R.id.agenda_list, R.id.agenda_empty)
            // Tocar un evento lo abre en tu calendario; sin permiso, abre Prisma
            v.setPendingIntentTemplate(R.id.agenda_list, PendingIntent.getActivity(ctx, 330,
                Intent(ctx, OpenEventActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            val app = PendingIntent.getActivity(ctx, 331, Intent(ctx, MainActivity::class.java).putExtra("tab", 1)
                .putExtra("askCalendar", !Extras.hasCalendar(ctx))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.agenda_head, app)
            v.setOnClickPendingIntent(R.id.agenda_empty, app)
            mgr.updateAppWidget(id, v)
        }
    }
}

class AgendaService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Ev(val id: Long, val title: String, val begin: Long, val allDay: Boolean)

    private class Factory(val ctx: Context) : RemoteViewsFactory {
        private var items = listOf<Ev>()
        override fun onCreate() {}
        override fun onDestroy() {}
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = items.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds() = true
        override fun getCount() = items.size

        override fun onDataSetChanged() {
            items = try { load() } catch (e: Exception) { emptyList() }
        }

        private fun load(): List<Ev> {
            if (!Extras.hasCalendar(ctx)) return emptyList()
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 2)
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0) }
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
                ContentUris.appendId(it, now); ContentUris.appendId(it, cal.timeInMillis); it.build() }
            val out = mutableListOf<Ev>()
            ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                "${CalendarContract.Instances.END} >= ?", arrayOf(now.toString()), "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && out.size < 20) {
                    val t = c.getString(1)?.trim().orEmpty()
                    val ev = Ev(c.getLong(0), t, c.getLong(2), c.getInt(3) == 1)
                    if (t.isNotEmpty() && dayOf(ev) in 0..1) out += ev
                }
            }
            return out
        }

        /** 0 = hoy, 1 = mañana (los de todo el día se guardan a las 00:00 UTC). */
        private fun dayOf(e: Ev): Int {
            val f = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            if (e.allDay) f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val d = f.format(java.util.Date(e.begin))
            val today = java.util.Calendar.getInstance()
            val loc = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            if (d <= loc.format(today.time)) return 0
            today.add(java.util.Calendar.DAY_OF_YEAR, 1)
            return if (d == loc.format(today.time)) 1 else 2
        }

        override fun getViewAt(position: Int): RemoteViews {
            val e = items.getOrNull(position) ?: return RemoteViews(ctx.packageName, R.layout.widget_agenda_item)
            val th = Themes.widget(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_agenda_item)
            val today = dayOf(e) == 0
            val day = I18n.tr(ctx, if (today) "Hoy" else "Mañana")
            val time = if (e.allDay) I18n.tr(ctx, "todo el día")
                else android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(e.begin))
            v.setTextViewText(R.id.ag_title, e.title)
            v.setTextColor(R.id.ag_title, th.head)
            v.setTextViewText(R.id.ag_time, "$day · $time")
            v.setTextColor(R.id.ag_time, th.dim)
            v.setInt(R.id.ag_bar, "setBackgroundColor", th.accent(ctx))
            v.setViewVisibility(R.id.ag_bar, if (today) View.VISIBLE else View.INVISIBLE)
            v.setOnClickFillInIntent(R.id.ag_row, Intent().setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)))
            return v
        }
    }
}

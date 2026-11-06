package com.yago.habitos;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public class WidgetProvider extends AppWidgetProvider {

    static final String ACTION_TOGGLE = "com.yago.habitos.TOGGLE";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
        Reminders.scheduleNext(c);
    }

    @Override
    public void onReceive(final Context c, Intent it) {
        if (ACTION_TOGGLE.equals(it.getAction())) {
            String kind = it.getStringExtra("kind");
            String id = it.getStringExtra("id");
            if (id != null) Model.toggle(c, Model.today(), kind, id);
            refreshAll(c);
            final PendingResult pr = goAsync();
            new Thread(new Runnable() {
                public void run() {
                    try { Sync.blocking(c); refreshAll(c); Reminders.scheduleNext(c); } finally { pr.finish(); }
                }
            }).start();
            return;
        }
        super.onReceive(c, it);
    }

    static void update(Context c, AppWidgetManager m, int widgetId) {
        String today = Model.today();
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);

        Intent svc = new Intent(c, WidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse("habitos://widget/" + widgetId));
        rv.setRemoteAdapter(R.id.w_list, svc);
        rv.setEmptyView(R.id.w_list, R.id.w_empty);

        Intent toggle = new Intent(c, WidgetProvider.class).setAction(ACTION_TOGGLE);
        PendingIntent pt = PendingIntent.getBroadcast(c, 2, toggle, PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setPendingIntentTemplate(R.id.w_list, pt);

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent po = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.w_header, po);

        int[] p = Model.progress(c, today);
        rv.setTextViewText(R.id.w_date, Model.longDate(today));
        rv.setTextViewText(R.id.w_count, p[0] + "/" + p[1]);
        rv.setProgressBar(R.id.w_progress, 100, p[1] == 0 ? 0 : (100 * p[0] / p[1]), false);

        m.updateAppWidget(widgetId, rv);
        m.notifyAppWidgetViewDataChanged(widgetId, R.id.w_list);
    }

    public static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
        for (int id : ids) update(c, m, id);
    }
}

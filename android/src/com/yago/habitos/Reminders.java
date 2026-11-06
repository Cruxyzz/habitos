package com.yago.habitos;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

/** Agenda o próximo lembrete com AlarmManager e mostra as notificações. */
public final class Reminders {

    static final String CHANNEL = "lembretes";
    static final String ACTION_FIRE = "com.yago.habitos.FIRE";
    static final String ACTION_DONE = "com.yago.habitos.DONE";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Class<?> chCls = Class.forName("android.app.NotificationChannel");
            Object ch = chCls.getConstructor(String.class, CharSequence.class, int.class)
                    .newInstance(CHANNEL, "Lembretes", 4);
            nm.getClass().getMethod("createNotificationChannel", chCls).invoke(nm, ch);
        } catch (Throwable ignored) {
        }
    }

    public static boolean notificationsEnabled(Context c) {
        if (Build.VERSION.SDK_INT < 24) return true;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return (Boolean) nm.getClass().getMethod("areNotificationsEnabled").invoke(nm);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Procura o próximo lembrete depois de agora e agenda um alarme exato para ele. */
    public static void scheduleNext(Context c) {
        long now = System.currentTimeMillis() + 1000;
        long best = Long.MAX_VALUE;
        String bestKey = null;
        String today = Model.today();

        for (int i = -1; i <= 8; i++) {
            String d = Model.addDays(today, i);
            for (JSONObject h : Model.habits(c)) {
                if (!h.optBoolean("remind", true) || !Model.appliesOn(h, d)) continue;
                int m = Model.timeOf(h, d);
                if (m < 0) continue;
                long t = Model.atMinutes(d, m - h.optInt("lead", 0));
                if (t > now && t < best) { best = t; bestKey = "h|" + d + "|" + h.optString("id"); }
            }
            for (JSONObject e : Model.events(c)) {
                if (!e.optBoolean("remind", true) || !Model.eventOn(e, d)) continue;
                int m = Model.parseTime(e.optString("start", ""));
                if (m < 0) continue;
                long t = Model.atMinutes(d, m - e.optInt("lead", 0));
                if (t > now && t < best) { best = t; bestKey = "e|" + d + "|" + e.optString("id"); }
            }
            long mid = Model.atMinutes(d, 24 * 60) + 30000;
            if (mid > now && mid < best) { best = mid; bestKey = "midnight||"; }
        }
        for (JSONObject t : Model.tasks(c)) {
            if (t.optBoolean("done") || !t.optBoolean("remind", true)) continue;
            String d = t.optString("date", "");
            int m = Model.parseTime(t.optString("time", ""));
            if (d.isEmpty() || m < 0) continue;
            long at = Model.atMinutes(d, m);
            if (at > now && at < best) { best = at; bestKey = "t||" + t.optString("id"); }
        }
        if (bestKey == null) return;

        Intent it = new Intent(c, AlarmReceiver.class).setAction(ACTION_FIRE).putExtra("key", bestKey);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, it, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
    }

    static int notifId(String key) { return Math.abs(key.hashCode() % 100000) + 10; }

    @SuppressWarnings("deprecation")
    static Notification.Builder builder(Context c) {
        Notification.Builder b = new Notification.Builder(c);
        if (Build.VERSION.SDK_INT >= 26) {
            try { b.getClass().getMethod("setChannelId", String.class).invoke(b, CHANNEL); } catch (Throwable ignored) { }
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent po = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF3E8E7E)
                .setContentIntent(po)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setCategory(Notification.CATEGORY_REMINDER);
        return b;
    }

    /** Executa o evento disparado pelo alarme. */
    public static void fire(Context c, String key) {
        if (key == null) return;
        String[] p = key.split("\\|", -1);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        String title = null, text = null, date = null, kind = null, id = null;

        if (p[0].equals("h") && p.length == 3) {
            date = p[1];
            JSONObject h = Model.habitById(c, p[2]);
            if (h != null && !Model.isMarked(c, date, p[2])) {
                kind = "h"; id = p[2];
                title = h.optString("title");
                String sub = Model.habitSubtitle(h, date);
                int lead = h.optInt("lead", 0);
                text = lead > 0 ? "Daqui a " + lead + " min · " + sub : (sub.isEmpty() ? "Hora de fazer." : sub);
            }
        } else if (p[0].equals("e") && p.length == 3) {
            JSONObject e = Model.taskById(c, p[2]);
            if (e != null) {
                int lead = e.optInt("lead", 0);
                String et = (lead > 0 ? "Começa em " + (lead >= 60 && lead % 60 == 0 ? (lead / 60) + " h" : lead + " min") : "Começando agora")
                        + " · " + Model.eventSub(e);
                Notification.Builder b = builder(c).setContentTitle(e.optString("title")).setContentText(et)
                        .setStyle(new Notification.BigTextStyle().bigText(et));
                nm.notify(notifId(key), b.build());
            }
        } else if (p[0].equals("t") && p.length == 3) {
            JSONObject t = Model.taskById(c, p[2]);
            if (t != null && !t.optBoolean("done")) {
                kind = "t"; id = p[2]; date = Model.today();
                title = t.optString("title");
                text = t.optString("note", "").isEmpty() ? "Tarefa de hoje" : t.optString("note");
            }
        }
        if (title != null) {
            int nid = notifId(key);
            Intent done = new Intent(c, AlarmReceiver.class).setAction(ACTION_DONE)
                    .putExtra("date", date).putExtra("kind", kind).putExtra("id", id).putExtra("notif", nid);
            PendingIntent pd = PendingIntent.getBroadcast(c, nid, done, PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = builder(c)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .addAction(R.drawable.ic_stat, "Feito ✓", pd);
            nm.notify(nid, b.build());
        }
        WidgetProvider.refreshAll(c);
        scheduleNext(c);
    }

    public static void markDone(Context c, Intent it) {
        String date = it.getStringExtra("date");
        String kind = it.getStringExtra("kind");
        String id = it.getStringExtra("id");
        if (id != null) {
            if ("t".equals(kind)) {
                JSONObject t = Model.taskById(c, id);
                if (t != null && !t.optBoolean("done")) Model.toggle(c, date, "t", id);
            } else if (date != null) {
                Model.setMark(c, date, id, true);
            }
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(it.getIntExtra("notif", 0));
        WidgetProvider.refreshAll(c);
    }

    private Reminders() {}
}

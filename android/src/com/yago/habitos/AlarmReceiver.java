package com.yago.habitos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context c, final Intent it) {
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            public void run() {
                try {
                    String a = it.getAction();
                    if (Reminders.ACTION_DONE.equals(a)) {
                        Reminders.markDone(c, it);
                        Sync.blocking(c);
                        WidgetProvider.refreshAll(c);
                    } else if (Reminders.ACTION_FIRE.equals(a)) {
                        Sync.blocking(c);
                        Reminders.fire(c, it.getStringExtra("key"));
                    }
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }
}

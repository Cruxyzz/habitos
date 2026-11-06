package com.yago.habitos;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class WidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static class Factory implements RemoteViewsFactory {
        final Context c;
        List<Model.Item> items = new ArrayList<>();

        Factory(Context c) { this.c = c; }

        @Override public void onCreate() { load(); }
        @Override public void onDataSetChanged() { load(); }
        @Override public void onDestroy() { items.clear(); }

        void load() {
            List<Model.Item> all = Model.itemsFor(c, Model.today());
            // Pendentes primeiro, feitos no fim.
            List<Model.Item> out = new ArrayList<>();
            for (Model.Item i : all) if (!i.done) out.add(i);
            for (Model.Item i : all) if (i.done) out.add(i);
            items = out;
        }

        @Override public int getCount() { return items.size(); }

        @Override
        public RemoteViews getViewAt(int pos) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_item);
            if (pos < 0 || pos >= items.size()) return rv;
            Model.Item it = items.get(pos);
            rv.setTextViewText(R.id.i_title, it.title);
            rv.setTextColor(R.id.i_title, it.done ? 0xFF6B7883 : 0xFFEEF2F5);
            rv.setImageViewResource(R.id.i_dot, it.done ? R.drawable.dot_on : R.drawable.dot_off);
            int col = it.overdue ? 0xFFE8A15C : (it.color.isEmpty() ? 0xFF7FD1AE : Model.color(it.color, true));
            rv.setInt(R.id.i_dot, "setColorFilter", it.done ? col : (col & 0x00FFFFFF) | 0xCC000000);
            String time = it.overdue ? "atrasada" : (it.minutes >= 0 ? Model.hhmm(it.minutes) + ("e".equals(it.kind) && it.end >= 0 ? "–" + Model.hhmm(it.end) : "") : "");
            rv.setTextViewText(R.id.i_time, time);
            rv.setTextColor(R.id.i_time, it.overdue ? 0xFFE8A15C : 0xFF8C9AA6);

            Intent fill = new Intent();
            fill.putExtra("id", it.id);
            fill.putExtra("kind", it.kind);
            rv.setOnClickFillInIntent(R.id.i_root, fill);
            return rv;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int pos) { return pos < items.size() ? (items.get(pos).kind + items.get(pos).id).hashCode() : pos; }
        @Override public boolean hasStableIds() { return true; }
    }
}

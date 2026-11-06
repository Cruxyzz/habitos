package com.yago.habitos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity {

    int BG, SURFACE, INK, MUTED, LINE, ACCENT, ON_ACCENT, LATE, SOFT;
    boolean dark;
    String sel;
    String weekAnchor; // domingo da semana mostrada
    LinearLayout root;
    Typeface MEDIUM, REGULAR, DISPLAY;

    @Override
    protected void onCreate(Bundle b) {
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        if (dark) {
            BG = 0xFF101417; SURFACE = 0xFF181E22; INK = 0xFFEEF2F5; MUTED = 0xFF8C9AA6;
            LINE = 0xFF262E34; ACCENT = 0xFF7FD1AE; ON_ACCENT = 0xFF0E1A15; LATE = 0xFFE8A15C; SOFT = 0xFF1C2A25;
        } else {
            BG = 0xFFF6F7F5; SURFACE = 0xFFFFFFFF; INK = 0xFF171C1F; MUTED = 0xFF6B757C;
            LINE = 0xFFE4E8E5; ACCENT = 0xFF3E8E7E; ON_ACCENT = 0xFFFFFFFF; LATE = 0xFFD9822B; SOFT = 0xFFE8F2EF;
        }
        MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
        DISPLAY = Typeface.create("sans-serif-light", Typeface.NORMAL);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(110));
        sv.addView(root);
        frame.addView(sv);

        TextView fab = new TextView(this);
        fab.setText("+");
        fab.setTextColor(ON_ACCENT);
        fab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        fab.setTypeface(DISPLAY);
        fab.setGravity(Gravity.CENTER);
        fab.setPadding(0, 0, 0, dp(3));
        GradientDrawable fg = new GradientDrawable();
        fg.setShape(GradientDrawable.OVAL);
        fg.setColor(ACCENT);
        fab.setBackground(fg);
        fab.setElevation(dp(6));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(22), dp(26));
        frame.addView(fab, flp);
        fab.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askAdd(); } });

        setContentView(frame);
        sel = Model.today();
        weekAnchor = Model.addDays(sel, -Model.dow(sel));
        Reminders.ensureChannel(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String t = Model.today();
        if (sel.compareTo(t) > 0) sel = t;
        render();
        Reminders.scheduleNext(this);
        WidgetProvider.refreshAll(this);
        Sync.listener = new Sync.Done() {
            public void run(boolean ok, String msg) { if (!isFinishing()) render(); }
        };
        Sync.now(this, null);
        Updater.check(this, new Updater.Cb() { public void run() { if (!isFinishing()) render(); } });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Sync.listener = null;
    }

    // ---------------- helpers de UI ----------------

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    TextView text(String s, float sp, int color, Typeface tf) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(tf);
        return tv;
    }

    GradientDrawable round(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    LinearLayout.LayoutParams lp(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        return p;
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // ---------------- tela ----------------

    void render() {
        root.removeAllViews();
        final String today = Model.today();
        int[] prog = Model.progress(this, sel);

        // Linha de cima: data + ajustes
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(Model.longDate(sel), 14, MUTED, REGULAR), new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView gear = text("Ajustes", 14, MUTED, MEDIUM);
        gear.setPadding(dp(12), dp(6), dp(12), dp(6));
        gear.setBackground(round(0, LINE, 99));
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSettings(); } });
        top.addView(gear);
        root.addView(top);

        if (Updater.available != null) {
            LinearLayout up = new LinearLayout(this);
            up.setGravity(Gravity.CENTER_VERTICAL);
            up.setPadding(dp(16), dp(12), dp(12), dp(12));
            up.setBackground(round(SOFT, 0, 16));
            LinearLayout ut = new LinearLayout(this);
            ut.setOrientation(LinearLayout.VERTICAL);
            ut.addView(text("Nova versão disponível", 15, INK, MEDIUM));
            String notes = Updater.available.optString("notes", "");
            if (!notes.isEmpty()) ut.addView(text(notes, 13, MUTED, REGULAR));
            up.addView(ut, new LinearLayout.LayoutParams(0, WRAP, 1));
            final TextView ub = text("Atualizar", 14, ON_ACCENT, MEDIUM);
            ub.setPadding(dp(14), dp(8), dp(14), dp(8));
            ub.setBackground(round(ACCENT, 0, 99));
            ub.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                ub.setText("Baixando…");
                Updater.downloadAndInstall(MainActivity.this, new Updater.Progress() { public void done(boolean ok, String msg) {
                    ub.setText("Atualizar");
                    if (!ok) Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                } });
            } });
            up.addView(ub);
            root.addView(up, lp(MATCH, WRAP, dp(14)));
        }

        String title = sel.equals(today) ? "Hoje" : (sel.equals(Model.addDays(today, -1)) ? "Ontem" : Model.DOW[Model.dow(sel)]);
        TextView h1 = text(title, 40, INK, DISPLAY);
        h1.setLetterSpacing(-0.02f);
        root.addView(h1, lp(WRAP, WRAP, dp(2)));

        // Progresso
        LinearLayout pr = new LinearLayout(this);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        Bar bar = new Bar(this, prog[1] == 0 ? 0 : (float) prog[0] / prog[1]);
        pr.addView(bar, new LinearLayout.LayoutParams(0, dp(6), 1));
        TextView pct = text(prog[0] + " de " + prog[1], 14, MUTED, MEDIUM);
        pct.setPadding(dp(12), 0, 0, 0);
        pr.addView(pct);
        root.addView(pr, lp(MATCH, WRAP, dp(14)));

        int left = Model.daysUntilExam();
        if (left >= 0) {
            TextView chip = text(left == 0 ? "Hoje é a prova do concurso" : left + (left == 1 ? " dia" : " dias") + " pra prova do concurso", 13, ACCENT, MEDIUM);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setBackground(round(SOFT, 0, 99));
            root.addView(chip, lp(WRAP, WRAP, dp(14)));
        }

        root.addView(weekStrip(today), lp(MATCH, WRAP, dp(22)));

        // Itens
        List<Model.Item> items = Model.itemsFor(this, sel);
        if (sel.compareTo(Model.START) < 0) {
            root.addView(text("Os hábitos começam em 6 de novembro.", 15, MUTED, REGULAR), lp(WRAP, WRAP, dp(28)));
        } else if (items.isEmpty()) {
            root.addView(text("Nada pra este dia. Toque em + pra criar um hábito ou tarefa.", 15, MUTED, REGULAR), lp(WRAP, WRAP, dp(28)));
        }
        String[] names = {"Atrasadas", "Manhã", "Tarde", "Noite", "Qualquer hora"};
        for (int g = 0; g < names.length; g++) {
            boolean header = false;
            for (Model.Item it : items) {
                if (groupOf(it) != g) continue;
                if (!header) {
                    TextView gh = text(names[g].toUpperCase(), 12, g == 0 ? LATE : MUTED, MEDIUM);
                    gh.setLetterSpacing(0.08f);
                    root.addView(gh, lp(WRAP, WRAP, dp(26)));
                    header = true;
                }
                root.addView(row(it), lp(MATCH, WRAP, dp(8)));
            }
        }
    }

    int groupOf(Model.Item it) {
        if (it.overdue) return 0;
        if (it.minutes < 0) return 4;
        if (it.minutes < 12 * 60) return 1;
        if (it.minutes < 18 * 60) return 2;
        return 3;
    }

    View weekStrip(final String today) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        final boolean canPrev = Model.addDays(weekAnchor, 6).compareTo(Model.START) >= 0 && weekAnchor.compareTo(Model.START) > 0;
        final boolean canNext = Model.addDays(weekAnchor, 7).compareTo(today) <= 0;
        TextView prev = text("‹", 22, canPrev ? INK : LINE, REGULAR);
        prev.setGravity(Gravity.CENTER);
        prev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (canPrev) { weekAnchor = Model.addDays(weekAnchor, -7); render(); } } });
        box.addView(prev, new LinearLayout.LayoutParams(dp(26), dp(56)));
        for (int i = 0; i < 7; i++) {
            final String d = Model.addDays(weekAnchor, i);
            int[] p = Model.progress(this, d);
            boolean enabled = d.compareTo(Model.START) >= 0 && d.compareTo(today) <= 0;
            DayCell cell = new DayCell(this, Model.DOW1[i], Model.parse(d).get(Calendar.DAY_OF_MONTH),
                    enabled && p[1] > 0 ? (float) p[0] / p[1] : 0, d.equals(sel), d.equals(today), enabled);
            if (enabled) cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sel = d; render(); } });
            box.addView(cell, new LinearLayout.LayoutParams(0, dp(62), 1));
        }
        TextView next = text("›", 22, canNext ? INK : LINE, REGULAR);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (canNext) { weekAnchor = Model.addDays(weekAnchor, 7); render(); } } });
        box.addView(next, new LinearLayout.LayoutParams(dp(26), dp(56)));
        return box;
    }

    View row(final Model.Item it) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(66));
        r.setPadding(dp(16), dp(12), dp(14), dp(12));
        r.setBackground(round(SURFACE, dark ? 0 : LINE, 18));

        Check ck = new Check(this, it.done, it.overdue, "t".equals(it.kind));
        r.addView(ck, new LinearLayout.LayoutParams(dp(26), dp(26)));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(14), 0, dp(8), 0);
        TextView t1 = text(it.title, 16, it.done ? MUTED : INK, MEDIUM);
        if (it.done) t1.setPaintFlags(t1.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        txt.addView(t1);
        if (it.sub != null && !it.sub.isEmpty()) {
            TextView t2 = text(it.sub, 13, it.overdue ? LATE : MUTED, REGULAR);
            t2.setPadding(0, dp(2), 0, 0);
            txt.addView(t2);
        }
        r.addView(txt, new LinearLayout.LayoutParams(0, WRAP, 1));

        if (it.streak >= 2) {
            TextView st = text(it.streak + " dias", 12, ACCENT, MEDIUM);
            st.setPadding(dp(10), dp(4), dp(10), dp(4));
            st.setBackground(round(SOFT, 0, 99));
            r.addView(st);
        }
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Model.toggle(MainActivity.this, sel, it.kind, it.id);
            changed();
        } });
        r.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) {
            if ("t".equals(it.kind)) editTask(Model.taskById(MainActivity.this, it.id));
            else editHabit(Model.habitById(MainActivity.this, it.id));
            return true;
        } });
        return r;
    }

    void changed() {
        render();
        WidgetProvider.refreshAll(this);
        Reminders.scheduleNext(this);
        Sync.soon(this);
    }

    // ---------------- criar / editar ----------------

    void askAdd() {
        new AlertDialog.Builder(this)
                .setTitle("Criar")
                .setItems(new String[]{"Novo hábito (repete nos dias escolhidos)", "Nova tarefa (uma vez só)"},
                        new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                            if (w == 0) editHabit(null); else editTask(null);
                        } })
                .show();
    }

    LinearLayout form() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(22), dp(8), dp(22), 0);
        return f;
    }

    EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    TextView label(String s) {
        TextView t = text(s, 13, MUTED, MEDIUM);
        t.setPadding(dp(4), dp(18), 0, dp(6));
        return t;
    }

    TextView pill(String s, boolean on) {
        TextView t = text(s, 14, on ? ON_ACCENT : INK, MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setBackground(on ? round(ACCENT, 0, 99) : round(0, LINE, 99));
        return t;
    }

    void editHabit(final JSONObject existing) {
        final JSONObject h;
        try { h = existing != null ? new JSONObject(existing.toString()) : new JSONObject(); }
        catch (JSONException e) { return; }
        final boolean isNew = existing == null;
        LinearLayout f = form();
        final EditText title = field("Nome do hábito", h.optString("title"));
        final EditText note = field("Detalhe (opcional)", h.optString("note"));
        f.addView(title);
        f.addView(note);

        f.addView(label("Dias"));
        final boolean[] days = new boolean[7];
        JSONArray ja = h.optJSONArray("days");
        if (ja == null) for (int i = 0; i < 7; i++) days[i] = true;
        else for (int i = 0; i < ja.length(); i++) { int d = ja.optInt(i, -1); if (d >= 0 && d < 7) days[d] = true; }
        final LinearLayout dayRow = new LinearLayout(this);
        final Runnable[] drawDays = new Runnable[1];
        drawDays[0] = new Runnable() { public void run() {
            dayRow.removeAllViews();
            for (int i = 0; i < 7; i++) {
                final int k = i;
                TextView p = pill(Model.DOW1[i], days[i]);
                p.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { days[k] = !days[k]; drawDays[0].run(); } });
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(38), 1);
                l.setMargins(dp(2), 0, dp(2), 0);
                dayRow.addView(p, l);
            }
        } };
        drawDays[0].run();
        f.addView(dayRow);

        f.addView(label("Horário"));
        final String[] time = {h.optString("time", "")};
        final TextView timeBtn = pill(time[0].isEmpty() ? "Sem horário" : time[0], false);
        timeBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        timeBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            pickTime(time[0], new TimeSet() { public void set(String t) { time[0] = t; timeBtn.setText(t.isEmpty() ? "Sem horário" : t); } });
        } });
        f.addView(timeBtn, new LinearLayout.LayoutParams(WRAP, WRAP));

        final Switch remind = new Switch(this);
        remind.setText("Lembrete no horário");
        remind.setTextColor(INK);
        remind.setChecked(h.optBoolean("remind", true));
        remind.setPadding(dp(4), dp(18), 0, dp(8));
        f.addView(remind);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Novo hábito" : "Editar hábito")
                .setView(wrap(f))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null);
        if (!isNew) b.setNeutralButton("Excluir", null);
        final AlertDialog dlg = b.create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() { public void onShow(DialogInterface di) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                String t = title.getText().toString().trim();
                if (t.isEmpty()) { title.setError("Dê um nome"); return; }
                JSONArray d = new JSONArray();
                for (int i = 0; i < 7; i++) if (days[i]) d.put(i);
                if (d.length() == 0) { Toast.makeText(MainActivity.this, "Escolha pelo menos um dia", Toast.LENGTH_SHORT).show(); return; }
                try {
                    if (isNew) { h.put("id", Model.newId("h")); h.put("order", Model.nextOrder(MainActivity.this)); h.put("lead", 0); }
                    if (!time[0].equals(h.optString("time", ""))) h.remove("times");
                    if (!note.getText().toString().trim().equals(h.optString("note", ""))) h.remove("notes");
                    h.put("title", t).put("note", note.getText().toString().trim()).put("days", d)
                            .put("time", time[0]).put("remind", remind.isChecked());
                } catch (JSONException ignored) { }
                Model.putHabit(MainActivity.this, h);
                dlg.dismiss();
                changed();
            } });
            if (!isNew) dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                confirmDelete("Excluir o hábito \"" + h.optString("title") + "\"?", new Runnable() { public void run() {
                    try { h.put("deleted", true); } catch (JSONException ignored) { }
                    Model.putHabit(MainActivity.this, h);
                    dlg.dismiss();
                    changed();
                } });
            } });
        } });
        dlg.show();
    }

    void editTask(final JSONObject existing) {
        final JSONObject t;
        try { t = existing != null ? new JSONObject(existing.toString()) : new JSONObject(); }
        catch (JSONException e) { return; }
        final boolean isNew = existing == null;
        LinearLayout f = form();
        final EditText title = field("O que precisa fazer?", t.optString("title"));
        final EditText note = field("Detalhe (opcional)", t.optString("note"));
        f.addView(title);
        f.addView(note);

        final String[] date = {isNew ? (sel.compareTo(Model.today()) >= 0 ? Model.today() : sel) : t.optString("date", "")};
        final String[] time = {t.optString("time", "")};

        f.addView(label("Quando"));
        LinearLayout when = new LinearLayout(this);
        final TextView dateBtn = pill(date[0].isEmpty() ? "Sem data" : Model.shortDate(date[0]), false);
        dateBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        final TextView timeBtn = pill(time[0].isEmpty() ? "Sem horário" : time[0], false);
        timeBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        dateBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            pickDate(date[0], new TimeSet() { public void set(String s) { date[0] = s; dateBtn.setText(s.isEmpty() ? "Sem data" : Model.shortDate(s)); } });
        } });
        timeBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            pickTime(time[0], new TimeSet() { public void set(String s) { time[0] = s; timeBtn.setText(s.isEmpty() ? "Sem horário" : s); } });
        } });
        when.addView(dateBtn);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(WRAP, WRAP);
        gap.leftMargin = dp(8);
        when.addView(timeBtn, gap);
        f.addView(when);

        final Switch remind = new Switch(this);
        remind.setText("Lembrete no horário");
        remind.setTextColor(INK);
        remind.setChecked(t.optBoolean("remind", true));
        remind.setPadding(dp(4), dp(18), 0, dp(8));
        f.addView(remind);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Nova tarefa" : "Editar tarefa")
                .setView(wrap(f))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null);
        if (!isNew) b.setNeutralButton("Excluir", null);
        final AlertDialog dlg = b.create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() { public void onShow(DialogInterface di) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                String s = title.getText().toString().trim();
                if (s.isEmpty()) { title.setError("Escreva a tarefa"); return; }
                try {
                    if (isNew) { t.put("id", Model.newId("t")); t.put("done", false); }
                    t.put("title", s).put("note", note.getText().toString().trim()).put("date", date[0])
                            .put("time", date[0].isEmpty() ? "" : time[0]).put("remind", remind.isChecked());
                } catch (JSONException ignored) { }
                Model.putTask(MainActivity.this, t);
                dlg.dismiss();
                changed();
            } });
            if (!isNew) dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                confirmDelete("Excluir a tarefa \"" + t.optString("title") + "\"?", new Runnable() { public void run() {
                    try { t.put("deleted", true); } catch (JSONException ignored) { }
                    Model.putTask(MainActivity.this, t);
                    dlg.dismiss();
                    changed();
                } });
            } });
        } });
        dlg.show();
    }

    View wrap(View v) {
        ScrollView s = new ScrollView(this);
        s.addView(v);
        return s;
    }

    void confirmDelete(String msg, final Runnable yes) {
        new AlertDialog.Builder(this).setMessage(msg)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { yes.run(); } })
                .show();
    }

    interface TimeSet { void set(String s); }

    void pickTime(String cur, final TimeSet cb) {
        int m = Model.parseTime(cur);
        if (m < 0) m = 19 * 60;
        TimePickerDialog d = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            public void onTimeSet(TimePicker v, int hh, int mm) { cb.set(Model.pad(hh) + ":" + Model.pad(mm)); }
        }, m / 60, m % 60, true);
        d.setButton(DialogInterface.BUTTON_NEUTRAL, "Sem horário", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface di, int w) { cb.set(""); }
        });
        d.show();
    }

    void pickDate(String cur, final TimeSet cb) {
        Calendar c = cur.isEmpty() ? Calendar.getInstance() : Model.parse(cur);
        DatePickerDialog d = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker v, int y, int mo, int day) { cb.set(y + "-" + Model.pad(mo + 1) + "-" + Model.pad(day)); }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.setButton(DialogInterface.BUTTON_NEUTRAL, "Sem data", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface di, int w) { cb.set(""); }
        });
        d.show();
    }

    // ---------------- ajustes ----------------

    void showSettings() {
        LinearLayout f = form();
        f.addView(label("Sincronização com o PC"));
        final EditText url = field("Cole aqui o link do script", Sync.url(this));
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        f.addView(url);
        final TextView status = text(Sync.status(this).isEmpty() ? "Ainda não sincronizado" : Sync.status(this), 13, MUTED, REGULAR);
        status.setPadding(dp(4), dp(6), 0, 0);
        f.addView(status);

        f.addView(label("Notificações"));
        boolean on = Reminders.notificationsEnabled(this);
        TextView nb = pill(on ? "Ativadas · abrir ajustes" : "Desativadas · toque pra ativar", false);
        nb.setPadding(dp(16), dp(10), dp(16), dp(10));
        nb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openNotificationSettings(); } });
        f.addView(nb, new LinearLayout.LayoutParams(WRAP, WRAP));

        TextView tip = text("Pra editar ou excluir um hábito ou tarefa, segure o dedo em cima dele.", 13, MUTED, REGULAR);
        tip.setPadding(dp(4), dp(20), 0, dp(6));
        f.addView(tip);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Ajustes")
                .setView(wrap(f))
                .setNegativeButton("Fechar", null)
                .setPositiveButton("Salvar e sincronizar", null)
                .create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() { public void onShow(DialogInterface di) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                Sync.setUrl(MainActivity.this, url.getText().toString());
                status.setText("Sincronizando…");
                Sync.now(MainActivity.this, new Sync.Done() { public void run(boolean ok, String msg) {
                    status.setText(msg);
                    render();
                } });
            } });
        } });
        dlg.show();
    }

    void openNotificationSettings() {
        Intent it;
        if (Build.VERSION.SDK_INT >= 26) {
            it = new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
        } else {
            it = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        }
        try { startActivity(it); } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    // ---------------- desenhos ----------------

    class Bar extends View {
        final float f;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bar(Context c, float f) { super(c); this.f = f; }
        @Override protected void onDraw(Canvas cv) {
            float h = getHeight(), w = getWidth();
            p.setColor(LINE);
            cv.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
            if (f > 0) {
                p.setColor(ACCENT);
                cv.drawRoundRect(new RectF(0, 0, Math.max(h, w * f), h), h / 2, h / 2, p);
            }
        }
    }

    class Check extends View {
        final boolean on, late, square;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Check(Context c, boolean on, boolean late, boolean square) { super(c); this.on = on; this.late = late; this.square = square; }
        @Override protected void onDraw(Canvas cv) {
            float s = getWidth(), sw = dp(2);
            RectF r = new RectF(sw / 2, sw / 2, s - sw / 2, s - sw / 2);
            float rad = square ? dp(7) : s / 2;
            if (on) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(ACCENT);
                cv.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2.4f));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(ON_ACCENT);
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(s * 0.28f, s * 0.52f);
                path.lineTo(s * 0.44f, s * 0.67f);
                path.lineTo(s * 0.73f, s * 0.37f);
                cv.drawPath(path, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sw);
                p.setColor(late ? LATE : (dark ? 0xFF4A5660 : 0xFFC3CBC6));
                cv.drawRoundRect(r, rad, rad, p);
            }
        }
    }

    class DayCell extends View {
        final String dow;
        final int day;
        final float f;
        final boolean selected, today, enabled;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        DayCell(Context c, String dow, int day, float f, boolean selected, boolean today, boolean enabled) {
            super(c); this.dow = dow; this.day = day; this.f = f; this.selected = selected; this.today = today; this.enabled = enabled;
        }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), cx = w / 2;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(MEDIUM);
            p.setTextSize(dp(11));
            p.setColor(today ? ACCENT : MUTED);
            cv.drawText(dow, cx, dp(12), p);

            float r = Math.min(w / 2 - dp(6), dp(16)), cy = dp(38);
            RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
            if (selected) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(INK);
                cv.drawOval(o, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2.5f));
            p.setStrokeCap(Paint.Cap.ROUND);
            if (enabled && !selected) {
                p.setColor(LINE);
                cv.drawOval(o, p);
            }
            if (enabled && f > 0) {
                p.setColor(ACCENT);
                RectF oi = selected ? new RectF(o.left - dp(4), o.top - dp(4), o.right + dp(4), o.bottom + dp(4)) : o;
                if (selected) { p.setColor(ACCENT); }
                cv.drawArc(oi, -90, 360 * f, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(dp(14));
            p.setColor(selected ? BG : (enabled ? INK : LINE));
            cv.drawText(String.valueOf(day), cx, cy - (p.descent() + p.ascent()) / 2, p);
        }
    }
}

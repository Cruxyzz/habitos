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
        String th = Model.prefs(this).getString("theme", "sistema");
        boolean sysDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = th.equals("escuro") || (th.equals("sistema") && sysDark);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        if (dark) {
            BG = 0xFF101417; SURFACE = 0xFF181E22; INK = 0xFFEEF2F5; MUTED = 0xFF8C9AA6;
            LINE = 0xFF262E34; ON_ACCENT = 0xFF0E1A15; LATE = 0xFFE8A15C;
        } else {
            BG = 0xFFF6F7F5; SURFACE = 0xFFFFFFFF; INK = 0xFF171C1F; MUTED = 0xFF6B757C;
            LINE = 0xFFE4E8E5; ON_ACCENT = 0xFFFFFFFF; LATE = 0xFFD9822B;
        }
        ACCENT = Model.color(Model.prefs(this).getString("accent", "verde"), dark);
        SOFT = blend(ACCENT, BG, dark ? 0.16f : 0.12f);
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

    static int blend(int a, int b, float f) {
        int r = (int) (((a >> 16) & 255) * f + ((b >> 16) & 255) * (1 - f));
        int g = (int) (((a >> 8) & 255) * f + ((b >> 8) & 255) * (1 - f));
        int bl = (int) ((a & 255) * f + (b & 255) * (1 - f));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
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

    String mode = "dia";      // dia, semana, mes, resumo
    Runnable lookApply;
    String monthAnchor;       // qualquer data do mês mostrado

    void render() {
        root.removeAllViews();
        final String today = Model.today();
        boolean isToday = sel.equals(today);

        // Linha de cima: saudação + ajustes
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        String nm = Model.prefs(this).getString("name", "").trim();
        top.addView(text(Model.greeting() + (nm.isEmpty() ? "" : ", " + nm), 15, MUTED, REGULAR), new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView gear = text("Ajustes", 13, MUTED, MEDIUM);
        gear.setPadding(dp(14), dp(7), dp(14), dp(7));
        gear.setBackground(round(0, LINE, 99));
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSettings(); } });
        top.addView(gear);
        root.addView(top);

        String title;
        if (mode.equals("resumo")) title = "Resumo";
        else if (mode.equals("mes")) title = Model.monthName(monthAnchor == null ? sel : monthAnchor);
        else if (mode.equals("semana")) title = "Semana";
        else title = isToday ? "Hoje" : (sel.equals(Model.addDays(today, -1)) ? "Ontem" : Model.DOW[Model.dow(sel)]);
        TextView h1 = text(title, mode.equals("mes") ? 32 : 40, INK, DISPLAY);
        h1.setLetterSpacing(-0.02f);
        root.addView(h1, lp(WRAP, WRAP, 0));
        if (mode.equals("dia")) root.addView(text(Model.longDate(sel), 14, MUTED, REGULAR));

        root.addView(segmented(), lp(MATCH, WRAP, dp(16)));
        updateBanner();

        if (mode.equals("resumo")) renderStats(today);
        else if (mode.equals("semana")) renderWeek(today);
        else if (mode.equals("mes")) renderMonth(today);
        else renderDay(today);
    }

    View segmented() {
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(round(dark ? SURFACE : 0xFFECEFEC, 0, 99));
        String[] keys = {"dia", "semana", "mes", "resumo"}, labels = {"Dia", "Semana", "Mês", "Resumo"};
        for (int i = 0; i < 4; i++) {
            final String k = keys[i];
            boolean on = mode.equals(k);
            TextView t = text(labels[i], 14, on ? INK : MUTED, MEDIUM);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(8), 0, dp(8));
            if (on) { t.setBackground(round(dark ? 0xFF2A333A : 0xFFFFFFFF, 0, 99)); t.setElevation(dp(1)); }
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                mode = k;
                if (k.equals("mes")) monthAnchor = sel;
                if (k.equals("semana")) weekAnchor = Model.addDays(sel, -Model.dow(sel));
                render();
            } });
            seg.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1));
        }
        return seg;
    }

    // ---------------- Dia ----------------

    void renderDay(final String today) {
        boolean isToday = sel.equals(today);
        int[] prog = Model.progress(this, sel);

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(round(SURFACE, dark ? 0 : LINE, 22));
        float frac = prog[1] == 0 ? 0 : (float) prog[0] / prog[1];
        hero.addView(new Ring(this, frac), new LinearLayout.LayoutParams(dp(84), dp(84)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(dp(18), 0, 0, 0);
        int left = prog[1] - prog[0];
        String headline = prog[1] == 0 ? "Dia livre" : left == 0 ? "Tudo feito!" : prog[0] == 0 ? "Vamos começar" : "Faltam " + left;
        ht.addView(text(headline, 20, INK, MEDIUM));
        ht.addView(text(prog[0] + " de " + prog[1] + " hábitos e tarefas", 14, MUTED, REGULAR));
        int ps = Model.perfectStreak(this);
        if (ps > 0) {
            TextView st = text(ps + (ps == 1 ? " dia" : " dias") + " seguidos com tudo feito", 13, ACCENT, MEDIUM);
            st.setPadding(0, dp(6), 0, 0);
            ht.addView(st);
        }
        hero.addView(ht, new LinearLayout.LayoutParams(0, WRAP, 1));
        root.addView(hero, lp(MATCH, WRAP, dp(16)));

        JSONObject nx = isToday ? Model.nextTask(this) : null;
        if (nx != null) {
            LinearLayout nc = new LinearLayout(this);
            nc.setGravity(Gravity.CENTER_VERTICAL);
            nc.setPadding(dp(16), dp(14), dp(16), dp(14));
            nc.setBackground(round(SOFT, 0, 18));
            LinearLayout nt = new LinearLayout(this);
            nt.setOrientation(LinearLayout.VERTICAL);
            TextView lab = text("A SEGUIR", 11, ACCENT, MEDIUM);
            lab.setLetterSpacing(0.1f);
            nt.addView(lab);
            TextView ntt = text(nx.optString("title"), 16, INK, MEDIUM);
            ntt.setPadding(0, dp(2), 0, 0);
            nt.addView(ntt);
            nc.addView(nt, new LinearLayout.LayoutParams(0, WRAP, 1));
            String d = nx.optString("date"), tm = nx.optString("time", "");
            TextView w = text(Model.relDay(d) + (tm.isEmpty() ? "" : "\n" + tm), 13, ACCENT, MEDIUM);
            w.setGravity(Gravity.END);
            nc.addView(w);
            root.addView(nc, lp(MATCH, WRAP, dp(10)));
        }

        root.addView(weekStrip(today), lp(MATCH, WRAP, dp(18)));

        List<Model.Item> items = Model.itemsFor(this, sel);
        List<JSONObject> allHabits = Model.habits(this);
        if (allHabits.size() < 4 && isToday) {
            LinearLayout sc = new LinearLayout(this);
            sc.setOrientation(LinearLayout.VERTICAL);
            sc.setPadding(dp(18), dp(16), dp(18), dp(16));
            sc.setBackground(round(SOFT, 0, 20));
            sc.addView(text(allHabits.isEmpty() ? "Bem-vindo! Comece por aqui" : "Mais ideias de hábitos", 17, INK, MEDIUM));
            TextView st = text("Toque pra adicionar. Depois dá pra mudar o horário e os dias segurando o dedo em cima.", 13, MUTED, REGULAR);
            st.setPadding(0, dp(4), 0, dp(8));
            sc.addView(st);
            for (final String[] sg : Model.SUGGESTIONS) {
                boolean exists = false;
                for (JSONObject h : allHabits) if (h.optString("title").equals(sg[0])) exists = true;
                if (exists) continue;
                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(0, dp(8), 0, dp(8));
                View dot = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Model.color(sg[2], dark));
                dot.setBackground(g);
                line.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
                TextView tl = text(sg[0], 15, INK, REGULAR);
                tl.setPadding(dp(12), 0, 0, 0);
                line.addView(tl, new LinearLayout.LayoutParams(0, WRAP, 1));
                if (!sg[1].isEmpty()) line.addView(text(sg[1] + "   ", 13, MUTED, REGULAR));
                line.addView(text("+", 20, ACCENT, MEDIUM));
                line.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    Model.addSuggestion(MainActivity.this, sg); changed(); } });
                sc.addView(line);
            }
            root.addView(sc, lp(MATCH, WRAP, dp(16)));
        }
        if (items.isEmpty()) {
            TextView em = text("Nada pra este dia.\nToque em + pra criar um compromisso, hábito ou tarefa.", 15, MUTED, REGULAR);
            em.setGravity(Gravity.CENTER);
            em.setPadding(0, dp(28), 0, dp(12));
            root.addView(em, lp(MATCH, WRAP, 0));
        }
        String[] names = {"Atrasadas", "Compromissos", "Hábitos", "Tarefas"};
        for (int g = 0; g < names.length; g++) {
            int cnt = 0, done = 0;
            for (Model.Item it : items) if (groupOf(it) == g) { cnt++; if (it.done) done++; }
            if (cnt == 0) continue;
            LinearLayout gh = new LinearLayout(this);
            gh.setGravity(Gravity.CENTER_VERTICAL);
            TextView gt = text(names[g].toUpperCase(), 12, g == 0 ? LATE : MUTED, MEDIUM);
            gt.setLetterSpacing(0.08f);
            gh.addView(gt, new LinearLayout.LayoutParams(0, WRAP, 1));
            if (g != 1) gh.addView(text(done + "/" + cnt, 12, MUTED, REGULAR));
            root.addView(gh, lp(MATCH, WRAP, dp(24)));
            for (Model.Item it : items) if (groupOf(it) == g)
                root.addView(g == 1 ? eventRow(it, isToday) : row(it), lp(MATCH, WRAP, dp(8)));
        }

        if (sel.compareTo(Model.START) >= 0) root.addView(weekSummary(today), lp(MATCH, WRAP, dp(30)));
    }

    View eventRow(final Model.Item it, boolean isToday) {
        int col = Model.color(it.color.isEmpty() ? "azul" : it.color, dark);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, 0, dp(14), 0);
        r.setBackground(round(SURFACE, dark ? 0 : LINE, 18));

        View bar = new View(this);
        bar.setBackground(round(col, 0, 3));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(5), dp(46));
        bl.setMargins(dp(12), dp(12), dp(12), dp(12));
        r.addView(bar, bl);

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.VERTICAL);
        times.addView(text(it.minutes >= 0 ? Model.hhmm(it.minutes) : "—", 15, INK, MEDIUM));
        if (it.end >= 0) times.addView(text(Model.hhmm(it.end), 13, MUTED, REGULAR));
        r.addView(times, new LinearLayout.LayoutParams(dp(54), WRAP));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(6), dp(12), dp(8), dp(12));
        txt.addView(text(it.title, 16, INK, MEDIUM));
        if (it.sub != null && !it.sub.isEmpty()) {
            TextView t2 = text(it.sub, 13, MUTED, REGULAR);
            t2.setPadding(0, dp(2), 0, 0);
            txt.addView(t2);
        }
        r.addView(txt, new LinearLayout.LayoutParams(0, WRAP, 1));

        if (isToday && it.minutes >= 0) {
            Calendar now = Calendar.getInstance();
            int nm = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            int end = it.end >= 0 ? it.end : it.minutes + 60;
            String badge = nm >= it.minutes && nm < end ? "agora" : (nm >= end ? "encerrado" : null);
            if (badge != null) {
                TextView b = text(badge, 12, badge.equals("agora") ? col : MUTED, MEDIUM);
                b.setPadding(dp(10), dp(4), dp(10), dp(4));
                b.setBackground(round(badge.equals("agora") ? (col & 0x00FFFFFF) | 0x26000000 : 0, badge.equals("agora") ? 0 : LINE, 99));
                r.addView(b);
            }
        }
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            editEvent(Model.taskById(MainActivity.this, it.id)); } });
        return r;
    }

    // ---------------- Resumo ----------------

    void renderStats(final String today) {
        Calendar c0 = Model.parse(today);
        c0.set(Calendar.DAY_OF_MONTH, 1);
        String first = Model.fmt(c0);
        int[] ms = Model.rangeStats(this, first, today);
        int pct = ms[1] == 0 ? 0 : Math.round(100f * ms[0] / ms[1]);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(round(SURFACE, dark ? 0 : LINE, 22));
        TextView lab = text("ESTE MÊS", 12, MUTED, MEDIUM);
        lab.setLetterSpacing(0.08f);
        card.addView(lab);
        LinearLayout big = new LinearLayout(this);
        big.setGravity(Gravity.BOTTOM);
        TextView pv = text(pct + "%", 44, INK, DISPLAY);
        big.addView(pv);
        TextView pd = text("  " + ms[0] + " de " + ms[1] + " concluídos", 14, MUTED, REGULAR);
        pd.setPadding(0, 0, 0, dp(10));
        big.addView(pd);
        card.addView(big);
        card.addView(new Bar(this, ms[1] == 0 ? 0 : (float) ms[0] / ms[1]), lp(MATCH, dp(6), dp(6)));
        root.addView(card, lp(MATCH, WRAP, dp(16)));

        LinearLayout tiles = new LinearLayout(this);
        int[] bw = Model.bestWeekday(this);
        tiles.addView(tile(String.valueOf(ms[2]), ms[2] == 1 ? "dia perfeito no mês" : "dias perfeitos no mês"), new LinearLayout.LayoutParams(0, WRAP, 1));
        View gap = new View(this);
        tiles.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        tiles.addView(tile(String.valueOf(Model.perfectStreak(this)), "dias seguidos com tudo feito"), new LinearLayout.LayoutParams(0, WRAP, 1));
        root.addView(tiles, lp(MATCH, WRAP, dp(10)));
        if (bw != null) {
            LinearLayout t2 = new LinearLayout(this);
            t2.addView(tile(Model.DOW[bw[0]], "seu melhor dia · " + bw[1] + "%"), new LinearLayout.LayoutParams(0, WRAP, 1));
            root.addView(t2, lp(MATCH, WRAP, dp(10)));
        }

        TextView gh = text("HÁBITOS · ÚLTIMOS 30 DIAS", 12, MUTED, MEDIUM);
        gh.setLetterSpacing(0.08f);
        root.addView(gh, lp(WRAP, WRAP, dp(26)));
        String from = Model.addDays(today, -29);
        for (JSONObject h : Model.habits(this)) {
            int col = Model.color(Model.colorKey(h), dark);
            int[] r = Model.habitRate(this, h, from, today);
            int cur = Model.streak(this, h), rec = Model.recordStreak(this, h);
            LinearLayout hc = new LinearLayout(this);
            hc.setOrientation(LinearLayout.VERTICAL);
            hc.setPadding(dp(16), dp(14), dp(16), dp(14));
            hc.setBackground(round(SURFACE, dark ? 0 : LINE, 18));
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            dot.setBackground(g);
            top.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
            TextView tt = text(h.optString("title"), 15, INK, MEDIUM);
            tt.setPadding(dp(10), 0, 0, 0);
            tt.setSingleLine(true);
            tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(tt, new LinearLayout.LayoutParams(0, WRAP, 1));
            top.addView(text(r[1] == 0 ? "—" : Math.round(100f * r[0] / r[1]) + "%", 15, col, MEDIUM));
            hc.addView(top);
            boolean[] app = new boolean[30], done = new boolean[30];
            for (int i = 0; i < 30; i++) {
                String d = Model.addDays(from, i);
                app[i] = Model.appliesOn(h, d);
                done[i] = app[i] && Model.isMarked(this, d, h.optString("id"));
            }
            hc.addView(new Heat(this, app, done, col), lp(MATCH, dp(16), dp(10)));
            TextView sub = text("Sequência atual " + cur + " · recorde " + rec, 12, MUTED, REGULAR);
            sub.setPadding(0, dp(8), 0, 0);
            hc.addView(sub);
            final String hid = h.optString("id");
            hc.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { editHabit(Model.habitById(MainActivity.this, hid)); } });
            root.addView(hc, lp(MATCH, WRAP, dp(8)));
        }
    }

    View tile(String big, String small) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(round(SURFACE, dark ? 0 : LINE, 18));
        t.addView(text(big, 26, ACCENT, DISPLAY));
        t.addView(text(small, 13, MUTED, REGULAR));
        return t;
    }

    class Heat extends View {
        final boolean[] app, done; final int col;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Heat(Context c, boolean[] app, boolean[] done, int col) { super(c); this.app = app; this.done = done; this.col = col; }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), gap = dp(2);
            float cw = (w - gap * (app.length - 1)) / app.length;
            for (int i = 0; i < app.length; i++) {
                float x = i * (cw + gap);
                RectF r = new RectF(x, 0, x + cw, h);
                if (!app[i]) { p.setColor((LINE & 0x00FFFFFF) | 0x55000000); }
                else p.setColor(done[i] ? col : LINE);
                cv.drawRoundRect(r, dp(3), dp(3), p);
            }
        }
    }

    // ---------------- Semana ----------------

    void renderWeek(final String today) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = text("‹", 24, INK, REGULAR);
        prev.setPadding(dp(8), 0, dp(16), 0);
        prev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { weekAnchor = Model.addDays(weekAnchor, -7); render(); } });
        TextView next = text("›", 24, INK, REGULAR);
        next.setPadding(dp(16), 0, dp(8), 0);
        next.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { weekAnchor = Model.addDays(weekAnchor, 7); render(); } });
        String end = Model.addDays(weekAnchor, 6);
        TextView lab = text(Model.shortDate(weekAnchor) + " – " + Model.shortDate(end), 15, INK, MEDIUM);
        lab.setGravity(Gravity.CENTER);
        nav.addView(prev);
        nav.addView(lab, new LinearLayout.LayoutParams(0, WRAP, 1));
        nav.addView(next);
        root.addView(nav, lp(MATCH, WRAP, dp(16)));

        for (int i = 0; i < 7; i++) {
            final String d = Model.addDays(weekAnchor, i);
            boolean isT = d.equals(today), past = d.compareTo(today) < 0;
            List<Model.Item> items = Model.itemsFor(this, d);
            int[] p = Model.progress(this, d);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            card.setBackground(round(SURFACE, isT ? ACCENT : (dark ? 0 : LINE), 18));

            LinearLayout dayCol = new LinearLayout(this);
            dayCol.setOrientation(LinearLayout.VERTICAL);
            dayCol.setGravity(Gravity.CENTER_HORIZONTAL);
            dayCol.addView(text(Model.DOW[Model.dow(d)].substring(0, 3).toUpperCase(), 11, isT ? ACCENT : MUTED, MEDIUM));
            dayCol.addView(text(String.valueOf(Model.parse(d).get(Calendar.DAY_OF_MONTH)), 24, INK, DISPLAY));
            card.addView(dayCol, new LinearLayout.LayoutParams(dp(48), WRAP));

            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(10), 0, 0, 0);
            int shown = 0;
            for (Model.Item it : items) {
                if (!it.kind.equals("e") && !(it.kind.equals("t") && !it.overdue)) continue;
                if (shown++ >= 4) break;
                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                View dot = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(it.kind.equals("e") ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
                if (!it.kind.equals("e")) g.setCornerRadius(dp(2));
                g.setColor(it.kind.equals("e") ? Model.color(it.color, dark) : MUTED);
                dot.setBackground(g);
                line.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
                String tm = it.minutes >= 0 ? Model.hhmm(it.minutes) + "  " : "";
                TextView tl = text(tm + it.title, 14, it.done ? MUTED : INK, REGULAR);
                tl.setSingleLine(true);
                tl.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tl.setPadding(dp(8), dp(2), 0, dp(2));
                line.addView(tl, new LinearLayout.LayoutParams(0, WRAP, 1));
                body.addView(line);
            }
            if (p[1] > 0) {
                LinearLayout pr = new LinearLayout(this);
                pr.setGravity(Gravity.CENTER_VERTICAL);
                pr.setPadding(0, shown > 0 ? dp(8) : dp(4), 0, 0);
                pr.addView(new Bar(this, (past || isT) ? (float) p[0] / p[1] : 0), new LinearLayout.LayoutParams(0, dp(5), 1));
                TextView pc = text((past || isT) ? p[0] + "/" + p[1] : p[1] + " itens", 12, MUTED, REGULAR);
                pc.setPadding(dp(10), 0, 0, 0);
                pr.addView(pc);
                body.addView(pr);
            } else if (shown == 0) {
                body.addView(text("Livre", 14, MUTED, REGULAR));
            }
            card.addView(body, new LinearLayout.LayoutParams(0, WRAP, 1));
            card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (d.compareTo(Model.today()) > 0 || d.compareTo(Model.START) < 0) { showDayPreview(d); return; }
                sel = d; mode = "dia"; weekAnchor = Model.addDays(sel, -Model.dow(sel)); render();
            } });
            root.addView(card, lp(MATCH, WRAP, dp(8)));
        }
    }

    // ---------------- Mês ----------------

    void renderMonth(final String today) {
        if (monthAnchor == null) monthAnchor = sel;
        Calendar first = Model.parse(monthAnchor);
        first.set(Calendar.DAY_OF_MONTH, 1);
        final String firstDay = Model.fmt(first);
        int daysIn = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int offset = first.get(Calendar.DAY_OF_WEEK) - 1;

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = text("‹  Anterior", 14, MUTED, MEDIUM);
        prev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Calendar c = Model.parse(firstDay); c.add(Calendar.MONTH, -1); monthAnchor = Model.fmt(c); render(); } });
        TextView next = text("Próximo  ›", 14, MUTED, MEDIUM);
        next.setGravity(Gravity.END);
        next.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Calendar c = Model.parse(firstDay); c.add(Calendar.MONTH, 1); monthAnchor = Model.fmt(c); render(); } });
        nav.addView(prev, new LinearLayout.LayoutParams(0, WRAP, 1));
        nav.addView(next, new LinearLayout.LayoutParams(0, WRAP, 1));
        root.addView(nav, lp(MATCH, WRAP, dp(16)));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(8), dp(12), dp(8), dp(12));
        grid.setBackground(round(SURFACE, dark ? 0 : LINE, 22));
        LinearLayout head = new LinearLayout(this);
        for (int i = 0; i < 7; i++) {
            TextView t = text(Model.DOW1[i], 12, MUTED, MEDIUM);
            t.setGravity(Gravity.CENTER);
            head.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1));
        }
        grid.addView(head);
        int cells = ((offset + daysIn + 6) / 7) * 7;
        LinearLayout week = null;
        for (int i = 0; i < cells; i++) {
            if (i % 7 == 0) { week = new LinearLayout(this); grid.addView(week, lp(MATCH, dp(52), dp(4))); }
            int dayNum = i - offset + 1;
            if (dayNum < 1 || dayNum > daysIn) { week.addView(new View(this), new LinearLayout.LayoutParams(0, MATCH, 1)); continue; }
            final String d = Model.addDays(firstDay, dayNum - 1);
            boolean enabled = d.compareTo(today) <= 0 && d.compareTo(Model.START) >= 0;
            int[] p = Model.progress(this, d);
            MonthCell cell = new MonthCell(this, dayNum, enabled && p[1] > 0 ? (float) p[0] / p[1] : -1,
                    d.equals(today), d.equals(sel), Model.hasMarker(this, d), d.compareTo(today) > 0);
            cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (d.compareTo(Model.today()) > 0) { showDayPreview(d); return; }
                sel = d; mode = "dia"; weekAnchor = Model.addDays(sel, -Model.dow(sel)); render();
            } });
            week.addView(cell, new LinearLayout.LayoutParams(0, MATCH, 1));
        }
        root.addView(grid, lp(MATCH, WRAP, dp(10)));

        // Lista do mês: compromissos únicos e tarefas com data
        String last = Model.addDays(firstDay, daysIn - 1);
        java.util.ArrayList<String[]> rows = new java.util.ArrayList<>();
        for (JSONObject e : Model.events(this)) {
            String d = e.optString("date", "");
            JSONArray days = e.optJSONArray("days");
            if ((days == null || days.length() == 0) && d.compareTo(firstDay) >= 0 && d.compareTo(last) <= 0)
                rows.add(new String[]{d + e.optString("start"), d, e.optString("start"), e.optString("title"), e.optString("color", "azul")});
        }
        for (JSONObject t : Model.tasks(this)) {
            String d = t.optString("date", "");
            if (!d.isEmpty() && d.compareTo(firstDay) >= 0 && d.compareTo(last) <= 0)
                rows.add(new String[]{d + t.optString("time"), d, t.optString("time"), t.optString("title") + (t.optBoolean("done") ? " ✓" : ""), ""});
        }
        java.util.Collections.sort(rows, new java.util.Comparator<String[]>() { public int compare(String[] a, String[] b) { return a[0].compareTo(b[0]); } });
        if (!rows.isEmpty()) {
            TextView gt = text("NESTE MÊS", 12, MUTED, MEDIUM);
            gt.setLetterSpacing(0.08f);
            root.addView(gt, lp(WRAP, WRAP, dp(24)));
            for (String[] r : rows) {
                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(dp(14), dp(12), dp(14), dp(12));
                line.setBackground(round(SURFACE, dark ? 0 : LINE, 14));
                TextView dd = text(Model.shortDate(r[1]), 14, r[4].isEmpty() ? MUTED : Model.color(r[4], dark), MEDIUM);
                line.addView(dd, new LinearLayout.LayoutParams(dp(64), WRAP));
                line.addView(text(r[3], 15, INK, REGULAR), new LinearLayout.LayoutParams(0, WRAP, 1));
                if (!r[2].isEmpty()) line.addView(text(r[2], 13, MUTED, REGULAR));
                root.addView(line, lp(MATCH, WRAP, dp(6)));
            }
        }
    }

    void showDayPreview(String d) {
        StringBuilder sb = new StringBuilder();
        for (Model.Item it : Model.itemsFor(this, d)) {
            if (it.kind.equals("h")) continue;
            if (it.minutes >= 0) sb.append(Model.hhmm(it.minutes)).append(it.end >= 0 ? "–" + Model.hhmm(it.end) : "").append("  ");
            sb.append(it.title).append("\n");
        }
        int habits = 0;
        for (Model.Item it : Model.itemsFor(this, d)) if (it.kind.equals("h")) habits++;
        if (habits > 0) sb.append("\n").append(habits).append(" hábitos programados");
        new AlertDialog.Builder(this).setTitle(Model.longDate(d))
                .setMessage(sb.length() == 0 ? "Nada programado." : sb.toString().trim())
                .setPositiveButton("Fechar", null).show();
    }

    class MonthCell extends View {
        final int day; final float f; final boolean today, selected, marker, future;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        MonthCell(Context c, int day, float f, boolean today, boolean selected, boolean marker, boolean future) {
            super(c); this.day = day; this.f = f; this.today = today; this.selected = selected; this.marker = marker; this.future = future;
        }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2 - dp(3);
            float r = Math.min(w, h) / 2 - dp(6);
            RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2.5f));
            p.setStrokeCap(Paint.Cap.ROUND);
            if (f >= 0) {
                p.setColor(LINE);
                cv.drawOval(o, p);
                if (f > 0) { p.setColor(ACCENT); cv.drawArc(o, -90, 360 * f, false, p); }
            }
            if (today) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(INK);
                cv.drawOval(new RectF(cx - r + dp(4), cy - r + dp(4), cx + r - dp(4), cy + r - dp(4)), p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(today ? MEDIUM : REGULAR);
            p.setTextSize(dp(14));
            p.setColor(today ? BG : (future ? MUTED : INK));
            cv.drawText(String.valueOf(day), cx, cy - (p.descent() + p.ascent()) / 2, p);
            if (marker) {
                p.setColor(LATE);
                cv.drawCircle(cx, h - dp(4), dp(2.5f), p);
            }
        }
    }

    void updateBanner() {
        if (Updater.available == null) return;
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

    View weekSummary(String today) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackground(round(SURFACE, dark ? 0 : LINE, 22));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Últimos 7 dias", 16, INK, MEDIUM), new LinearLayout.LayoutParams(0, WRAP, 1));
        int tot = 0, done = 0;
        float[] vals = new float[7];
        String[] labs = new String[7];
        for (int i = 0; i < 7; i++) {
            String d = Model.addDays(today, i - 6);
            labs[i] = Model.DOW1[Model.dow(d)];
            if (d.compareTo(Model.START) < 0) { vals[i] = -1; continue; }
            int[] p = Model.progress(this, d);
            tot += p[1]; done += p[0];
            vals[i] = p[1] == 0 ? 0 : (float) p[0] / p[1];
        }
        int pct = tot == 0 ? 0 : Math.round(100f * done / tot);
        head.addView(text(pct + "%", 16, ACCENT, MEDIUM));
        box.addView(head);
        box.addView(new Bars(this, vals, labs), lp(MATCH, dp(96), dp(12)));
        int best = Model.bestHabitStreak(this);
        if (best >= 2) {
            TextView b = text("Maior sequência atual: " + best + " dias", 13, MUTED, REGULAR);
            b.setPadding(0, dp(10), 0, 0);
            box.addView(b);
        }
        return box;
    }

    int groupOf(Model.Item it) {
        if (it.overdue) return 0;
        if ("e".equals(it.kind)) return 1;
        if ("h".equals(it.kind)) return 2;
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

        Check ck = new Check(this, it.done, it.overdue, "t".equals(it.kind), it.color.isEmpty() ? ACCENT : Model.color(it.color, dark));
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
            int sc = it.color.isEmpty() ? ACCENT : Model.color(it.color, dark);
            TextView st = text(it.streak + " dias", 12, sc, MEDIUM);
            st.setPadding(dp(10), dp(4), dp(10), dp(4));
            st.setBackground(round((sc & 0x00FFFFFF) | 0x22000000, 0, 99));
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
                .setItems(new String[]{"Compromisso · horário de início e fim", "Hábito · algo que você repete e marca", "Tarefa · algo pra fazer uma vez"},
                        new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                            if (w == 0) editEvent(null); else if (w == 1) editHabit(null); else editTask(null);
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

        f.addView(label("Cor"));
        final String[] color = {isNew ? Model.COLOR_KEYS[(int) (Math.random() * Model.COLOR_KEYS.length)] : Model.colorKey(h)};
        final LinearLayout colorRow = new LinearLayout(this);
        final Runnable[] drawColors = new Runnable[1];
        drawColors[0] = new Runnable() { public void run() {
            colorRow.removeAllViews();
            for (final String k : Model.COLOR_KEYS) {
                View dot = new View(MainActivity.this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Model.color(k, dark));
                if (k.equals(color[0])) g.setStroke(dp(3), INK);
                dot.setBackground(g);
                dot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { color[0] = k; drawColors[0].run(); } });
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(32), dp(32));
                l.setMargins(dp(3), 0, dp(5), dp(8));
                colorRow.addView(dot, l);
            }
        } };
        drawColors[0].run();
        f.addView(colorRow);

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
                            .put("time", time[0]).put("remind", remind.isChecked()).put("color", color[0]);
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

    void editEvent(final JSONObject existing) {
        final JSONObject e;
        try { e = existing != null ? new JSONObject(existing.toString()) : new JSONObject(); }
        catch (JSONException ex) { return; }
        final boolean isNew = existing == null;
        LinearLayout f = form();
        final EditText title = field("Nome do compromisso", e.optString("title"));
        final EditText note = field("Local ou detalhe (opcional)", e.optString("note"));
        f.addView(title);
        f.addView(note);

        JSONArray ja = e.optJSONArray("days");
        final boolean[] weekly = {ja != null && ja.length() > 0};
        final boolean[] days = new boolean[7];
        if (ja != null) for (int i = 0; i < ja.length(); i++) { int d = ja.optInt(i, -1); if (d >= 0 && d < 7) days[d] = true; }
        final String[] date = {isNew ? (sel.compareTo(Model.today()) >= 0 ? sel : Model.today()) : e.optString("date", "")};
        if (date[0].isEmpty()) date[0] = Model.today();
        final String[] start = {e.optString("start", "")}, end = {e.optString("end", "")};
        final int[] lead = {isNew ? 15 : e.optInt("lead", 0)};
        final String[] color = {isNew ? "azul" : e.optString("color", "azul")};

        f.addView(label("Repete?"));
        final LinearLayout repRow = new LinearLayout(this);
        final LinearLayout whenBox = new LinearLayout(this);
        whenBox.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] draw = new Runnable[1];
        draw[0] = new Runnable() { public void run() {
            repRow.removeAllViews();
            String[] opts = {"Uma vez", "Toda semana"};
            for (int i = 0; i < 2; i++) {
                final boolean w = i == 1;
                TextView p = pill(opts[i], weekly[0] == w);
                p.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { weekly[0] = w; draw[0].run(); } });
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(38), 1);
                l.setMargins(dp(2), 0, dp(2), 0);
                repRow.addView(p, l);
            }
            whenBox.removeAllViews();
            if (weekly[0]) {
                LinearLayout dayRow = new LinearLayout(MainActivity.this);
                for (int i = 0; i < 7; i++) {
                    final int k = i;
                    TextView p = pill(Model.DOW1[i], days[i]);
                    p.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { days[k] = !days[k]; draw[0].run(); } });
                    LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(38), 1);
                    l.setMargins(dp(2), dp(10), dp(2), 0);
                    dayRow.addView(p, l);
                }
                whenBox.addView(dayRow);
            } else {
                final TextView db = pill(Model.longDate(date[0]), false);
                db.setPadding(dp(16), dp(10), dp(16), dp(10));
                db.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    pickDate(date[0], new TimeSet() { public void set(String x) { if (!x.isEmpty()) { date[0] = x; draw[0].run(); } } });
                } });
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(WRAP, WRAP);
                l.topMargin = dp(10);
                whenBox.addView(db, l);
            }
        } };
        draw[0].run();
        f.addView(repRow);
        f.addView(whenBox);

        f.addView(label("Horário"));
        LinearLayout tr = new LinearLayout(this);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        final TextView sb = pill(start[0].isEmpty() ? "Início" : start[0], false);
        sb.setPadding(dp(18), dp(10), dp(18), dp(10));
        final TextView eb = pill(end[0].isEmpty() ? "Fim" : end[0], false);
        eb.setPadding(dp(18), dp(10), dp(18), dp(10));
        sb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            pickTime(start[0], new TimeSet() { public void set(String x) { start[0] = x; sb.setText(x.isEmpty() ? "Início" : x); } });
        } });
        eb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            pickTime(end[0].isEmpty() ? start[0] : end[0], new TimeSet() { public void set(String x) { end[0] = x; eb.setText(x.isEmpty() ? "Fim" : x); } });
        } });
        tr.addView(sb);
        TextView dash = text("  até  ", 14, MUTED, REGULAR);
        tr.addView(dash);
        tr.addView(eb);
        f.addView(tr);

        f.addView(label("Avisar"));
        final LinearLayout leadRow = new LinearLayout(this);
        final int[] leads = {0, 10, 30, 60, 120};
        final String[] leadNames = {"Na hora", "10 min", "30 min", "1 h", "2 h"};
        final boolean[] remindOn = {e.optBoolean("remind", true)};
        final Runnable[] drawLead = new Runnable[1];
        drawLead[0] = new Runnable() { public void run() {
            leadRow.removeAllViews();
            TextView off = pill("Não", !remindOn[0]);
            off.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { remindOn[0] = false; drawLead[0].run(); } });
            LinearLayout.LayoutParams l0 = new LinearLayout.LayoutParams(0, dp(36), 1);
            l0.setMargins(dp(2), 0, dp(2), 0);
            leadRow.addView(off, l0);
            for (int i = 0; i < leads.length; i++) {
                final int k = i;
                TextView p = pill(leadNames[i], remindOn[0] && lead[0] == leads[i]);
                p.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                p.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { remindOn[0] = true; lead[0] = leads[k]; drawLead[0].run(); } });
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(36), 1);
                l.setMargins(dp(2), 0, dp(2), 0);
                leadRow.addView(p, l);
            }
        } };
        drawLead[0].run();
        f.addView(leadRow);

        f.addView(label("Cor"));
        final LinearLayout colorRow = new LinearLayout(this);
        final Runnable[] drawColors = new Runnable[1];
        drawColors[0] = new Runnable() { public void run() {
            colorRow.removeAllViews();
            for (final String k : Model.COLOR_KEYS) {
                View dot = new View(MainActivity.this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Model.color(k, dark));
                if (k.equals(color[0])) g.setStroke(dp(3), INK);
                dot.setBackground(g);
                dot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { color[0] = k; drawColors[0].run(); } });
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(32), dp(32));
                l.setMargins(dp(3), 0, dp(5), dp(8));
                colorRow.addView(dot, l);
            }
        } };
        drawColors[0].run();
        f.addView(colorRow);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Novo compromisso" : "Editar compromisso")
                .setView(wrap(f))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null);
        if (!isNew) b.setNeutralButton("Excluir", null);
        final AlertDialog dlg = b.create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() { public void onShow(DialogInterface di) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                String t = title.getText().toString().trim();
                if (t.isEmpty()) { title.setError("Dê um nome"); return; }
                if (start[0].isEmpty()) { Toast.makeText(MainActivity.this, "Escolha o horário de início", Toast.LENGTH_SHORT).show(); return; }
                if (!end[0].isEmpty() && Model.parseTime(end[0]) <= Model.parseTime(start[0])) {
                    Toast.makeText(MainActivity.this, "O fim precisa ser depois do início", Toast.LENGTH_SHORT).show(); return; }
                JSONArray d = new JSONArray();
                if (weekly[0]) {
                    for (int i = 0; i < 7; i++) if (days[i]) d.put(i);
                    if (d.length() == 0) { Toast.makeText(MainActivity.this, "Escolha pelo menos um dia", Toast.LENGTH_SHORT).show(); return; }
                }
                try {
                    if (isNew) e.put("id", Model.newId("e"));
                    e.put("type", "event").put("title", t).put("note", note.getText().toString().trim())
                            .put("days", d).put("date", weekly[0] ? "" : date[0]).put("start", start[0]).put("end", end[0])
                            .put("lead", lead[0]).put("remind", remindOn[0]).put("color", color[0]);
                } catch (JSONException ignored) { }
                Model.putTask(MainActivity.this, e);
                dlg.dismiss();
                changed();
            } });
            if (!isNew) dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                confirmDelete("Excluir o compromisso \"" + e.optString("title") + "\"?", new Runnable() { public void run() {
                    try { e.put("deleted", true); } catch (JSONException ignored) { }
                    Model.putTask(MainActivity.this, e);
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
        f.addView(label("Sincronização com o PC (opcional)"));
        final EditText url = field("Cole aqui o link do script", Sync.url(this));
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        f.addView(url);
        final TextView status = text(Sync.status(this).isEmpty() ? "Ainda não sincronizado" : Sync.status(this), 13, MUTED, REGULAR);
        status.setPadding(dp(4), dp(6), 0, 0);
        f.addView(status);

        f.addView(label("Seu nome (aparece no cumprimento)"));
        final EditText nameF = field("Ex.: Yago", Model.prefs(this).getString("name", ""));
        f.addView(nameF);

        f.addView(label("Tema"));
        final String[] theme = {Model.prefs(this).getString("theme", "sistema")};
        final LinearLayout themeRow = new LinearLayout(this);
        final Runnable[] drawTheme = new Runnable[1];
        drawTheme[0] = new Runnable() { public void run() {
            themeRow.removeAllViews();
            String[] k = {"sistema", "claro", "escuro"}, l = {"Automático", "Claro", "Escuro"};
            for (int i = 0; i < 3; i++) {
                final String kk = k[i];
                TextView p = pill(l[i], theme[0].equals(kk));
                p.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { theme[0] = kk; drawTheme[0].run(); } });
                LinearLayout.LayoutParams lpp = new LinearLayout.LayoutParams(0, dp(38), 1);
                lpp.setMargins(dp(2), 0, dp(2), 0);
                themeRow.addView(p, lpp);
            }
        } };
        drawTheme[0].run();
        f.addView(themeRow);

        f.addView(label("Cor de destaque"));
        final String[] accent = {Model.prefs(this).getString("accent", "verde")};
        final LinearLayout accRow = new LinearLayout(this);
        final Runnable[] drawAcc = new Runnable[1];
        drawAcc[0] = new Runnable() { public void run() {
            accRow.removeAllViews();
            for (final String k : Model.COLOR_KEYS) {
                View dot = new View(MainActivity.this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Model.color(k, dark));
                if (k.equals(accent[0])) g.setStroke(dp(3), INK);
                dot.setBackground(g);
                dot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { accent[0] = k; drawAcc[0].run(); } });
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(32), dp(32));
                l.setMargins(dp(3), 0, dp(5), 0);
                accRow.addView(dot, l);
            }
        } };
        drawAcc[0].run();
        f.addView(accRow);
        final Runnable applyLook = new Runnable() { public void run() {
            boolean changedLook = !theme[0].equals(Model.prefs(MainActivity.this).getString("theme", "sistema"))
                    || !accent[0].equals(Model.prefs(MainActivity.this).getString("accent", "verde"));
            Model.prefs(MainActivity.this).edit().putString("name", nameF.getText().toString().trim())
                    .putString("theme", theme[0]).putString("accent", accent[0]).apply();
            if (changedLook) recreate(); else render();
        } };
        lookApply = applyLook;

        f.addView(label("Notificações"));
        boolean on = Reminders.notificationsEnabled(this);
        TextView nb = pill(on ? "Ativadas · abrir ajustes" : "Desativadas · toque pra ativar", false);
        nb.setPadding(dp(16), dp(10), dp(16), dp(10));
        nb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openNotificationSettings(); } });
        f.addView(nb, new LinearLayout.LayoutParams(WRAP, WRAP));

        f.addView(label("Atualização"));
        final TextView ub = pill("Procurar atualização · versão " + Updater.currentVersion(this), false);
        ub.setPadding(dp(16), dp(10), dp(16), dp(10));
        ub.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            ub.setText("Procurando…");
            Updater.check(MainActivity.this, null, new Updater.Progress() { public void done(boolean ok, String msg) {
                if ("nova".equals(msg)) {
                    ub.setText("Baixando a versão nova…");
                    Updater.downloadAndInstall(MainActivity.this, new Updater.Progress() { public void done(boolean ok2, String m2) {
                        ub.setText(ok2 ? "Confirme a instalação" : m2);
                    } });
                } else ub.setText("atual".equals(msg) ? "Você já está na versão mais nova" : msg);
                render();
            } });
        } });
        f.addView(ub, new LinearLayout.LayoutParams(WRAP, WRAP));

        TextView tip = text("Compromissos: toque pra editar. Hábitos e tarefas: segure o dedo em cima pra editar ou excluir.", 13, MUTED, REGULAR);
        tip.setPadding(dp(4), dp(20), 0, dp(6));
        f.addView(tip);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Ajustes")
                .setView(wrap(f))
                .setNegativeButton("Fechar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() { public void onShow(DialogInterface di) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (lookApply != null) { Runnable r = lookApply; lookApply = null; dlg.dismiss(); Sync.setUrl(MainActivity.this, url.getText().toString()); Sync.now(MainActivity.this, null); r.run(); return; }
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
        final int col;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Check(Context c, boolean on, boolean late, boolean square, int col) { super(c); this.on = on; this.late = late; this.square = square; this.col = col; }
        @Override protected void onDraw(Canvas cv) {
            float s = getWidth(), sw = dp(2);
            RectF r = new RectF(sw / 2, sw / 2, s - sw / 2, s - sw / 2);
            float rad = square ? dp(7) : s / 2;
            if (on) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(col);
                cv.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2.4f));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(dark ? 0xFF101417 : 0xFFFFFFFF);
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(s * 0.28f, s * 0.52f);
                path.lineTo(s * 0.44f, s * 0.67f);
                path.lineTo(s * 0.73f, s * 0.37f);
                cv.drawPath(path, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor((col & 0x00FFFFFF) | 0x1A000000);
                cv.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sw);
                p.setColor(late ? LATE : col);
                cv.drawRoundRect(r, rad, rad, p);
            }
        }
    }

    class Ring extends View {
        final float f;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Ring(Context c, float f) { super(c); this.f = f; }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), sw = dp(8);
            RectF r = new RectF(sw / 2, sw / 2, w - sw / 2, w - sw / 2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(LINE);
            cv.drawOval(r, p);
            if (f > 0) { p.setColor(ACCENT); cv.drawArc(r, -90, Math.max(4, 360 * f), false, p); }
            p.setStyle(Paint.Style.FILL);
            p.setColor(INK);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(MEDIUM);
            p.setTextSize(dp(20));
            String t = Math.round(f * 100) + "%";
            cv.drawText(t, w / 2, w / 2 - (p.descent() + p.ascent()) / 2, p);
        }
    }

    class Bars extends View {
        final float[] v;
        final String[] labs;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bars(Context c, float[] v, String[] labs) { super(c); this.v = v; this.labs = labs; }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), lh = dp(18), bw = dp(14);
            float area = h - lh;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(MEDIUM);
            p.setTextSize(dp(11));
            for (int i = 0; i < v.length; i++) {
                float cx = w * (i + 0.5f) / v.length;
                RectF bg = new RectF(cx - bw / 2, 0, cx + bw / 2, area);
                p.setColor(LINE);
                cv.drawRoundRect(bg, bw / 2, bw / 2, p);
                if (v[i] > 0) {
                    float top = area * (1 - v[i]);
                    p.setColor(v[i] >= 1 ? ACCENT : (ACCENT & 0x00FFFFFF) | 0x99000000);
                    cv.drawRoundRect(new RectF(cx - bw / 2, Math.min(top, area - bw), cx + bw / 2, area), bw / 2, bw / 2, p);
                }
                p.setColor(i == v.length - 1 ? INK : MUTED);
                cv.drawText(labs[i], cx, h - dp(2), p);
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

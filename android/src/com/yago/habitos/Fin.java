package com.yago.habitos;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Dinheiro. Fica dentro de "marks" (chaves cfg|fin, tx|id, goal|id) para sincronizar
 * pelo mesmo script e não aparecer em versões antigas do app. Valores em centavos.
 */
public final class Fin {

    public static final String[] OUT_CATS = {"lanche", "transporte", "lazer", "jogos", "roupas", "escola", "assinaturas", "presentes", "outros"};
    public static final String[] OUT_NAMES = {"Lanche e comida", "Transporte", "Lazer e passeios", "Jogos", "Roupas", "Escola e estudo", "Assinaturas", "Presentes", "Outros"};
    public static final String[] OUT_COLORS = {"laranja", "azul", "rosa", "roxo", "ciano", "amarelo", "verde", "rosa", "azul"};
    public static final String[] IN_CATS = {"extra", "presente", "venda", "outros"};
    public static final String[] IN_NAMES = {"Dinheiro extra", "Presente", "Venda ou bico", "Outros"};

    static JSONObject marks(Context c) { return Model.get(c).optJSONObject("marks"); }

    static void put(Context c, String key, JSONObject o) {
        synchronized (Model.class) {
            try {
                o.put("t", System.currentTimeMillis());
                marks(c).put(key, o);
            } catch (JSONException ignored) { }
            Model.save(c);
        }
    }

    // ---------- configuração ----------

    public static JSONObject cfg(Context c) {
        JSONObject o = marks(c).optJSONObject("cfg|fin");
        return o != null && o.optBoolean("v") ? o : null;
    }

    public static void setCfg(Context c, long allowance, int payday, int savePct) {
        JSONObject o = new JSONObject();
        try { o.put("v", true).put("allowance", allowance).put("payday", payday).put("savePct", savePct); } catch (JSONException ignored) { }
        put(c, "cfg|fin", o);
    }

    // ---------- lançamentos ----------

    public static List<JSONObject> txs(Context c) {
        List<JSONObject> out = new ArrayList<>();
        JSONObject m = marks(c);
        Iterator<String> it = m.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!k.startsWith("tx|")) continue;
            JSONObject o = m.optJSONObject(k);
            if (o != null && o.optBoolean("v")) {
                try { JSONObject cp = new JSONObject(o.toString()); cp.put("key", k); out.add(cp); } catch (JSONException ignored) { }
            }
        }
        Collections.sort(out, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) {
                int x = b.optString("date").compareTo(a.optString("date"));
                return x != 0 ? x : Long.compare(b.optLong("created"), a.optLong("created"));
            }
        });
        return out;
    }

    public static void addTx(Context c, String type, long amount, String cat, String note, String date, String goal) {
        JSONObject o = new JSONObject();
        try {
            o.put("v", true).put("type", type).put("amount", amount).put("cat", cat).put("note", note)
                    .put("date", date).put("goal", goal == null ? "" : goal).put("created", System.currentTimeMillis());
        } catch (JSONException ignored) { }
        put(c, "tx|" + Model.newId("x"), o);
    }

    public static void updateTx(Context c, String key, JSONObject o) {
        try { o.remove("key"); } catch (Exception ignored) { }
        put(c, key, o);
    }

    public static void deleteTx(Context c, String key) {
        JSONObject o = new JSONObject();
        try { o.put("v", false); } catch (JSONException ignored) { }
        put(c, key, o);
    }

    // ---------- metas ----------

    public static List<JSONObject> goals(Context c) {
        List<JSONObject> out = new ArrayList<>();
        JSONObject m = marks(c);
        Iterator<String> it = m.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!k.startsWith("goal|")) continue;
            JSONObject o = m.optJSONObject(k);
            if (o != null && o.optBoolean("v")) {
                try { JSONObject cp = new JSONObject(o.toString()); cp.put("id", k.substring(5)); out.add(cp); } catch (JSONException ignored) { }
            }
        }
        Collections.sort(out, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) { return Long.compare(a.optLong("created"), b.optLong("created")); }
        });
        return out;
    }

    public static void putGoal(Context c, String id, String title, long target, String color, long created) {
        JSONObject o = new JSONObject();
        try { o.put("v", true).put("title", title).put("target", target).put("color", color).put("created", created); } catch (JSONException ignored) { }
        put(c, "goal|" + id, o);
    }

    public static void deleteGoal(Context c, String id) {
        JSONObject o = new JSONObject();
        try { o.put("v", false); } catch (JSONException ignored) { }
        put(c, "goal|" + id, o);
    }

    public static long goalSaved(Context c, String id) {
        long s = 0;
        for (JSONObject t : txs(c)) if ("save".equals(t.optString("type")) && id.equals(t.optString("goal"))) s += t.optLong("amount");
        return s;
    }

    public static long totalSaved(Context c) {
        long s = 0;
        for (JSONObject t : txs(c)) if ("save".equals(t.optString("type"))) s += t.optLong("amount");
        return s;
    }

    // ---------- período da mesada ----------

    /** {início, fim} do período atual (de um dia de mesada até a véspera do próximo). */
    public static String[] period(Context c, String today) {
        JSONObject cf = cfg(c);
        int pd = cf == null ? 1 : cf.optInt("payday", 1);
        if (pd < 1) pd = 1;
        Calendar t = Model.parse(today);
        Calendar s = (Calendar) t.clone();
        s.set(Calendar.DAY_OF_MONTH, Math.min(pd, s.getActualMaximum(Calendar.DAY_OF_MONTH)));
        if (s.after(t)) {
            s.add(Calendar.MONTH, -1);
            s.set(Calendar.DAY_OF_MONTH, Math.min(pd, s.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        Calendar e = (Calendar) s.clone();
        e.set(Calendar.DAY_OF_MONTH, 1);
        e.add(Calendar.MONTH, 1);
        e.set(Calendar.DAY_OF_MONTH, Math.min(pd, e.getActualMaximum(Calendar.DAY_OF_MONTH)));
        e.add(Calendar.DAY_OF_MONTH, -1);
        return new String[]{Model.fmt(s), Model.fmt(e)};
    }

    public static final class Summary {
        public long allowance, extras, spent, saved, saveTarget, spendable, left, perDay, weekLimit, weekSpent;
        public int daysLeft, periodDays;
        public String from, to;
        public long[] byCat = new long[OUT_CATS.length];
    }

    public static Summary summary(Context c) {
        String today = Model.today();
        String[] p = period(c, today);
        Summary s = new Summary();
        s.from = p[0]; s.to = p[1];
        JSONObject cf = cfg(c);
        s.allowance = cf == null ? 0 : cf.optLong("allowance");
        int pct = cf == null ? 0 : cf.optInt("savePct");
        String ws = Model.addDays(today, -Model.dow(today)), we = Model.addDays(ws, 6);
        for (JSONObject t : txs(c)) {
            String d = t.optString("date");
            if (d.compareTo(p[0]) < 0 || d.compareTo(p[1]) > 0) continue;
            long a = t.optLong("amount");
            String ty = t.optString("type");
            if ("in".equals(ty)) s.extras += a;
            else if ("save".equals(ty)) s.saved += a;
            else {
                s.spent += a;
                int ci = indexOf(OUT_CATS, t.optString("cat"));
                s.byCat[ci < 0 ? OUT_CATS.length - 1 : ci] += a;
                if (d.compareTo(ws) >= 0 && d.compareTo(we) <= 0) s.weekSpent += a;
            }
        }
        s.saveTarget = s.allowance * pct / 100;
        s.spendable = s.allowance + s.extras - Math.max(s.saveTarget, s.saved);
        s.left = s.spendable - s.spent;
        s.periodDays = (int) Math.round((Model.toMillis(p[1]) - Model.toMillis(p[0])) / 86400000.0) + 1;
        s.daysLeft = (int) Math.round((Model.toMillis(p[1]) - Model.toMillis(today)) / 86400000.0) + 1;
        s.perDay = s.daysLeft > 0 ? Math.max(0, s.left) / s.daysLeft : 0;
        long base = s.allowance + s.extras - Math.max(s.saveTarget, s.saved);
        s.weekLimit = s.periodDays > 0 ? base * 7 / s.periodDays : 0;
        return s;
    }

    static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }

    public static String catName(String type, String cat) {
        if ("save".equals(type)) return "Guardado";
        if ("in".equals(type)) { int i = indexOf(IN_CATS, cat); return i < 0 ? "Entrada" : IN_NAMES[i]; }
        int i = indexOf(OUT_CATS, cat);
        return i < 0 ? "Outros" : OUT_NAMES[i];
    }

    // ---------- dinheiro em texto ----------

    public static String brl(long cents) {
        boolean neg = cents < 0;
        long v = Math.abs(cents);
        String inteiro = String.format(Locale.US, "%,d", v / 100).replace(',', '.');
        return (neg ? "−R$ " : "R$ ") + inteiro + "," + String.format(Locale.US, "%02d", v % 100);
    }

    /** "12,50", "12.5", "R$ 1.200" → centavos; -1 se inválido. */
    public static long parse(String s) {
        if (s == null) return -1;
        s = s.replace("R$", "").replace(" ", "").trim();
        if (s.isEmpty()) return -1;
        if (s.contains(",")) s = s.replace(".", "").replace(',', '.');
        else if (s.indexOf('.') != s.lastIndexOf('.') || (s.contains(".") && s.length() - s.lastIndexOf('.') == 4)) s = s.replace(".", "");
        try {
            double d = Double.parseDouble(s);
            if (d < 0 || d > 10000000) return -1;
            return Math.round(d * 100);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Fin() {}
}

package com.yago.habitos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Estado do app: hábitos, tarefas e marcações. O mesmo formato JSON é usado pelo app do PC
 * e pelo script de sincronização. Cada item tem "t" (última alteração, em ms); na mesclagem
 * vale o mais recente.
 */
public final class Model {

    public static final String START = "2026-11-06";
    public static final String EXAM = "2027-01-24";

    private static JSONObject state;

    // ---------------- carga / gravação ----------------

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("habitos", Context.MODE_PRIVATE);
    }

    public static synchronized JSONObject get(Context c) {
        if (state != null) return state;
        String raw = prefs(c).getString("state", null);
        try {
            state = raw != null ? new JSONObject(raw) : new JSONObject();
        } catch (JSONException e) {
            state = new JSONObject();
        }
        ensureShape(state);
        if (raw == null) {
            seed(state);
            migrateOld(c, state);
            save(c);
        }
        return state;
    }

    public static synchronized void save(Context c) {
        if (state == null) return;
        prefs(c).edit().putString("state", state.toString()).apply();
    }

    /** Força recarregar do disco (outro processo/receiver pode ter gravado). */
    public static synchronized void reload(Context c) {
        state = null;
        get(c);
    }

    static void ensureShape(JSONObject s) {
        try {
            if (!s.has("habits")) s.put("habits", new JSONObject());
            if (!s.has("tasks")) s.put("tasks", new JSONObject());
            if (!s.has("marks")) s.put("marks", new JSONObject());
            s.put("v", 2);
        } catch (JSONException ignored) {
        }
    }

    // ---------------- dados iniciais ----------------

    static JSONObject habit(String id, String title, int order, int[] days, String time, int lead, String note) {
        JSONObject h = new JSONObject();
        try {
            h.put("id", id).put("title", title).put("order", order).put("time", time)
                    .put("lead", lead).put("note", note).put("remind", true).put("t", 0);
            JSONArray d = new JSONArray();
            for (int x : days) d.put(x);
            h.put("days", d);
        } catch (JSONException ignored) {
        }
        return h;
    }

    static JSONObject task(String id, String title, String date, String time, String note) {
        JSONObject t = new JSONObject();
        try {
            t.put("id", id).put("title", title).put("date", date).put("time", time).put("note", note)
                    .put("remind", true).put("done", false).put("t", 0);
        } catch (JSONException ignored) {
        }
        return t;
    }

    static void seed(JSONObject s) {
        int[] all = {0, 1, 2, 3, 4, 5, 6};
        try {
            JSONObject hs = s.getJSONObject("habits");
            JSONObject h;
            hs.put("acordar", habit("acordar", "Acordar às 06:00", 0, all, "06:00", 0, "Sem soneca · luz natural e água"));
            h = habit("devocional", "Devocional", 1, all, "06:10", 0, "");
            h.put("notes", new JSONObject().put("0", "Mais longo no fim de semana").put("6", "Mais longo no fim de semana"));
            hs.put("devocional", h);
            h = habit("concurso", "Estudar pro concurso", 2, new int[]{1, 2, 3, 4, 5, 6}, "19:00", 0, "");
            h.put("times", new JSONObject().put("6", "15:00"));
            h.put("notes", new JSONObject().put("1", "Português").put("2", "Específicos: {esp}")
                    .put("3", "Legislação Municipal").put("4", "Específicos: {esp}")
                    .put("5", "Raciocínio Lógico + questões").put("6", "Simulado e revisão"));
            hs.put("concurso", h);
            hs.put("escola", habit("escola", "Tarefas da escola", 3, new int[]{1, 2, 3, 4, 5}, "20:30", 0, ""));
            hs.put("revisao", habit("revisao", "Revisar a semana", 4, new int[]{0}, "16:30", 0, "Erros do simulado e 3 prioridades"));
            h = habit("igreja", "Igreja", 5, new int[]{6, 0}, "", 60, "");
            h.put("times", new JSONObject().put("6", "20:00"));
            h.put("notes", new JSONObject().put("6", "Arrumar às 19:00").put("0", "Manhã (09:30) ou noite (19:30)"));
            hs.put("igreja", h);
            hs.put("telas", habit("telas", "Banho e telas fora", 6, all, "21:45", 0, "Preparar o dia seguinte"));
            hs.put("leitura", habit("leitura", "Leitura", 7, all, "22:00", 0, "Livro físico"));
            hs.put("dormir", habit("dormir", "Dormir no horário", 8, all, "22:30", 30, ""));

            JSONObject ts = s.getJSONObject("tasks");
            ts.put("c_inscricao", task("c_inscricao", "Fazer inscrição no concurso", "2026-11-06", "18:30", "concursos.furb.br · Assistente Administrativo"));
            ts.put("c_isencao", task("c_isencao", "Pedir isenção da taxa (último dia)", "2026-11-10", "12:00", "Se for pedir"));
            ts.put("c_taxa", task("c_taxa", "Pagar a taxa do concurso (R$ 120)", "2026-12-08", "12:00", "Boleto ou Pix gerado no site da FURB"));
            ts.put("c_deferida", task("c_deferida", "Conferir se a inscrição foi deferida", "2026-12-10", "18:30", ""));
            ts.put("c_local", task("c_local", "Ver local e sala da prova", "2027-01-13", "18:30", "concursos.furb.br"));
            ts.put("c_prova", task("c_prova", "Prova do concurso às 15h15", "2027-01-24", "12:00", "Chegar até 14h30 · documento com foto e caneta"));
        } catch (JSONException ignored) {
        }
    }

    /** Traz as marcações da versão 1 do app (ids antigos). */
    static void migrateOld(Context c, JSONObject s) {
        SharedPreferences p = prefs(c);
        try {
            JSONObject marks = s.getJSONObject("marks");
            long now = System.currentTimeMillis();
            for (String k : p.getAll().keySet()) {
                if (!k.startsWith("d_")) continue;
                String date = k.substring(2);
                String v = p.getString(k, "");
                for (String id : v.split(",")) {
                    if (id.isEmpty()) continue;
                    marks.put(date + "|" + id, new JSONObject().put("v", true).put("t", now));
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ---------------- mesclagem ----------------

    public static synchronized void mergeIn(Context c, JSONObject other) {
        JSONObject s = get(c);
        for (String k : new String[]{"habits", "tasks", "marks"}) {
            JSONObject mine = s.optJSONObject(k);
            JSONObject theirs = other.optJSONObject(k);
            if (mine == null || theirs == null) continue;
            Iterator<String> it = theirs.keys();
            while (it.hasNext()) {
                String id = it.next();
                JSONObject t = theirs.optJSONObject(id);
                if (t == null) continue;
                JSONObject m = mine.optJSONObject(id);
                if (m == null || t.optLong("t") >= m.optLong("t")) {
                    try { mine.put(id, t); } catch (JSONException ignored) { }
                }
            }
        }
        save(c);
    }

    // ---------------- hábitos ----------------

    public static List<JSONObject> habits(Context c) {
        List<JSONObject> out = new ArrayList<>();
        JSONObject hs = get(c).optJSONObject("habits");
        Iterator<String> it = hs.keys();
        while (it.hasNext()) {
            JSONObject h = hs.optJSONObject(it.next());
            if (h != null && !h.optBoolean("deleted")) out.add(h);
        }
        Collections.sort(out, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) {
                int x = Double.compare(a.optDouble("order", 999), b.optDouble("order", 999));
                return x != 0 ? x : a.optString("title").compareTo(b.optString("title"));
            }
        });
        return out;
    }

    public static JSONObject habitById(Context c, String id) {
        JSONObject h = get(c).optJSONObject("habits").optJSONObject(id);
        return h == null || h.optBoolean("deleted") ? null : h;
    }

    public static synchronized void putHabit(Context c, JSONObject h) {
        try {
            h.put("t", System.currentTimeMillis());
            get(c).getJSONObject("habits").put(h.getString("id"), h);
        } catch (JSONException ignored) {
        }
        save(c);
    }

    public static double nextOrder(Context c) {
        double max = 0;
        for (JSONObject h : habits(c)) max = Math.max(max, h.optDouble("order", 0));
        return max + 1;
    }

    public static boolean appliesOn(JSONObject h, String date) {
        if (date.compareTo(START) < 0) return false;
        int w = dow(date);
        JSONArray d = h.optJSONArray("days");
        if (d == null) return false;
        for (int i = 0; i < d.length(); i++) if (d.optInt(i, -1) == w) return true;
        return false;
    }

    /** Horário do hábito nesse dia, em minutos (pode passar de 1440), ou -1. */
    public static int timeOf(JSONObject h, String date) {
        if ("dormir".equals(h.optString("id"))) {
            if (date.compareTo("2026-11-13") <= 0) return 24 * 60;
            if (date.compareTo("2026-11-20") <= 0) return 23 * 60 + 30;
            if (date.compareTo("2026-11-27") <= 0) return 23 * 60;
        }
        String t = h.optString("time", "");
        JSONObject times = h.optJSONObject("times");
        if (times != null && times.has(String.valueOf(dow(date)))) t = times.optString(String.valueOf(dow(date)));
        return parseTime(t);
    }

    public static String noteOf(JSONObject h, String date) {
        String n = h.optString("note", "");
        JSONObject notes = h.optJSONObject("notes");
        if (notes != null && notes.has(String.valueOf(dow(date)))) n = notes.optString(String.valueOf(dow(date)));
        if (n.contains("{esp}")) n = n.replace("{esp}", espTopic(date));
        return n;
    }

    public static String habitSubtitle(JSONObject h, String date) {
        int t = timeOf(h, date);
        String note = noteOf(h, date);
        if ("dormir".equals(h.optString("id")) && t >= 0) return "Meta: até " + hhmm(t);
        if (t >= 0 && !note.isEmpty()) return hhmm(t) + " · " + note;
        if (t >= 0) return hhmm(t);
        return note;
    }

    // ---------------- tarefas ----------------

    public static List<JSONObject> tasks(Context c) {
        List<JSONObject> out = new ArrayList<>();
        JSONObject ts = get(c).optJSONObject("tasks");
        Iterator<String> it = ts.keys();
        while (it.hasNext()) {
            JSONObject t = ts.optJSONObject(it.next());
            if (t != null && !t.optBoolean("deleted")) out.add(t);
        }
        Collections.sort(out, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) {
                String da = a.optString("date", ""), db = b.optString("date", "");
                if (da.isEmpty() != db.isEmpty()) return da.isEmpty() ? 1 : -1;
                int x = (da + a.optString("time", "")).compareTo(db + b.optString("time", ""));
                return x != 0 ? x : a.optString("title").compareTo(b.optString("title"));
            }
        });
        return out;
    }

    public static JSONObject taskById(Context c, String id) {
        JSONObject t = get(c).optJSONObject("tasks").optJSONObject(id);
        return t == null || t.optBoolean("deleted") ? null : t;
    }

    public static synchronized void putTask(Context c, JSONObject t) {
        try {
            t.put("t", System.currentTimeMillis());
            get(c).getJSONObject("tasks").put(t.getString("id"), t);
        } catch (JSONException ignored) {
        }
        save(c);
    }

    public static String taskSubtitle(JSONObject t, String viewDate) {
        String d = t.optString("date", "");
        String tm = t.optString("time", "");
        String note = t.optString("note", "");
        StringBuilder sb = new StringBuilder();
        if (!d.isEmpty() && !d.equals(viewDate)) {
            if (d.compareTo(today()) < 0 && !t.optBoolean("done")) sb.append("Atrasada · ");
            sb.append(shortDate(d));
            if (!tm.isEmpty()) sb.append(" ").append(tm);
        } else if (!tm.isEmpty()) {
            sb.append(tm);
        }
        if (!note.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(note);
        }
        return sb.toString();
    }

    // ---------------- marcações ----------------

    public static boolean isMarked(Context c, String date, String id) {
        JSONObject m = get(c).optJSONObject("marks").optJSONObject(date + "|" + id);
        return m != null && m.optBoolean("v");
    }

    public static synchronized void setMark(Context c, String date, String id, boolean v) {
        try {
            get(c).getJSONObject("marks").put(date + "|" + id,
                    new JSONObject().put("v", v).put("t", System.currentTimeMillis()));
        } catch (JSONException ignored) {
        }
        save(c);
    }

    // ---------------- lista do dia ----------------

    public static final class Item {
        public String kind; // "h" ou "t"
        public String id, title, sub;
        public int minutes = -1;
        public boolean done, overdue;
        public int streak;
        public String color = "";
    }

    /** Itens do dia, já ordenados: hábitos e tarefas com horário por hora, sem horário no fim. */
    public static List<Item> itemsFor(Context c, String date) {
        List<Item> out = new ArrayList<>();
        String today = today();
        for (JSONObject h : habits(c)) {
            if (!appliesOn(h, date)) continue;
            Item it = new Item();
            it.kind = "h";
            it.id = h.optString("id");
            it.title = h.optString("title");
            it.sub = habitSubtitle(h, date);
            it.minutes = timeOf(h, date);
            it.done = isMarked(c, date, it.id);
            if (date.equals(today)) it.streak = streak(c, h);
            it.color = colorKey(h);
            out.add(it);
        }
        for (JSONObject t : tasks(c)) {
            String d = t.optString("date", "");
            boolean done = t.optBoolean("done");
            String doneOn = t.optString("doneOn", "");
            boolean show;
            if (date.equals(today)) {
                show = done ? doneOn.equals(today) : (d.isEmpty() || d.compareTo(today) <= 0);
            } else {
                show = d.equals(date) || (done && doneOn.equals(date));
            }
            if (!show) continue;
            Item it = new Item();
            it.kind = "t";
            it.id = t.optString("id");
            it.title = t.optString("title");
            it.sub = taskSubtitle(t, date);
            it.minutes = d.equals(date) ? parseTime(t.optString("time", "")) : -1;
            it.done = done;
            it.overdue = !done && !d.isEmpty() && d.compareTo(today) < 0;
            out.add(it);
        }
        Collections.sort(out, new Comparator<Item>() {
            public int compare(Item a, Item b) {
                int ma = a.minutes < 0 ? 100000 : a.minutes, mb = b.minutes < 0 ? 100000 : b.minutes;
                if (a.overdue != b.overdue) return a.overdue ? -1 : 1;
                return ma - mb;
            }
        });
        return out;
    }

    public static void toggle(Context c, String date, String kind, String id) {
        if ("t".equals(kind)) {
            JSONObject t = taskById(c, id);
            if (t == null) return;
            try {
                boolean d = !t.optBoolean("done");
                t.put("done", d);
                t.put("doneOn", d ? today() : "");
            } catch (JSONException ignored) {
            }
            putTask(c, t);
        } else {
            setMark(c, date, id, !isMarked(c, date, id));
        }
    }

    public static int[] progress(Context c, String date) {
        int tot = 0, done = 0;
        for (Item it : itemsFor(c, date)) {
            tot++;
            if (it.done) done++;
        }
        return new int[]{done, tot};
    }

    public static int streak(Context c, JSONObject h) {
        String id = h.optString("id");
        String d = today();
        if (appliesOn(h, d) && !isMarked(c, d, id)) d = addDays(d, -1);
        int n = 0, guard = 0;
        while (d.compareTo(START) >= 0 && guard++ < 500) {
            if (appliesOn(h, d)) {
                if (isMarked(c, d, id)) n++; else break;
            }
            d = addDays(d, -1);
        }
        return n;
    }


    // ---------------- cores ----------------

    public static final String[] COLOR_KEYS = {"verde", "azul", "roxo", "rosa", "laranja", "amarelo", "ciano"};
    public static final String[] COLOR_NAMES = {"Verde", "Azul", "Roxo", "Rosa", "Laranja", "Amarelo", "Ciano"};

    public static int color(String key, boolean dark) {
        switch (key) {
            case "azul": return dark ? 0xFF6EA8E8 : 0xFF3F7CC4;
            case "roxo": return dark ? 0xFFA48BE6 : 0xFF7556C4;
            case "rosa": return dark ? 0xFFE88AA6 : 0xFFC4517A;
            case "laranja": return dark ? 0xFFF0A866 : 0xFFD07A2A;
            case "amarelo": return dark ? 0xFFE3C55E : 0xFFB08A16;
            case "ciano": return dark ? 0xFF62C8D4 : 0xFF2394A3;
            default: return dark ? 0xFF7FD1AE : 0xFF3E8E7E;
        }
    }

    public static String colorKey(JSONObject h) {
        String k = h.optString("color", "");
        if (!k.isEmpty()) return k;
        switch (h.optString("id")) {
            case "acordar": return "laranja";
            case "devocional": return "roxo";
            case "concurso": return "azul";
            case "escola": return "ciano";
            case "revisao": return "azul";
            case "igreja": return "roxo";
            case "telas": return "verde";
            case "leitura": return "amarelo";
            case "dormir": return "rosa";
        }
        return "verde";
    }

    // ---------------- resumos ----------------

    /** Próxima tarefa pendente com data (hoje ou depois), ou null. */
    public static JSONObject nextTask(Context c) {
        String today = today();
        int nowMin = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE);
        for (JSONObject t : tasks(c)) {
            if (t.optBoolean("done")) continue;
            String d = t.optString("date", "");
            if (d.isEmpty() || d.compareTo(today) < 0) continue;
            if (d.equals(today)) {
                int m = parseTime(t.optString("time", ""));
                if (m >= 0 && m < nowMin) continue;
            }
            return t;
        }
        return null;
    }

    public static String relDay(String d) {
        String today = today();
        if (d.equals(today)) return "hoje";
        if (d.equals(addDays(today, 1))) return "amanhã";
        long n = Math.round((toMillis(d) - toMillis(today)) / 86400000.0);
        if (n < 7) return DOW[dow(d)].toLowerCase();
        return "em " + n + " dias";
    }

    /** Dias seguidos com tudo feito (contando hoje se já estiver completo). */
    public static int perfectStreak(Context c) {
        String d = today();
        int[] p = progress(c, d);
        if (p[1] == 0 || p[0] < p[1]) d = addDays(d, -1);
        int n = 0, guard = 0;
        while (d.compareTo(START) >= 0 && guard++ < 400) {
            p = progress(c, d);
            if (p[1] > 0 && p[0] == p[1]) n++; else break;
            d = addDays(d, -1);
        }
        return n;
    }

    public static int bestHabitStreak(Context c) {
        int best = 0;
        for (JSONObject h : habits(c)) best = Math.max(best, streak(c, h));
        return best;
    }

    public static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 5) return "Boa madrugada";
        if (h < 12) return "Bom dia";
        if (h < 18) return "Boa tarde";
        return "Boa noite";
    }

    // ---------------- concurso ----------------

    static final String[] ESP = {
            "Administração Pública e Direito Administrativo",
            "Administração Pública e Direito Administrativo",
            "Redação oficial, protocolo, arquivo e documentos",
            "Licitações, contratos, almoxarifado e patrimônio",
            "Orçamento público, empenho, liquidação e pagamento",
            "Gestão de pessoas, folha e atendimento ao público",
            "Informática, segurança da informação e LGPD",
    };

    public static String espTopic(String date) {
        long w = (toMillis(date) - toMillis("2026-11-09")) / (7L * 86400000L);
        if (w < 0) w = 0;
        return w >= ESP.length ? "Revisão geral e questões" : ESP[(int) w];
    }

    public static int daysUntilExam() {
        return (int) Math.round((toMillis(EXAM) - toMillis(today())) / 86400000.0);
    }

    // ---------------- datas ----------------

    public static int parseTime(String t) {
        if (t == null || !t.matches("\\d{1,2}:\\d{2}")) return -1;
        String[] p = t.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    static String pad(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    public static String hhmm(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return pad(m / 60) + ":" + pad(m % 60);
    }

    public static String fmt(Calendar c) {
        return c.get(Calendar.YEAR) + "-" + pad(c.get(Calendar.MONTH) + 1) + "-" + pad(c.get(Calendar.DAY_OF_MONTH));
    }

    public static Calendar parse(String s) {
        String[] p = s.split("-");
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 12, 0, 0);
        return c;
    }

    static long toMillis(String s) { return parse(s).getTimeInMillis(); }

    public static String today() { return fmt(Calendar.getInstance()); }

    public static String addDays(String s, int n) {
        Calendar c = parse(s);
        c.add(Calendar.DAY_OF_MONTH, n);
        return fmt(c);
    }

    /** 0 = domingo ... 6 = sábado */
    public static int dow(String s) { return parse(s).get(Calendar.DAY_OF_WEEK) - 1; }

    public static long atMinutes(String date, int minutes) {
        Calendar c = parse(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.add(Calendar.MINUTE, minutes);
        return c.getTimeInMillis();
    }

    static final String[] MONTHS = {"janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho",
            "agosto", "setembro", "outubro", "novembro", "dezembro"};
    static final String[] MONTHS_SHORT = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};
    public static final String[] DOW = {"Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"};
    public static final String[] DOW1 = {"D", "S", "T", "Q", "Q", "S", "S"};

    public static String longDate(String s) {
        Calendar c = parse(s);
        return DOW[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + " de " + MONTHS[c.get(Calendar.MONTH)];
    }

    public static String shortDate(String s) {
        Calendar c = parse(s);
        return c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS_SHORT[c.get(Calendar.MONTH)];
    }

    public static String newId(String prefix) {
        return prefix + Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
    }

    private Model() {}
}

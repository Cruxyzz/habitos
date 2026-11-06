using System;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Net;
using System.Text;
using System.Web.Script.Serialization;

namespace Habitos
{
    // Mesmo formato JSON do app do celular e do script de sincronização.
    // state = { v, habits:{id:{...,t}}, tasks:{id:{...,t}}, marks:{"data|id":{v,t}} }
    static class Model
    {
        public static readonly DateTime Start = new DateTime(2026, 11, 6);
        public static readonly DateTime Exam = new DateTime(2027, 1, 24);
        static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
        public static readonly string Dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Habitos");
        static readonly string StatePath = Path.Combine(Dir, "estado.json");
        static readonly object Lock = new object();
        static Dictionary<string, object> state;

        public static event Action Changed;

        // ---------- carga / gravação ----------

        static Dictionary<string, object> S
        {
            get
            {
                lock (Lock)
                {
                    if (state != null) return state;
                    try { if (File.Exists(StatePath)) state = Json.DeserializeObject(File.ReadAllText(StatePath, Encoding.UTF8)) as Dictionary<string, object>; }
                    catch { state = null; }
                    if (state == null)
                    {
                        state = new Dictionary<string, object>();
                        Shape(state);
                        Seed(state);
                        MigrateOld(state);
                        Save();
                    }
                    Shape(state);
                    return state;
                }
            }
        }

        static void Shape(Dictionary<string, object> s)
        {
            foreach (var k in new[] { "habits", "tasks", "marks" })
                if (!(s.ContainsKey(k) && s[k] is Dictionary<string, object>)) s[k] = new Dictionary<string, object>();
            s["v"] = 2;
        }

        public static void Save()
        {
            lock (Lock)
            {
                try
                {
                    Directory.CreateDirectory(Dir);
                    var tmp = StatePath + ".tmp";
                    File.WriteAllText(tmp, Json.Serialize(state), Encoding.UTF8);
                    if (File.Exists(StatePath)) File.Delete(StatePath);
                    File.Move(tmp, StatePath);
                }
                catch { }
            }
        }

        static void Notify() { var h = Changed; if (h != null) h(); }

        static Dictionary<string, object> Coll(string k) { return (Dictionary<string, object>)S[k]; }

        public static string ToJson() { lock (Lock) return Json.Serialize(S); }

        // ---------- dados iniciais ----------

        static Dictionary<string, object> H(string id, string title, int order, int[] days, string time, int lead, string note)
        {
            return new Dictionary<string, object> {
                {"id", id}, {"title", title}, {"order", order}, {"days", days.Cast<object>().ToArray()}, {"time", time},
                {"lead", lead}, {"note", note}, {"remind", true}, {"t", 0L}
            };
        }

        static Dictionary<string, object> T(string id, string title, string date, string time, string note)
        {
            return new Dictionary<string, object> {
                {"id", id}, {"title", title}, {"date", date}, {"time", time}, {"note", note}, {"remind", true}, {"done", false}, {"t", 0L}
            };
        }

        static void Seed(Dictionary<string, object> s)
        {
            var all = new[] { 0, 1, 2, 3, 4, 5, 6 };
            var hs = (Dictionary<string, object>)s["habits"];
            hs["acordar"] = H("acordar", "Acordar às 06:00", 0, all, "06:00", 0, "Sem soneca · luz natural e água");
            var dev = H("devocional", "Devocional", 1, all, "06:10", 0, "");
            dev["notes"] = new Dictionary<string, object> { { "0", "Mais longo no fim de semana" }, { "6", "Mais longo no fim de semana" } };
            hs["devocional"] = dev;
            var con = H("concurso", "Estudar pro concurso", 2, new[] { 1, 2, 3, 4, 5, 6 }, "19:00", 0, "");
            con["times"] = new Dictionary<string, object> { { "6", "15:00" } };
            con["notes"] = new Dictionary<string, object> {
                { "1", "Português" }, { "2", "Específicos: {esp}" }, { "3", "Legislação Municipal" },
                { "4", "Específicos: {esp}" }, { "5", "Raciocínio Lógico + questões" }, { "6", "Simulado e revisão" } };
            hs["concurso"] = con;
            hs["escola"] = H("escola", "Tarefas da escola", 3, new[] { 1, 2, 3, 4, 5 }, "20:30", 0, "");
            hs["revisao"] = H("revisao", "Revisar a semana", 4, new[] { 0 }, "16:30", 0, "Erros do simulado e 3 prioridades");
            var ig = H("igreja", "Igreja", 5, new[] { 6, 0 }, "", 60, "");
            ig["times"] = new Dictionary<string, object> { { "6", "20:00" } };
            ig["notes"] = new Dictionary<string, object> { { "6", "Arrumar às 19:00" }, { "0", "Manhã (09:30) ou noite (19:30)" } };
            hs["igreja"] = ig;
            hs["telas"] = H("telas", "Banho e telas fora", 6, all, "21:45", 0, "Preparar o dia seguinte");
            hs["leitura"] = H("leitura", "Leitura", 7, all, "22:00", 0, "Livro físico");
            hs["dormir"] = H("dormir", "Dormir no horário", 8, all, "22:30", 30, "");

            var ts = (Dictionary<string, object>)s["tasks"];
            ts["c_inscricao"] = T("c_inscricao", "Fazer inscrição no concurso", "2026-11-06", "18:30", "concursos.furb.br · Assistente Administrativo");
            ts["c_isencao"] = T("c_isencao", "Pedir isenção da taxa (último dia)", "2026-11-10", "12:00", "Se for pedir");
            ts["c_taxa"] = T("c_taxa", "Pagar a taxa do concurso (R$ 120)", "2026-12-08", "12:00", "Boleto ou Pix gerado no site da FURB");
            ts["c_deferida"] = T("c_deferida", "Conferir se a inscrição foi deferida", "2026-12-10", "18:30", "");
            ts["c_local"] = T("c_local", "Ver local e sala da prova", "2027-01-13", "18:30", "concursos.furb.br");
            ts["c_prova"] = T("c_prova", "Prova do concurso às 15h15", "2027-01-24", "12:00", "Chegar até 14h30 · documento com foto e caneta");
        }

        static void MigrateOld(Dictionary<string, object> s)
        {
            try
            {
                var old = Path.Combine(Dir, "dados.txt");
                if (!File.Exists(old)) return;
                var marks = (Dictionary<string, object>)s["marks"];
                long now = Now();
                foreach (var line in File.ReadAllLines(old))
                {
                    int i = line.IndexOf('=');
                    if (i <= 0) continue;
                    foreach (var id in line.Substring(i + 1).Split(new[] { ',' }, StringSplitOptions.RemoveEmptyEntries))
                        marks[line.Substring(0, i) + "|" + id] = new Dictionary<string, object> { { "v", true }, { "t", now } };
                }
            }
            catch { }
        }

        // ---------- mesclagem ----------

        public static void MergeIn(Dictionary<string, object> other)
        {
            lock (Lock)
            {
                var s = S;
                foreach (var k in new[] { "habits", "tasks", "marks" })
                {
                    var mine = (Dictionary<string, object>)s[k];
                    var theirs = other.ContainsKey(k) ? other[k] as Dictionary<string, object> : null;
                    if (theirs == null) continue;
                    foreach (var kv in theirs)
                    {
                        var t = kv.Value as Dictionary<string, object>;
                        if (t == null) continue;
                        object m;
                        if (!mine.TryGetValue(kv.Key, out m) || L(t, "t") >= L((Dictionary<string, object>)m, "t")) mine[kv.Key] = t;
                    }
                }
                Save();
            }
            Notify();
        }

        // ---------- leitura de campos ----------

        public static string Str(Dictionary<string, object> o, string k) { object v; return o.TryGetValue(k, out v) && v != null ? Convert.ToString(v, CultureInfo.InvariantCulture) : ""; }
        public static bool Bool(Dictionary<string, object> o, string k, bool def) { object v; return o.TryGetValue(k, out v) && v is bool ? (bool)v : def; }
        public static long L(Dictionary<string, object> o, string k) { object v; try { return o.TryGetValue(k, out v) && v != null ? Convert.ToInt64(v, CultureInfo.InvariantCulture) : 0; } catch { return 0; } }
        public static double D(Dictionary<string, object> o, string k, double def) { object v; try { return o.TryGetValue(k, out v) && v != null ? Convert.ToDouble(v, CultureInfo.InvariantCulture) : def; } catch { return def; } }
        public static Dictionary<string, object> Obj(Dictionary<string, object> o, string k) { object v; return o.TryGetValue(k, out v) ? v as Dictionary<string, object> : null; }
        public static int[] Days(Dictionary<string, object> o)
        {
            object v;
            if (!o.TryGetValue("days", out v) || v == null) return new int[0];
            var e = v as IEnumerable;
            if (e == null) return new int[0];
            var l = new List<int>();
            foreach (var x in e) { try { l.Add(Convert.ToInt32(x, CultureInfo.InvariantCulture)); } catch { } }
            return l.ToArray();
        }

        public static long Now() { return (long)(DateTime.UtcNow - new DateTime(1970, 1, 1)).TotalMilliseconds; }

        // ---------- hábitos ----------

        public static List<Dictionary<string, object>> Habits()
        {
            lock (Lock)
                return Coll("habits").Values.OfType<Dictionary<string, object>>().Where(h => !Bool(h, "deleted", false))
                    .OrderBy(h => D(h, "order", 999)).ThenBy(h => Str(h, "title")).ToList();
        }

        public static Dictionary<string, object> Habit(string id)
        {
            lock (Lock) { object h; return Coll("habits").TryGetValue(id, out h) && !Bool((Dictionary<string, object>)h, "deleted", false) ? (Dictionary<string, object>)h : null; }
        }

        public static void PutHabit(Dictionary<string, object> h)
        {
            lock (Lock) { h["t"] = Now(); Coll("habits")[Str(h, "id")] = h; Save(); }
            Notify();
        }

        public static double NextOrder() { var l = Habits(); return l.Count == 0 ? 0 : l.Max(h => D(h, "order", 0)) + 1; }

        public static bool AppliesOn(Dictionary<string, object> h, DateTime d)
        {
            return d >= Start && Days(h).Contains((int)d.DayOfWeek);
        }

        static string Dow(DateTime d) { return ((int)d.DayOfWeek).ToString(); }

        public static int TimeOf(Dictionary<string, object> h, DateTime d)
        {
            if (Str(h, "id") == "dormir")
            {
                if (d <= new DateTime(2026, 11, 13)) return 24 * 60;
                if (d <= new DateTime(2026, 11, 20)) return 23 * 60 + 30;
                if (d <= new DateTime(2026, 11, 27)) return 23 * 60;
            }
            var t = Str(h, "time");
            var times = Obj(h, "times");
            if (times != null && times.ContainsKey(Dow(d))) t = Str(times, Dow(d));
            return ParseTime(t);
        }

        public static string NoteOf(Dictionary<string, object> h, DateTime d)
        {
            var n = Str(h, "note");
            var notes = Obj(h, "notes");
            if (notes != null && notes.ContainsKey(Dow(d))) n = Str(notes, Dow(d));
            return n.Replace("{esp}", EspTopic(d));
        }

        public static string HabitSub(Dictionary<string, object> h, DateTime d)
        {
            int t = TimeOf(h, d);
            var note = NoteOf(h, d);
            if (Str(h, "id") == "dormir" && t >= 0) return "Meta: até " + HHMM(t);
            if (t >= 0 && note.Length > 0) return HHMM(t) + " · " + note;
            if (t >= 0) return HHMM(t);
            return note;
        }

        // ---------- tarefas ----------

        public static List<Dictionary<string, object>> Tasks()
        {
            lock (Lock)
                return Coll("tasks").Values.OfType<Dictionary<string, object>>().Where(t => !Bool(t, "deleted", false))
                    .OrderBy(t => Str(t, "date") == "" ? 1 : 0).ThenBy(t => Str(t, "date") + Str(t, "time")).ThenBy(t => Str(t, "title")).ToList();
        }

        public static Dictionary<string, object> Task(string id)
        {
            lock (Lock) { object t; return Coll("tasks").TryGetValue(id, out t) && !Bool((Dictionary<string, object>)t, "deleted", false) ? (Dictionary<string, object>)t : null; }
        }

        public static void PutTask(Dictionary<string, object> t)
        {
            lock (Lock) { t["t"] = Now(); Coll("tasks")[Str(t, "id")] = t; Save(); }
            Notify();
        }

        public static string TaskSub(Dictionary<string, object> t, DateTime view)
        {
            var d = Str(t, "date"); var tm = Str(t, "time"); var note = Str(t, "note");
            var sb = new StringBuilder();
            if (d != "" && d != Key(view))
            {
                if (string.Compare(d, Key(DateTime.Today), StringComparison.Ordinal) < 0 && !Bool(t, "done", false)) sb.Append("Atrasada · ");
                sb.Append(ShortDate(ParseDate(d)));
                if (tm != "") sb.Append(" ").Append(tm);
            }
            else if (tm != "") sb.Append(tm);
            if (note != "") { if (sb.Length > 0) sb.Append(" · "); sb.Append(note); }
            return sb.ToString();
        }

        // ---------- marcações ----------

        public static bool Marked(DateTime d, string id)
        {
            lock (Lock) { object m; return Coll("marks").TryGetValue(Key(d) + "|" + id, out m) && Bool((Dictionary<string, object>)m, "v", false); }
        }

        public static void SetMark(DateTime d, string id, bool v)
        {
            lock (Lock) { Coll("marks")[Key(d) + "|" + id] = new Dictionary<string, object> { { "v", v }, { "t", Now() } }; Save(); }
            Notify();
        }

        // ---------- lista do dia ----------

        public class Item
        {
            public string Kind, Id, Title, Sub;
            public int Minutes = -1;
            public bool Done, Overdue;
            public int Streak;
            public string Color = "";
        }

        public static List<Item> ItemsFor(DateTime date)
        {
            var today = DateTime.Today;
            var outl = new List<Item>();
            foreach (var h in Habits())
            {
                if (!AppliesOn(h, date)) continue;
                var id = Str(h, "id");
                outl.Add(new Item
                {
                    Kind = "h", Id = id, Title = Str(h, "title"), Sub = HabitSub(h, date), Minutes = TimeOf(h, date),
                    Done = Marked(date, id), Streak = date == today ? Streak(h) : 0, Color = ColorKey(h)
                });
            }
            var tk = Key(today); var dk = Key(date);
            foreach (var t in Tasks())
            {
                var d = Str(t, "date"); bool done = Bool(t, "done", false); var doneOn = Str(t, "doneOn");
                bool show = date == today
                    ? (done ? doneOn == tk : (d == "" || string.Compare(d, tk, StringComparison.Ordinal) <= 0))
                    : (d == dk || (done && doneOn == dk));
                if (!show) continue;
                outl.Add(new Item
                {
                    Kind = "t", Id = Str(t, "id"), Title = Str(t, "title"), Sub = TaskSub(t, date),
                    Minutes = d == dk ? ParseTime(Str(t, "time")) : -1, Done = done,
                    Overdue = !done && d != "" && string.Compare(d, tk, StringComparison.Ordinal) < 0
                });
            }
            return outl.OrderBy(i => i.Overdue ? 0 : 1).ThenBy(i => i.Minutes < 0 ? 100000 : i.Minutes).ToList();
        }

        public static void Toggle(DateTime date, string kind, string id)
        {
            if (kind == "t")
            {
                var t = Task(id);
                if (t == null) return;
                bool d = !Bool(t, "done", false);
                t["done"] = d;
                t["doneOn"] = d ? Key(DateTime.Today) : "";
                PutTask(t);
            }
            else SetMark(date, id, !Marked(date, id));
        }

        public static int[] Progress(DateTime d)
        {
            var l = ItemsFor(d);
            return new[] { l.Count(i => i.Done), l.Count };
        }

        public static int Streak(Dictionary<string, object> h)
        {
            var id = Str(h, "id");
            var d = DateTime.Today;
            if (AppliesOn(h, d) && !Marked(d, id)) d = d.AddDays(-1);
            int n = 0;
            while (d >= Start)
            {
                if (AppliesOn(h, d)) { if (Marked(d, id)) n++; else break; }
                d = d.AddDays(-1);
            }
            return n;
        }


        // ---------- cores e resumos ----------

        public static readonly string[] ColorKeys = { "verde", "azul", "roxo", "rosa", "laranja", "amarelo", "ciano" };

        public static System.Drawing.Color ColorOf(string key)
        {
            switch (key)
            {
                case "azul": return System.Drawing.Color.FromArgb(0x6E, 0xA8, 0xE8);
                case "roxo": return System.Drawing.Color.FromArgb(0xA4, 0x8B, 0xE6);
                case "rosa": return System.Drawing.Color.FromArgb(0xE8, 0x8A, 0xA6);
                case "laranja": return System.Drawing.Color.FromArgb(0xF0, 0xA8, 0x66);
                case "amarelo": return System.Drawing.Color.FromArgb(0xE3, 0xC5, 0x5E);
                case "ciano": return System.Drawing.Color.FromArgb(0x62, 0xC8, 0xD4);
                default: return System.Drawing.Color.FromArgb(0x7F, 0xD1, 0xAE);
            }
        }

        public static string ColorKey(Dictionary<string, object> h)
        {
            var k = Str(h, "color");
            if (k != "") return k;
            switch (Str(h, "id"))
            {
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

        public static Dictionary<string, object> NextTask()
        {
            var tk = Key(DateTime.Today);
            int nowMin = DateTime.Now.Hour * 60 + DateTime.Now.Minute;
            foreach (var t in Tasks())
            {
                if (Bool(t, "done", false)) continue;
                var d = Str(t, "date");
                if (d == "" || string.Compare(d, tk, StringComparison.Ordinal) < 0) continue;
                if (d == tk) { int m = ParseTime(Str(t, "time")); if (m >= 0 && m < nowMin) continue; }
                return t;
            }
            return null;
        }

        public static string RelDay(string d)
        {
            var dt = ParseDate(d); var today = DateTime.Today;
            if (dt == today) return "hoje";
            if (dt == today.AddDays(1)) return "amanhã";
            int n = (int)(dt - today).TotalDays;
            if (n < 7) return DowNames[(int)dt.DayOfWeek].ToLower();
            return "em " + n + " dias";
        }

        public static int PerfectStreak()
        {
            var d = DateTime.Today;
            var p = Progress(d);
            if (p[1] == 0 || p[0] < p[1]) d = d.AddDays(-1);
            int n = 0;
            while (d >= Start && n < 400)
            {
                p = Progress(d);
                if (p[1] > 0 && p[0] == p[1]) n++; else break;
                d = d.AddDays(-1);
            }
            return n;
        }

        public static string Greeting()
        {
            int h = DateTime.Now.Hour;
            return h < 5 ? "Boa madrugada" : h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite";
        }

        // ---------- concurso ----------

        static readonly string[] Esp = {
            "Administração Pública e Direito Administrativo", "Administração Pública e Direito Administrativo",
            "Redação oficial, protocolo, arquivo e documentos", "Licitações, contratos, almoxarifado e patrimônio",
            "Orçamento público, empenho, liquidação e pagamento", "Gestão de pessoas, folha e atendimento ao público",
            "Informática, segurança da informação e LGPD" };

        public static string EspTopic(DateTime d)
        {
            int w = (int)Math.Floor((d - new DateTime(2026, 11, 9)).TotalDays / 7);
            if (w < 0) w = 0;
            return w >= Esp.Length ? "Revisão geral e questões" : Esp[w];
        }

        // ---------- datas ----------

        public static string Key(DateTime d) { return d.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture); }
        public static DateTime ParseDate(string s) { return DateTime.ParseExact(s, "yyyy-MM-dd", CultureInfo.InvariantCulture); }
        public static int ParseTime(string t)
        {
            if (string.IsNullOrEmpty(t)) return -1;
            var p = t.Split(':');
            int h, m;
            if (p.Length != 2 || !int.TryParse(p[0], out h) || !int.TryParse(p[1], out m) || h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        }
        public static string HHMM(int m) { m = ((m % 1440) + 1440) % 1440; return (m / 60).ToString("00") + ":" + (m % 60).ToString("00"); }

        static readonly string[] Months = { "janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro" };
        static readonly string[] MonthsShort = { "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez" };
        public static readonly string[] DowNames = { "Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado" };
        public static readonly string[] Dow1 = { "D", "S", "T", "Q", "Q", "S", "S" };
        public static string LongDate(DateTime d) { return DowNames[(int)d.DayOfWeek] + ", " + d.Day + " de " + Months[d.Month - 1]; }
        public static string ShortDate(DateTime d) { return d.Day + " " + MonthsShort[d.Month - 1]; }

        public static string NewId(string p) { return p + Now().ToString("x") + new Random().Next(4096).ToString("x"); }
    }

    static class Sync
    {
        static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
        static bool running, again;
        public static string Status = "";
        public static event Action<bool, string> Finished;

        public static void Run()
        {
            var url = Settings.Get("syncurl", "").Trim();
            if (url == "") return;
            lock (typeof(Sync)) { if (running) { again = true; return; } running = true; }
            System.Threading.ThreadPool.QueueUserWorkItem(_ =>
            {
                bool ok = false; string msg;
                try
                {
                    var resp = Post(url, Model.ToJson());
                    var r = Json.DeserializeObject(resp) as Dictionary<string, object>;
                    if (r == null || !Model.Bool(r, "ok", false)) throw new Exception(r == null ? "resposta inválida" : Model.Str(r, "error"));
                    Model.MergeIn((Dictionary<string, object>)r["state"]);
                    ok = true;
                    msg = "Sincronizado às " + DateTime.Now.ToString("HH:mm");
                }
                catch (Exception e) { msg = "Falha ao sincronizar: " + e.Message; }
                Status = msg;
                bool rerun;
                lock (typeof(Sync)) { running = false; rerun = again; again = false; }
                var f = Finished; if (f != null) f(ok, msg);
                if (rerun) Run();
            });
        }

        static string Post(string url, string body)
        {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072; // TLS 1.2
            var req = (HttpWebRequest)WebRequest.Create(url);
            req.Method = "POST";
            req.AllowAutoRedirect = false;
            req.ContentType = "application/json; charset=utf-8";
            req.Timeout = 30000;
            var bytes = Encoding.UTF8.GetBytes(body);
            using (var s = req.GetRequestStream()) s.Write(bytes, 0, bytes.Length);
            var resp = (HttpWebResponse)req.GetResponse();
            int hops = 0;
            while ((int)resp.StatusCode >= 300 && (int)resp.StatusCode < 400 && hops++ < 5)
            {
                var loc = resp.Headers["Location"];
                resp.Close();
                var g = (HttpWebRequest)WebRequest.Create(loc);
                g.AllowAutoRedirect = false;
                g.Timeout = 30000;
                resp = (HttpWebResponse)g.GetResponse();
            }
            using (resp)
            using (var sr = new StreamReader(resp.GetResponseStream(), Encoding.UTF8))
            {
                var text = sr.ReadToEnd();
                if (!text.TrimStart().StartsWith("{")) throw new Exception("o link não é do script (confira se o acesso está como \"Qualquer pessoa\")");
                return text;
            }
        }
    }

    static class Settings
    {
        static readonly string FilePath = Path.Combine(Model.Dir, "config.txt");
        static Dictionary<string, string> d;
        static void Load()
        {
            if (d != null) return;
            d = new Dictionary<string, string>();
            try { if (File.Exists(FilePath)) foreach (var l in File.ReadAllLines(FilePath)) { int i = l.IndexOf('='); if (i > 0) d[l.Substring(0, i)] = l.Substring(i + 1); } } catch { }
        }
        public static string Get(string k, string def) { Load(); string v; return d.TryGetValue(k, out v) ? v : def; }
        public static void Set(string k, string v)
        {
            Load(); d[k] = v;
            try { Directory.CreateDirectory(Model.Dir); File.WriteAllLines(FilePath, d.Select(kv => kv.Key + "=" + kv.Value)); } catch { }
        }
    }
}

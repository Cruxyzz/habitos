using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;

namespace Habitos
{
    // Dinheiro. Fica dentro de "marks" (cfg|fin, tx|id, goal|id): sincroniza pelo mesmo script
    // e não aparece em versões antigas. Valores em centavos.
    static class Fin
    {
        public static readonly string[] OutCats = { "lanche", "transporte", "lazer", "jogos", "roupas", "escola", "assinaturas", "presentes", "outros" };
        public static readonly string[] OutNames = { "Lanche e comida", "Transporte", "Lazer e passeios", "Jogos", "Roupas", "Escola e estudo", "Assinaturas", "Presentes", "Outros" };
        public static readonly string[] OutShort = { "Lanche", "Transporte", "Lazer", "Jogos", "Roupas", "Escola", "Assinaturas", "Presentes", "Outros" };
        public static readonly string[] OutColors = { "laranja", "azul", "rosa", "roxo", "ciano", "amarelo", "verde", "rosa", "azul" };
        public static readonly string[] InCats = { "extra", "presente", "venda", "outros" };
        public static readonly string[] InNames = { "Dinheiro extra", "Presente", "Venda ou bico", "Outros" };
        public static readonly string[] InShort = { "Extra", "Presente", "Venda", "Outros" };

        public static Dictionary<string, object> Cfg()
        {
            var o = Model.MarkObj("cfg|fin");
            return o != null && Model.Bool(o, "v", false) ? o : null;
        }

        public static void SetCfg(long allowance, int payday, int savePct)
        {
            Model.PutMark("cfg|fin", new Dictionary<string, object> { { "v", true }, { "allowance", allowance }, { "payday", payday }, { "savePct", savePct } });
        }

        public static List<Dictionary<string, object>> Txs()
        {
            return Model.MarksWithPrefix("tx|").Where(kv => Model.Bool(kv.Value, "v", false))
                .Select(kv => { var c = new Dictionary<string, object>(kv.Value); c["key"] = kv.Key; return c; })
                .OrderByDescending(t => Model.Str(t, "date")).ThenByDescending(t => Model.L(t, "created")).ToList();
        }

        public static void AddTx(string type, long amount, string cat, string note, string date, string goal)
        {
            Model.PutMark("tx|" + Model.NewId("x"), new Dictionary<string, object> {
                { "v", true }, { "type", type }, { "amount", amount }, { "cat", cat }, { "note", note }, { "date", date },
                { "goal", goal ?? "" }, { "created", Model.Now() } });
        }

        public static void UpdateTx(string key, Dictionary<string, object> o)
        {
            var c = new Dictionary<string, object>(o);
            c.Remove("key");
            Model.PutMark(key, c);
        }

        public static void DeleteTx(string key) { Model.PutMark(key, new Dictionary<string, object> { { "v", false } }); }

        public static List<Dictionary<string, object>> Goals()
        {
            return Model.MarksWithPrefix("goal|").Where(kv => Model.Bool(kv.Value, "v", false))
                .Select(kv => { var c = new Dictionary<string, object>(kv.Value); c["id"] = kv.Key.Substring(5); return c; })
                .OrderBy(g => Model.L(g, "created")).ToList();
        }

        public static void PutGoal(string id, string title, long target, string color, long created)
        {
            Model.PutMark("goal|" + id, new Dictionary<string, object> { { "v", true }, { "title", title }, { "target", target }, { "color", color }, { "created", created } });
        }

        public static void DeleteGoal(string id) { Model.PutMark("goal|" + id, new Dictionary<string, object> { { "v", false } }); }

        public static long GoalSaved(string id) { return Txs().Where(t => Model.Str(t, "type") == "save" && Model.Str(t, "goal") == id).Sum(t => Model.L(t, "amount")); }
        public static long TotalSaved() { return Txs().Where(t => Model.Str(t, "type") == "save").Sum(t => Model.L(t, "amount")); }

        static DateTime Clamp(int y, int m, int d) { return new DateTime(y, m, Math.Min(d, DateTime.DaysInMonth(y, m))); }

        public static DateTime[] Period(DateTime today)
        {
            var cf = Cfg();
            int pd = cf == null ? 1 : (int)Model.L(cf, "payday");
            if (pd < 1) pd = 1;
            var s = Clamp(today.Year, today.Month, pd);
            if (s > today) { var pm = today.AddMonths(-1); s = Clamp(pm.Year, pm.Month, pd); }
            var nm = new DateTime(s.Year, s.Month, 1).AddMonths(1);
            var e = Clamp(nm.Year, nm.Month, pd).AddDays(-1);
            return new[] { s, e };
        }

        public class Summary
        {
            public long Allowance, Extras, Spent, Saved, SaveTarget, Spendable, Left, PerDay, WeekLimit, WeekSpent;
            public int DaysLeft, PeriodDays, SavePct;
            public DateTime From, To;
            public long[] ByCat = new long[OutCats.Length];
        }

        public static Summary Sum()
        {
            var today = DateTime.Today;
            var p = Period(today);
            var s = new Summary { From = p[0], To = p[1] };
            var cf = Cfg();
            s.Allowance = cf == null ? 0 : Model.L(cf, "allowance");
            s.SavePct = cf == null ? 0 : (int)Model.L(cf, "savePct");
            var ws = today.AddDays(-(int)today.DayOfWeek); var we = ws.AddDays(6);
            foreach (var t in Txs())
            {
                DateTime d;
                try { d = Model.ParseDate(Model.Str(t, "date")); } catch { continue; }
                if (d < p[0] || d > p[1]) continue;
                long a = Model.L(t, "amount");
                var ty = Model.Str(t, "type");
                if (ty == "in") s.Extras += a;
                else if (ty == "save") s.Saved += a;
                else
                {
                    s.Spent += a;
                    int ci = Array.IndexOf(OutCats, Model.Str(t, "cat"));
                    s.ByCat[ci < 0 ? OutCats.Length - 1 : ci] += a;
                    if (d >= ws && d <= we) s.WeekSpent += a;
                }
            }
            s.SaveTarget = s.Allowance * s.SavePct / 100;
            s.Spendable = s.Allowance + s.Extras - Math.Max(s.SaveTarget, s.Saved);
            s.Left = s.Spendable - s.Spent;
            s.PeriodDays = (int)(p[1] - p[0]).TotalDays + 1;
            s.DaysLeft = (int)(p[1] - today).TotalDays + 1;
            s.PerDay = s.DaysLeft > 0 ? Math.Max(0, s.Left) / s.DaysLeft : 0;
            s.WeekLimit = s.PeriodDays > 0 ? s.Spendable * 7 / s.PeriodDays : 0;
            return s;
        }

        public static string CatName(string type, string cat)
        {
            if (type == "save") return "Guardado";
            if (type == "in") { int i = Array.IndexOf(InCats, cat); return i < 0 ? "Entrada" : InNames[i]; }
            int j = Array.IndexOf(OutCats, cat);
            return j < 0 ? "Outros" : OutNames[j];
        }

        public static string Brl(long cents)
        {
            bool neg = cents < 0; long v = Math.Abs(cents);
            return (neg ? "−R$ " : "R$ ") + (v / 100).ToString("#,0", CultureInfo.InvariantCulture).Replace(',', '.') + "," + (v % 100).ToString("00");
        }

        public static long Parse(string s)
        {
            if (s == null) return -1;
            s = s.Replace("R$", "").Replace(" ", "").Trim();
            if (s == "") return -1;
            if (s.Contains(",")) s = s.Replace(".", "").Replace(',', '.');
            else if (s.IndexOf('.') != s.LastIndexOf('.') || (s.Contains(".") && s.Length - s.LastIndexOf('.') == 4)) s = s.Replace(".", "");
            double d;
            if (!double.TryParse(s, NumberStyles.Float, CultureInfo.InvariantCulture, out d) || d < 0 || d > 10000000) return -1;
            return (long)Math.Round(d * 100);
        }
    }
}

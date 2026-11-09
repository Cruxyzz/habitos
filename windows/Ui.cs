using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Linq;
using System.Runtime.InteropServices;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: System.Reflection.AssemblyTitle("Hábitos")]
[assembly: System.Reflection.AssemblyProduct("Hábitos")]
[assembly: System.Reflection.AssemblyVersion("3.1.0.0")]

namespace Habitos
{
    static class Theme
    {
        public static Color Bg, Surface, Hover, Ink, Muted, Faint, Line, Accent, OnAccent, Late, Tab;
        public static bool IsLight;

        public static void Apply()
        {
            var th = Settings.Get("theme", "escuro");
            IsLight = th == "claro" || (th == "sistema" && SystemLight());
            Model.LightColors = IsLight;
            if (IsLight)
            {
                Bg = Color.FromArgb(0xF6, 0xF7, 0xF5); Surface = Color.FromArgb(0xFF, 0xFF, 0xFF); Hover = Color.FromArgb(0xEC, 0xEF, 0xEC);
                Ink = Color.FromArgb(0x17, 0x1C, 0x1F); Muted = Color.FromArgb(0x6B, 0x75, 0x7C); Faint = Color.FromArgb(0xB4, 0xBC, 0xB8);
                Line = Color.FromArgb(0xE2, 0xE6, 0xE3); OnAccent = Color.White; Late = Color.FromArgb(0xD9, 0x82, 0x2B); Tab = Color.White;
            }
            else
            {
                Bg = Color.FromArgb(0x13, 0x18, 0x1C); Surface = Color.FromArgb(0x1B, 0x22, 0x27); Hover = Color.FromArgb(0x21, 0x2A, 0x30);
                Ink = Color.FromArgb(0xEE, 0xF2, 0xF5); Muted = Color.FromArgb(0x8C, 0x9A, 0xA6); Faint = Color.FromArgb(0x4A, 0x56, 0x60);
                Line = Color.FromArgb(0x29, 0x32, 0x38); OnAccent = Color.FromArgb(0x0E, 0x1A, 0x15); Late = Color.FromArgb(0xE8, 0xA1, 0x5C); Tab = Color.FromArgb(0x2C, 0x36, 0x3D);
            }
            Accent = Model.ColorOf(Settings.Get("accent", "verde"));
        }

        static bool SystemLight()
        {
            try { using (var k = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize")) return k != null && Convert.ToInt32(k.GetValue("AppsUseLightTheme", 0)) == 1; }
            catch { return false; }
        }
        public static float S = 1f; // escala de DPI
        public static int P(float v) { return (int)Math.Round(v * S); }
        public static Font F(float pt, bool semibold = false) { return new Font(semibold ? "Segoe UI Semibold" : "Segoe UI", pt); }
        public static Font Light(float pt) { return new Font("Segoe UI Light", pt); }

        public static GraphicsPath Round(RectangleF r, float rad)
        {
            var p = new GraphicsPath();
            float d = rad * 2;
            if (d > r.Height) d = r.Height;
            if (d > r.Width) d = r.Width;
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        public static void Smooth(Graphics g)
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        }

        public static void DrawCheck(Graphics g, RectangleF r, bool on, bool late, bool square)
        {
            float rad = square ? r.Width * 0.28f : r.Width / 2;
            using (var path = Round(r, rad))
            {
                if (on)
                {
                    using (var b = new SolidBrush(Accent)) g.FillPath(b, path);
                    using (var pen = new Pen(OnAccent, r.Width * 0.11f) { StartCap = LineCap.Round, EndCap = LineCap.Round, LineJoin = LineJoin.Round })
                        g.DrawLines(pen, new[] {
                            new PointF(r.X + r.Width * 0.28f, r.Y + r.Height * 0.52f),
                            new PointF(r.X + r.Width * 0.44f, r.Y + r.Height * 0.67f),
                            new PointF(r.X + r.Width * 0.73f, r.Y + r.Height * 0.37f) });
                }
                else using (var pen = new Pen(late ? Late : Faint, Math.Max(1.5f, 2f * S))) g.DrawPath(pen, path);
            }
        }
    }

    static class Native
    {
        [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
        [DllImport("user32.dll")] public static extern bool ReleaseCapture();
        [DllImport("user32.dll")] public static extern IntPtr SendMessage(IntPtr h, int msg, IntPtr w, IntPtr l);
        [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
        [DllImport("dwmapi.dll")] public static extern int DwmSetWindowAttribute(IntPtr h, int attr, ref int val, int size);
        public static readonly IntPtr HWND_BOTTOM = new IntPtr(1);
        public static readonly IntPtr HWND_TOPMOST = new IntPtr(-1);
        public static readonly IntPtr HWND_NOTOPMOST = new IntPtr(-2);

        [StructLayout(LayoutKind.Sequential)]
        public struct WINDOWPOS { public IntPtr hwnd, hwndInsertAfter; public int x, y, cx, cy; public uint flags; }

        public static void RoundCorners(IntPtr h) { try { int v = 2; DwmSetWindowAttribute(h, 33, ref v, 4); } catch { } }
        public static void DarkTitle(IntPtr h) { try { int v = 1; if (DwmSetWindowAttribute(h, 20, ref v, 4) != 0) DwmSetWindowAttribute(h, 19, ref v, 4); } catch { } }
    }

    // ===================== widget da área de trabalho =====================

    class DesktopWidget : Form
    {
        DateTime sel = DateTime.Today;
        string mode = "dia";
        DateTime weekStart, monthStart;
        List<Model.Item> items = new List<Model.Item>();
        List<KeyValuePair<RectangleF, string>> hits = new List<KeyValuePair<RectangleF, string>>();
        string hoverKey;
        public bool Pinned = true;
        ContextMenuStrip itemMenu;
        string menuKind, menuId;
        public event Action OpenMenu;
        Dictionary<string, object> next;
        DateTime lastToday = DateTime.Today;
        bool showFoot = true;
        const int W = 330, TabsTop = 74, BodyTop = 116, RowH = 40, SecH = 26, FootH = 96;

        public DesktopWidget()
        {
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = false;
            StartPosition = FormStartPosition.Manual;
            BackColor = Theme.Bg;
            DoubleBuffered = true;
            Text = "Hábitos";
            Width = Theme.P(W);
            Height = Theme.P(200);
            Pinned = Settings.Get("mode", "pin") == "pin";
            TopMost = !Pinned;
            ApplyLook();
            weekStart = sel.AddDays(-(int)sel.DayOfWeek);
            monthStart = new DateTime(sel.Year, sel.Month, 1);

            var wa = Screen.PrimaryScreen.WorkingArea;
            int x, y;
            if (int.TryParse(Settings.Get("x", ""), out x) && int.TryParse(Settings.Get("y", ""), out y) && Screen.AllScreens.Any(sc => sc.WorkingArea.Contains(x + 20, y + 20)))
                Location = new Point(x, y);
            else
                Location = new Point(wa.Right - Width - Theme.P(24), wa.Top + Theme.P(24));

            itemMenu = new ContextMenuStrip();
            itemMenu.Items.Add("Editar…", null, (s, e) => { if (menuId != null) Editors.Edit(menuKind, menuId); });
            itemMenu.Items.Add("Excluir", null, (s, e) => { if (menuId != null) Editors.Delete(menuKind, menuId); });

            Model.Changed += () => { if (IsHandleCreated) BeginInvoke((Action)Reload); };
            Reload();
        }

        protected override CreateParams CreateParams
        {
            get { var cp = base.CreateParams; cp.ExStyle |= 0x80; cp.ClassStyle |= 0x20000; return cp; }
        }

        public void ApplyLook()
        {
            BackColor = Theme.Bg;
            int op; if (!int.TryParse(Settings.Get("opacity", "100"), out op)) op = 100;
            Opacity = Math.Max(50, Math.Min(100, op)) / 100.0;
            Invalidate();
        }

        protected override void OnHandleCreated(EventArgs e) { base.OnHandleCreated(e); Native.RoundCorners(Handle); }
        protected override void OnShown(EventArgs e) { base.OnShown(e); if (Pinned) SendToBottom(); }

        public void SetPinned(bool pin)
        {
            Pinned = pin;
            Settings.Set("mode", pin ? "pin" : "top");
            TopMost = !pin;
            if (pin) SendToBottom();
            else Native.SetWindowPos(Handle, Native.HWND_TOPMOST, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0010);
        }

        void SendToBottom() { Native.SetWindowPos(Handle, Native.HWND_BOTTOM, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0010); }

        protected override void WndProc(ref Message m)
        {
            if (m.Msg == 0x0046 && Pinned)
            {
                var wp = (Native.WINDOWPOS)Marshal.PtrToStructure(m.LParam, typeof(Native.WINDOWPOS));
                wp.hwndInsertAfter = Native.HWND_BOTTOM;
                wp.flags &= ~0x0004u;
                Marshal.StructureToPtr(wp, m.LParam, false);
            }
            base.WndProc(ref m);
        }

        static int Group(Model.Item i) { return i.Overdue ? 0 : i.Kind == "e" ? 1 : i.Kind == "h" ? 2 : 3; }
        static readonly string[] GroupNames = { "ATRASADAS", "COMPROMISSOS", "HÁBITOS", "TAREFAS" };

        public void Reload()
        {
            if (DateTime.Today > sel && sel == lastToday) sel = DateTime.Today;
            lastToday = DateTime.Today;
            items = Model.ItemsFor(sel).OrderBy(Group).ThenBy(i => i.Done ? 1 : 0).ThenBy(i => i.Minutes < 0 ? 100000 : i.Minutes).ToList();
            next = sel == DateTime.Today ? Model.NextTask() : null;
            int h;
            if (mode == "resumo") h = Theme.P(BodyTop + 160 + Model.Habits().Count * 66 + 16);
            else if (mode == "semana") h = Theme.P(BodyTop + 34 + 7 * 50 + 12);
            else if (mode == "mes")
            {
                int offset = (int)monthStart.DayOfWeek, days = DateTime.DaysInMonth(monthStart.Year, monthStart.Month);
                int rows = (offset + days + 6) / 7;
                h = Theme.P(BodyTop + 34 + 22 + rows * 42 + 14);
            }
            else
            {
                int groups = items.Select(Group).Distinct().Count();
                h = Theme.P(BodyTop + 76 + 12) + (next != null ? Theme.P(62) : 0) + Theme.P(4)
                    + groups * Theme.P(SecH) + Math.Max(1, items.Count) * Theme.P(RowH) + Theme.P(FootH) + Theme.P(14);
            }
            int max = Screen.FromControl(this).WorkingArea.Height - Theme.P(40);
            showFoot = mode != "dia" || h <= max;
            if (mode == "dia" && !showFoot) h -= Theme.P(FootH);
            Height = Math.Min(h, max);
            Invalidate();
        }

        static Color Alpha(Color c, int a) { return Color.FromArgb(a, c.R, c.G, c.B); }
        static readonly StringFormat FmtL = new StringFormat { Trimming = StringTrimming.EllipsisCharacter, FormatFlags = StringFormatFlags.NoWrap, LineAlignment = StringAlignment.Center };
        static readonly StringFormat FmtC = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };

        void Hit(RectangleF r, string key) { hits.Add(new KeyValuePair<RectangleF, string>(r, key)); }

        void HoverFill(Graphics g, RectangleF r, string key, float rad)
        {
            if (key == hoverKey) using (var path = Theme.Round(r, rad)) using (var b = new SolidBrush(Theme.Hover)) g.FillPath(b, path);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            Theme.Smooth(g);
            g.Clear(Theme.Bg);
            hits.Clear();
            float pad = Theme.P(18), w = Width;
            var today = DateTime.Today;

            // Cabeçalho
            string title = mode == "resumo" ? "Resumo" : mode == "mes" ? Model.MonthName(monthStart) : mode == "semana" ? "Semana" :
                sel == today ? "Hoje" : (sel == today.AddDays(-1) ? "Ontem" : Model.DowNames[(int)sel.DayOfWeek]);
            using (var f = Theme.F(9f)) using (var b = new SolidBrush(Theme.Muted))
                g.DrawString(Model.Greeting() + (Settings.Get("name", "") == "" ? "" : ", " + Settings.Get("name", "")), f, b, new RectangleF(pad - Theme.P(2), Theme.P(14), w - Theme.P(150), Theme.P(18)), FmtL);
            float tw;
            using (var f = Theme.Light(mode == "mes" ? 18f : 22f)) using (var b = new SolidBrush(Theme.Ink))
            {
                tw = g.MeasureString(title, f).Width;
                g.DrawString(title, f, b, pad - Theme.P(5), Theme.P(mode == "mes" ? 34 : 30));
            }
            if (mode == "dia")
                using (var f = Theme.F(9f)) using (var b = new SolidBrush(Theme.Muted))
                    g.DrawString(Model.DowNames[(int)sel.DayOfWeek].ToLower() + ", " + Model.ShortDate(sel), f, b, pad + tw, Theme.P(46));

            float bs = Theme.P(28), by = Theme.P(14);
            var rMenu = new RectangleF(w - pad - bs + Theme.P(4), by, bs, bs);
            var rPlus = new RectangleF(rMenu.X - bs - Theme.P(2), by, bs, bs);
            IconButton(g, rPlus, "plus", Theme.Ink); Hit(rPlus, "btn_plus");
            IconButton(g, rMenu, "menu", Theme.Ink); Hit(rMenu, "btn_menu");
            if (mode == "dia")
            {
                var rNext = new RectangleF(rPlus.X - bs * 0.8f - Theme.P(8), by, bs * 0.8f, bs);
                var rPrev = new RectangleF(rNext.X - bs * 0.8f, by, bs * 0.8f, bs);
                IconButton(g, rPrev, "prev", sel > Model.Start ? Theme.Muted : Theme.Line); Hit(rPrev, "btn_prev");
                IconButton(g, rNext, "next", sel < today ? Theme.Muted : Theme.Line); Hit(rNext, "btn_next");
            }

            // Abas
            var tabs = new RectangleF(Theme.P(12), Theme.P(TabsTop), w - Theme.P(24), Theme.P(32));
            using (var path = Theme.Round(tabs, tabs.Height / 2)) using (var b = new SolidBrush(Theme.Surface)) g.FillPath(b, path);
            string[] tk = { "dia", "semana", "mes", "resumo" }, tl = { "Dia", "Semana", "Mês", "Resumo" };
            for (int i = 0; i < 4; i++)
            {
                var r = new RectangleF(tabs.X + Theme.P(3) + i * (tabs.Width - Theme.P(6)) / 4, tabs.Y + Theme.P(3), (tabs.Width - Theme.P(6)) / 4, tabs.Height - Theme.P(6));
                bool on = mode == tk[i];
                if (on) using (var path = Theme.Round(r, r.Height / 2)) using (var b = new SolidBrush(Theme.Tab)) g.FillPath(b, path);
                else HoverFill(g, r, "tab_" + tk[i], r.Height / 2);
                using (var f = Theme.F(9f, true)) using (var b = new SolidBrush(on ? Theme.Ink : Theme.Muted)) g.DrawString(tl[i], f, b, r, FmtC);
                Hit(r, "tab_" + tk[i]);
            }

            if (mode == "resumo") PaintStats(g, w, pad, today);
            else if (mode == "semana") PaintWeek(g, w, pad, today);
            else if (mode == "mes") PaintMonth(g, w, pad, today);
            else PaintDay(g, w, pad, today);
        }

        void PaintDay(Graphics g, float w, float pad, DateTime today)
        {
            var p = Model.Progress(sel);
            float frac = p[1] == 0 ? 0 : (float)p[0] / p[1];
            var hero = new RectangleF(Theme.P(10), Theme.P(BodyTop), w - Theme.P(20), Theme.P(76));
            using (var path = Theme.Round(hero, Theme.P(16))) using (var b = new SolidBrush(Theme.Surface)) g.FillPath(b, path);
            float rs = Theme.P(52), rx = hero.X + Theme.P(14), ry = hero.Y + (hero.Height - rs) / 2;
            using (var pen = new Pen(Theme.Line, Theme.P(6))) g.DrawEllipse(pen, rx, ry, rs, rs);
            if (frac > 0) using (var pen = new Pen(Theme.Accent, Theme.P(6)) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                g.DrawArc(pen, rx, ry, rs, rs, -90, Math.Max(4, 360 * frac));
            using (var f = Theme.F(9.5f, true)) using (var b = new SolidBrush(Theme.Ink))
                g.DrawString((int)Math.Round(frac * 100) + "%", f, b, new RectangleF(rx, ry, rs, rs), FmtC);
            int left = p[1] - p[0];
            string headline = p[1] == 0 ? "Dia livre" : left == 0 ? "Tudo feito!" : p[0] == 0 ? "Vamos começar" : "Faltam " + left;
            float hx = rx + rs + Theme.P(16), hw = hero.Right - hx - Theme.P(10);
            int ps = Model.PerfectStreak();
            float ty = hero.Y + (ps > 0 ? Theme.P(10) : Theme.P(18));
            using (var f = Theme.F(12f, true)) using (var b = new SolidBrush(Theme.Ink)) g.DrawString(headline, f, b, new RectangleF(hx, ty, hw, Theme.P(22)), FmtL);
            using (var f = Theme.F(9f)) using (var b = new SolidBrush(Theme.Muted)) g.DrawString(p[0] + " de " + p[1] + " hábitos e tarefas", f, b, new RectangleF(hx, ty + Theme.P(22), hw, Theme.P(18)), FmtL);
            if (ps > 0) using (var f = Theme.F(8.5f, true)) using (var b = new SolidBrush(Theme.Accent))
                g.DrawString(ps + (ps == 1 ? " dia seguido" : " dias seguidos") + " com tudo feito", f, b, new RectangleF(hx, ty + Theme.P(40), hw, Theme.P(18)), FmtL);

            float y = Theme.P(BodyTop + 76 + 12);
            if (next != null)
            {
                var nr = new RectangleF(Theme.P(10), y, w - Theme.P(20), Theme.P(52));
                using (var path = Theme.Round(nr, Theme.P(14))) using (var b = new SolidBrush(Alpha(Theme.Accent, 28))) g.FillPath(b, path);
                var d = Model.Str(next, "date"); var tm = Model.Str(next, "time");
                string when = Model.RelDay(d) + (tm != "" ? " · " + tm : "");
                float ww;
                using (var f = Theme.F(8.5f, true)) using (var f2 = Theme.F(7.5f, true)) using (var b = new SolidBrush(Theme.Accent))
                {
                    ww = g.MeasureString(when, f).Width;
                    g.DrawString(when, f, b, new RectangleF(nr.Right - ww - Theme.P(12), nr.Y, ww + 4, nr.Height), FmtL);
                    g.DrawString("A SEGUIR", f2, b, nr.X + Theme.P(12), nr.Y + Theme.P(7));
                }
                using (var f = Theme.F(10f, true)) using (var b = new SolidBrush(Theme.Ink))
                    g.DrawString(Model.Str(next, "title"), f, b, new RectangleF(nr.X + Theme.P(12), nr.Y + Theme.P(20), nr.Width - ww - Theme.P(36), Theme.P(24)), FmtL);
                y += Theme.P(62);
            }
            y += Theme.P(4);

            float rh = Theme.P(RowH), listEnd = Height - (showFoot ? Theme.P(FootH) : 0) - Theme.P(8);
            if (items.Count == 0)
                using (var f = Theme.F(9.5f)) using (var b = new SolidBrush(Theme.Muted))
                    g.DrawString("Nada pra este dia. Clique em + pra criar.", f, b, pad - Theme.P(2), y + Theme.P(10));
            int lastGroup = -1;
            foreach (var it in items)
            {
                int gr = Group(it);
                if (gr != lastGroup)
                {
                    if (y + Theme.P(SecH) + rh > listEnd + 1) break;
                    lastGroup = gr;
                    var grp = items.Where(i => Group(i) == gr).ToList();
                    using (var f = Theme.F(7.5f, true)) using (var b = new SolidBrush(gr == 0 ? Theme.Late : Theme.Muted))
                    {
                        g.DrawString(GroupNames[gr], f, b, new RectangleF(pad - Theme.P(2), y, w, Theme.P(SecH)), FmtL);
                        if (gr != 1)
                        {
                            string c = grp.Count(i => i.Done) + "/" + grp.Count;
                            var sz = g.MeasureString(c, f);
                            g.DrawString(c, f, b, new RectangleF(w - pad - sz.Width, y, sz.Width + 2, Theme.P(SecH)), FmtL);
                        }
                    }
                    y += Theme.P(SecH);
                }
                if (y + rh > listEnd + 1) break;
                var row = new RectangleF(Theme.P(8), y, w - Theme.P(16), rh - Theme.P(2));
                string key = "item:" + it.Kind + ":" + it.Id;
                HoverFill(g, row, key, Theme.P(10));
                var col = it.Overdue ? Theme.Late : (it.Color == "" ? Theme.Accent : Model.ColorOf(it.Color));
                float tx = pad + Theme.P(30), rxx = w - pad;

                if (it.Kind == "e")
                {
                    using (var path = Theme.Round(new RectangleF(pad + Theme.P(6), y + Theme.P(8), Theme.P(4), rh - Theme.P(18)), Theme.P(2))) using (var b = new SolidBrush(col)) g.FillPath(b, path);
                    string range = (it.Minutes >= 0 ? Model.HHMM(it.Minutes) : "") + (it.End >= 0 ? "–" + Model.HHMM(it.End) : "");
                    int nowMin = DateTime.Now.Hour * 60 + DateTime.Now.Minute;
                    bool now = sel == today && it.Minutes >= 0 && nowMin >= it.Minutes && nowMin < (it.End >= 0 ? it.End : it.Minutes + 60);
                    using (var f = Theme.F(8.5f, now)) using (var b = new SolidBrush(now ? col : Theme.Muted))
                    {
                        string rt = now ? "agora · " + range : range;
                        var sz = g.MeasureString(rt, f);
                        rxx = w - pad - sz.Width;
                        g.DrawString(rt, f, b, new RectangleF(rxx, y, sz.Width + 2, rh - Theme.P(2)), FmtL);
                    }
                    using (var f = Theme.F(10f, true)) using (var b = new SolidBrush(Theme.Ink))
                        g.DrawString(it.Title, f, b, new RectangleF(tx, y, rxx - tx - Theme.P(8), rh - Theme.P(2)), FmtL);
                }
                else
                {
                    float cs = Theme.P(18);
                    DrawColorCheck(g, new RectangleF(pad, y + (rh - cs) / 2 - Theme.P(1), cs, cs), it.Done, col, it.Kind == "t");
                    string right = it.Overdue ? "atrasada" : (it.Streak >= 2 ? it.Streak + "d · " : "") + (it.Minutes >= 0 ? Model.HHMM(it.Minutes) : "");
                    if (right.EndsWith(" · ")) right = right.Substring(0, right.Length - 3);
                    if (right != "")
                        using (var f = Theme.F(8.5f)) using (var b = new SolidBrush(it.Overdue ? Theme.Late : Theme.Muted))
                        {
                            var sz = g.MeasureString(right, f);
                            rxx = w - pad - sz.Width;
                            g.DrawString(right, f, b, new RectangleF(rxx, y, sz.Width + 2, rh - Theme.P(2)), FmtL);
                        }
                    using (var f = Theme.F(10f, !it.Done)) using (var fs = new Font(f, FontStyle.Strikeout)) using (var b = new SolidBrush(it.Done ? Theme.Faint : Theme.Ink))
                        g.DrawString(it.Title, it.Done ? fs : f, b, new RectangleF(tx, y, rxx - tx - Theme.P(8), rh - Theme.P(2)), FmtL);
                }
                Hit(row, key);
                y += rh;
            }

            // Últimos 7 dias
            if (!showFoot) return;
            var foot = new RectangleF(Theme.P(10), Height - Theme.P(FootH) - Theme.P(4), w - Theme.P(20), Theme.P(FootH) - Theme.P(6));
            using (var pen = new Pen(Theme.Line)) g.DrawLine(pen, foot.X + Theme.P(8), foot.Y, foot.Right - Theme.P(8), foot.Y);
            int tot = 0, dn = 0;
            var vals = new float[7];
            for (int i = 0; i < 7; i++)
            {
                var d = today.AddDays(i - 6);
                if (d < Model.Start) { vals[i] = -1; continue; }
                var pp = Model.Progress(d); tot += pp[1]; dn += pp[0];
                vals[i] = pp[1] == 0 ? 0 : (float)pp[0] / pp[1];
            }
            using (var f = Theme.F(8.5f, true))
            {
                using (var b = new SolidBrush(Theme.Muted)) g.DrawString("ÚLTIMOS 7 DIAS", f, b, foot.X + Theme.P(8), foot.Y + Theme.P(10));
                string pc = (tot == 0 ? 0 : (int)Math.Round(100.0 * dn / tot)) + "%";
                var sz = g.MeasureString(pc, f);
                using (var b = new SolidBrush(Theme.Accent)) g.DrawString(pc, f, b, foot.Right - sz.Width - Theme.P(8), foot.Y + Theme.P(10));
            }
            float barTop = foot.Y + Theme.P(32), barH = Theme.P(34), bw = Theme.P(10);
            for (int i = 0; i < 7; i++)
            {
                float cx = foot.X + Theme.P(8) + (foot.Width - Theme.P(16)) * (i + 0.5f) / 7;
                using (var path = Theme.Round(new RectangleF(cx - bw / 2, barTop, bw, barH), bw / 2)) using (var b = new SolidBrush(Theme.Line)) g.FillPath(b, path);
                if (vals[i] > 0)
                {
                    float hh = Math.Max(bw, barH * vals[i]);
                    using (var path = Theme.Round(new RectangleF(cx - bw / 2, barTop + barH - hh, bw, hh), bw / 2))
                    using (var b = new SolidBrush(vals[i] >= 1 ? Theme.Accent : Alpha(Theme.Accent, 150))) g.FillPath(b, path);
                }
                var d = today.AddDays(i - 6);
                using (var f = Theme.F(7.5f, i == 6)) using (var b = new SolidBrush(i == 6 ? Theme.Ink : Theme.Muted))
                    g.DrawString(Model.Dow1[(int)d.DayOfWeek], f, b, new RectangleF(cx - Theme.P(10), barTop + barH + Theme.P(2), Theme.P(20), Theme.P(16)), new StringFormat { Alignment = StringAlignment.Center });
            }
        }

        void PaintStats(Graphics g, float w, float pad, DateTime today)
        {
            var first = new DateTime(today.Year, today.Month, 1);
            var ms = Model.RangeStats(first, today);
            int pct = ms[1] == 0 ? 0 : (int)Math.Round(100.0 * ms[0] / ms[1]);
            float y = Theme.P(BodyTop);
            var card = new RectangleF(Theme.P(10), y, w - Theme.P(20), Theme.P(86));
            using (var path = Theme.Round(card, Theme.P(16))) using (var b = new SolidBrush(Theme.Surface)) g.FillPath(b, path);
            using (var f = Theme.F(7.5f, true)) using (var b = new SolidBrush(Theme.Muted)) g.DrawString("ESTE MÊS", f, b, card.X + Theme.P(14), card.Y + Theme.P(10));
            float pw;
            using (var f = Theme.Light(24f)) using (var b = new SolidBrush(Theme.Ink)) { pw = g.MeasureString(pct + "%", f).Width; g.DrawString(pct + "%", f, b, card.X + Theme.P(8), card.Y + Theme.P(24)); }
            using (var f = Theme.F(9f)) using (var b = new SolidBrush(Theme.Muted)) g.DrawString(ms[0] + " de " + ms[1] + " concluídos", f, b, card.X + Theme.P(10) + pw, card.Y + Theme.P(40));
            float bh = Theme.P(5), by = card.Bottom - Theme.P(16), bw = card.Width - Theme.P(28);
            using (var path = Theme.Round(new RectangleF(card.X + Theme.P(14), by, bw, bh), bh / 2)) using (var b = new SolidBrush(Theme.Line)) g.FillPath(b, path);
            if (ms[0] > 0) using (var path = Theme.Round(new RectangleF(card.X + Theme.P(14), by, Math.Max(bh, bw * ms[0] / ms[1]), bh), bh / 2)) using (var b = new SolidBrush(Theme.Accent)) g.FillPath(b, path);

            y = card.Bottom + Theme.P(8);
            var bwk = Model.BestWeekday();
            string[] bigs = { ms[2].ToString(), Model.PerfectStreak().ToString(), bwk == null ? "—" : Model.DowNames[bwk[0]].Substring(0, 3) };
            string[] smalls = { "dias perfeitos", "seguidos agora", bwk == null ? "melhor dia" : "melhor dia · " + bwk[1] + "%" };
            float tw = (w - Theme.P(20) - Theme.P(12)) / 3;
            for (int i = 0; i < 3; i++)
            {
                var t = new RectangleF(Theme.P(10) + i * (tw + Theme.P(6)), y, tw, Theme.P(56));
                using (var path = Theme.Round(t, Theme.P(12))) using (var b = new SolidBrush(Theme.Surface)) g.FillPath(b, path);
                using (var f = Theme.Light(14f)) using (var b = new SolidBrush(Theme.Accent)) g.DrawString(bigs[i], f, b, t.X + Theme.P(6), t.Y + Theme.P(4));
                using (var f = Theme.F(7.5f)) using (var b = new SolidBrush(Theme.Muted)) g.DrawString(smalls[i], f, b, new RectangleF(t.X + Theme.P(10), t.Y + Theme.P(34), t.Width - Theme.P(12), Theme.P(16)), FmtL);
            }
            y += Theme.P(66);
            using (var f = Theme.F(7.5f, true)) using (var b = new SolidBrush(Theme.Muted)) g.DrawString("HÁBITOS · ÚLTIMOS 30 DIAS", f, b, pad - Theme.P(2), y);
            y += Theme.P(20);
            var from = today.AddDays(-29);
            foreach (var h in Model.Habits())
            {
                if (y + Theme.P(62) > Height) break;
                var col = Model.ColorOf(Model.ColorKey(h));
                var r = new RectangleF(Theme.P(10), y, w - Theme.P(20), Theme.P(60));
                string key = "habit:" + Model.Str(h, "id");
                using (var path = Theme.Round(r, Theme.P(12))) using (var b = new SolidBrush(key == hoverKey ? Theme.Hover : Theme.Surface)) g.FillPath(b, path);
                using (var b = new SolidBrush(col)) g.FillEllipse(b, r.X + Theme.P(12), r.Y + Theme.P(12), Theme.P(8), Theme.P(8));
                var rate = Model.HabitRate(h, from, today);
                string rt = rate[1] == 0 ? "—" : (int)Math.Round(100.0 * rate[0] / rate[1]) + "%";
                float rw;
                using (var f = Theme.F(9f, true)) using (var b = new SolidBrush(col)) { rw = g.MeasureString(rt, f).Width; g.DrawString(rt, f, b, r.Right - rw - Theme.P(10), r.Y + Theme.P(5)); }
                using (var f = Theme.F(9.5f, true)) using (var b = new SolidBrush(Theme.Ink))
                    g.DrawString(Model.Str(h, "title"), f, b, new RectangleF(r.X + Theme.P(26), r.Y + Theme.P(4), r.Width - rw - Theme.P(44), Theme.P(20)), FmtL);
                float cw = (r.Width - Theme.P(24) - 29 * Theme.P(2)) / 30;
                for (int i = 0; i < 30; i++)
                {
                    var d = from.AddDays(i);
                    bool app = Model.AppliesOn(h, d), done = app && Model.Marked(d, Model.Str(h, "id"));
                    var cell = new RectangleF(r.X + Theme.P(12) + i * (cw + Theme.P(2)), r.Y + Theme.P(27), cw, Theme.P(11));
                    using (var path = Theme.Round(cell, Theme.P(2)))
                    using (var b = new SolidBrush(!app ? Alpha(Theme.Line, 90) : done ? col : Theme.Line)) g.FillPath(b, path);
                }
                using (var f = Theme.F(7.5f)) using (var b = new SolidBrush(Theme.Muted))
                    g.DrawString("Sequência atual " + Model.Streak(h) + " · recorde " + Model.RecordStreak(h), f, b, r.X + Theme.P(10), r.Y + Theme.P(41));
                Hit(r, key);
                y += Theme.P(66);
            }
        }

        void NavRow(Graphics g, float w, string label, string prevKey, string nextKey)
        {
            float y = Theme.P(BodyTop);
            var rp = new RectangleF(Theme.P(12), y, Theme.P(30), Theme.P(26));
            var rn = new RectangleF(w - Theme.P(42), y, Theme.P(30), Theme.P(26));
            HoverFill(g, rp, prevKey, Theme.P(13)); HoverFill(g, rn, nextKey, Theme.P(13));
            IconButton(g, rp, "prev", Theme.Muted); IconButton(g, rn, "next", Theme.Muted);
            Hit(rp, prevKey); Hit(rn, nextKey);
            using (var f = Theme.F(9.5f, true)) using (var b = new SolidBrush(Theme.Ink)) g.DrawString(label, f, b, new RectangleF(0, y, w, Theme.P(26)), FmtC);
        }

        void PaintWeek(Graphics g, float w, float pad, DateTime today)
        {
            NavRow(g, w, Model.ShortDate(weekStart) + " – " + Model.ShortDate(weekStart.AddDays(6)), "wprev", "wnext");
            float y = Theme.P(BodyTop + 34), rh = Theme.P(50);
            for (int i = 0; i < 7; i++)
            {
                var d = weekStart.AddDays(i);
                bool isT = d == today, future = d > today;
                var r = new RectangleF(Theme.P(10), y, w - Theme.P(20), rh - Theme.P(4));
                string key = "day:" + Model.Key(d);
                using (var path = Theme.Round(r, Theme.P(12)))
                {
                    using (var b = new SolidBrush(key == hoverKey ? Theme.Hover : Theme.Surface)) g.FillPath(b, path);
                    if (isT) using (var pen = new Pen(Theme.Accent, Theme.P(1.5f))) g.DrawPath(pen, path);
                }
                using (var f = Theme.F(7f, true)) using (var b = new SolidBrush(isT ? Theme.Accent : Theme.Muted))
                    g.DrawString(Model.DowNames[(int)d.DayOfWeek].Substring(0, 3).ToUpper(), f, b, new RectangleF(r.X, r.Y + Theme.P(5), Theme.P(46), Theme.P(14)), FmtC);
                using (var f = Theme.Light(13f)) using (var b = new SolidBrush(Theme.Ink))
                    g.DrawString(d.Day.ToString(), f, b, new RectangleF(r.X, r.Y + Theme.P(17), Theme.P(46), Theme.P(24)), FmtC);

                var its = Model.ItemsFor(d);
                var evs = its.Where(x => x.Kind == "e" || (x.Kind == "t" && !x.Overdue)).Take(2).ToList();
                float tx = r.X + Theme.P(50);
                var p = Model.Progress(d);
                float barW = Theme.P(54), bx = r.Right - barW - Theme.P(40);
                string summary = evs.Count == 0 ? (p[1] == 0 ? "Livre" : p[1] + (p[1] == 1 ? " hábito" : " hábitos")) :
                    string.Join("  ·  ", evs.Select(x => (x.Minutes >= 0 ? Model.HHMM(x.Minutes) + " " : "") + x.Title));
                using (var f = Theme.F(9f)) using (var b = new SolidBrush(evs.Count == 0 ? Theme.Muted : Theme.Ink))
                    g.DrawString(summary, f, b, new RectangleF(tx, r.Y, bx - tx - Theme.P(8), r.Height), FmtL);
                if (p[1] > 0)
                {
                    float bh = Theme.P(5), byy = r.Y + r.Height / 2 - bh / 2;
                    using (var path = Theme.Round(new RectangleF(bx, byy, barW, bh), bh / 2)) using (var b = new SolidBrush(Theme.Line)) g.FillPath(b, path);
                    if (!future && p[0] > 0)
                        using (var path = Theme.Round(new RectangleF(bx, byy, Math.Max(bh, barW * p[0] / p[1]), bh), bh / 2)) using (var b = new SolidBrush(Theme.Accent)) g.FillPath(b, path);
                    using (var f = Theme.F(8f)) using (var b = new SolidBrush(Theme.Muted))
                        g.DrawString(future ? "—" : p[0] + "/" + p[1], f, b, new RectangleF(bx + barW, r.Y, Theme.P(38), r.Height), FmtC);
                }
                Hit(r, key);
                y += rh;
            }
        }

        void PaintMonth(Graphics g, float w, float pad, DateTime today)
        {
            NavRow(g, w, "", "mprev", "mnext");
            float top = Theme.P(BodyTop + 34);
            float cw = (w - Theme.P(20)) / 7, ch = Theme.P(42);
            for (int i = 0; i < 7; i++)
                using (var f = Theme.F(7.5f, true)) using (var b = new SolidBrush(Theme.Muted))
                    g.DrawString(Model.Dow1[i], f, b, new RectangleF(Theme.P(10) + i * cw, top, cw, Theme.P(18)), FmtC);
            top += Theme.P(22);
            int offset = (int)monthStart.DayOfWeek, days = DateTime.DaysInMonth(monthStart.Year, monthStart.Month);
            for (int n = 1; n <= days; n++)
            {
                int idx = offset + n - 1;
                var d = monthStart.AddDays(n - 1);
                var cell = new RectangleF(Theme.P(10) + (idx % 7) * cw, top + (idx / 7) * ch, cw, ch);
                string key = "day:" + Model.Key(d);
                float r = Math.Min(cw, ch) / 2 - Theme.P(5);
                var o = new RectangleF(cell.X + cell.Width / 2 - r, cell.Y + Theme.P(3), r * 2, r * 2);
                if (key == hoverKey) using (var b = new SolidBrush(Theme.Hover)) g.FillEllipse(b, o);
                bool enabled = d <= today && d >= Model.Start;
                if (enabled)
                {
                    var p = Model.Progress(d);
                    if (p[1] > 0)
                    {
                        using (var pen = new Pen(Theme.Line, Theme.P(2.5f))) g.DrawEllipse(pen, o);
                        if (p[0] > 0) using (var pen = new Pen(Theme.Accent, Theme.P(2.5f)) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                            g.DrawArc(pen, o, -90, 360f * p[0] / p[1]);
                    }
                }
                if (d == today)
                {
                    var inner = RectangleF.Inflate(o, -Theme.P(4), -Theme.P(4));
                    using (var b = new SolidBrush(Theme.Ink)) g.FillEllipse(b, inner);
                }
                using (var f = Theme.F(9f, d == today)) using (var b = new SolidBrush(d == today ? Theme.Bg : (d > today ? Theme.Muted : Theme.Ink)))
                    g.DrawString(d.Day.ToString(), f, b, o, FmtC);
                if (Model.HasMarker(d))
                    using (var b = new SolidBrush(Theme.Late)) g.FillEllipse(b, cell.X + cell.Width / 2 - Theme.P(2.5f), o.Bottom + Theme.P(2), Theme.P(5), Theme.P(5));
                Hit(cell, key);
            }
        }

        static void DrawColorCheck(Graphics g, RectangleF r, bool on, Color col, bool square)
        {
            float rad = square ? r.Width * 0.28f : r.Width / 2;
            using (var path = Theme.Round(r, rad))
            {
                if (on)
                {
                    using (var b = new SolidBrush(col)) g.FillPath(b, path);
                    using (var pen = new Pen(Theme.Bg, r.Width * 0.12f) { StartCap = LineCap.Round, EndCap = LineCap.Round, LineJoin = LineJoin.Round })
                        g.DrawLines(pen, new[] {
                            new PointF(r.X + r.Width * 0.28f, r.Y + r.Height * 0.52f),
                            new PointF(r.X + r.Width * 0.44f, r.Y + r.Height * 0.67f),
                            new PointF(r.X + r.Width * 0.73f, r.Y + r.Height * 0.37f) });
                }
                else
                {
                    using (var b = new SolidBrush(Alpha(col, 30))) g.FillPath(b, path);
                    using (var pen = new Pen(col, Math.Max(1.5f, 2f * Theme.S))) g.DrawPath(pen, path);
                }
            }
        }

        void IconButton(Graphics g, RectangleF r, string kind, Color c)
        {
            float cx = r.X + r.Width / 2, cy = r.Y + r.Height / 2, s = Theme.P(5);
            if (hoverKey == "btn_" + kind && c != Theme.Line)
                using (var path = Theme.Round(r, r.Height / 2)) using (var b = new SolidBrush(Theme.Hover)) g.FillPath(b, path);
            using (var pen = new Pen(c, Math.Max(1.5f, 1.8f * Theme.S)) { StartCap = LineCap.Round, EndCap = LineCap.Round, LineJoin = LineJoin.Round })
            using (var br = new SolidBrush(c))
            {
                if (kind == "plus") { g.DrawLine(pen, cx - s, cy, cx + s, cy); g.DrawLine(pen, cx, cy - s, cx, cy + s); }
                else if (kind == "menu") { float d = Theme.P(2.2f); foreach (var dx in new[] { -s, 0, s }) g.FillEllipse(br, cx + dx - d / 2, cy - d / 2, d, d); }
                else if (kind == "prev") g.DrawLines(pen, new[] { new PointF(cx + s * 0.4f, cy - s * 0.8f), new PointF(cx - s * 0.4f, cy), new PointF(cx + s * 0.4f, cy + s * 0.8f) });
                else if (kind == "next") g.DrawLines(pen, new[] { new PointF(cx - s * 0.4f, cy - s * 0.8f), new PointF(cx + s * 0.4f, cy), new PointF(cx - s * 0.4f, cy + s * 0.8f) });
            }
        }

        string HitKey(Point p)
        {
            for (int i = hits.Count - 1; i >= 0; i--) if (hits[i].Key.Contains(p)) return hits[i].Value;
            return null;
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            var k = HitKey(e.Location);
            if (k != hoverKey) { hoverKey = k; Cursor = k != null ? Cursors.Hand : Cursors.Default; Invalidate(); }
            base.OnMouseMove(e);
        }

        protected override void OnMouseLeave(EventArgs e) { hoverKey = null; Invalidate(); base.OnMouseLeave(e); }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button == MouseButtons.Left && HitKey(e.Location) == null)
            {
                Native.ReleaseCapture();
                Native.SendMessage(Handle, 0xA1, new IntPtr(2), IntPtr.Zero);
                Settings.Set("x", Left.ToString());
                Settings.Set("y", Top.ToString());
                if (Pinned) SendToBottom();
            }
        }

        void GoDay(DateTime d)
        {
            if (d > DateTime.Today || d < Model.Start) { Editors.DayPreview(d); return; }
            sel = d; mode = "dia"; Reload();
        }

        protected override void OnMouseClick(MouseEventArgs e)
        {
            base.OnMouseClick(e);
            var k = HitKey(e.Location);
            if (k == null) return;
            var today = DateTime.Today;
            if (e.Button == MouseButtons.Left)
            {
                if (k == "btn_plus") Editors.AskAdd(this, Cursor.Position, sel);
                else if (k == "btn_menu") { var h = OpenMenu; if (h != null) h(); }
                else if (k == "btn_prev") { if (sel > Model.Start) { sel = sel.AddDays(-1); Reload(); } }
                else if (k == "btn_next") { if (sel < today) { sel = sel.AddDays(1); Reload(); } }
                else if (k.StartsWith("tab_"))
                {
                    mode = k.Substring(4);
                    if (mode == "semana") weekStart = sel.AddDays(-(int)sel.DayOfWeek);
                    if (mode == "mes") monthStart = new DateTime(sel.Year, sel.Month, 1);
                    Reload();
                }
                else if (k == "wprev") { weekStart = weekStart.AddDays(-7); Reload(); }
                else if (k == "wnext") { weekStart = weekStart.AddDays(7); Reload(); }
                else if (k == "mprev") { monthStart = monthStart.AddMonths(-1); Reload(); }
                else if (k == "mnext") { monthStart = monthStart.AddMonths(1); Reload(); }
                else if (k.StartsWith("day:")) GoDay(Model.ParseDate(k.Substring(4)));
                else if (k.StartsWith("habit:")) Editors.Edit("h", k.Substring(6));
                else if (k.StartsWith("item:"))
                {
                    var parts = k.Split(':');
                    if (parts[1] == "e") Editors.Edit("e", parts[2]);
                    else { Model.Toggle(sel, parts[1], parts[2]); Program.SyncSoon(); }
                }
            }
            else if (e.Button == MouseButtons.Right && k.StartsWith("item:"))
            {
                var parts = k.Split(':');
                menuKind = parts[1]; menuId = parts[2];
                itemMenu.Show(this, e.Location);
            }
            if (Pinned) SendToBottom();
        }

        protected override void OnMouseDoubleClick(MouseEventArgs e)
        {
            base.OnMouseDoubleClick(e);
            if (e.Y < Theme.P(70) && HitKey(e.Location) == null) { sel = DateTime.Today; mode = "dia"; Reload(); }
        }
    }

    // ===================== formulários (escuros) =====================

    class DarkForm : Form
    {
        public DarkForm(string title, int w, int h)
        {
            Text = title;
            BackColor = Theme.Bg;
            ForeColor = Theme.Ink;
            Font = Theme.F(10f);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false; MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(Theme.P(w), Theme.P(h));
            ShowIcon = false;
            TopMost = true;
        }
        protected override void OnHandleCreated(EventArgs e) { base.OnHandleCreated(e); if (!Theme.IsLight) Native.DarkTitle(Handle); }

        public Label Lbl(string s, int x, int y, bool muted = true)
        {
            var l = new Label { Text = s, AutoSize = true, ForeColor = muted ? Theme.Muted : Theme.Ink, Location = new Point(Theme.P(x), Theme.P(y)), Font = Theme.F(9f, true) };
            Controls.Add(l); return l;
        }
        public TextBox Box(string val, int x, int y, int w, string placeholder = null)
        {
            var t = new TextBox { Text = val, Location = new Point(Theme.P(x), Theme.P(y)), Width = Theme.P(w), BackColor = Theme.Surface, ForeColor = Theme.Ink, BorderStyle = BorderStyle.FixedSingle, Font = Theme.F(10.5f) };
            Controls.Add(t); return t;
        }
        public Button Btn(string s, int x, int y, int w, bool primary = false)
        {
            var b = new Button { Text = s, Location = new Point(Theme.P(x), Theme.P(y)), Size = new Size(Theme.P(w), Theme.P(34)), FlatStyle = FlatStyle.Flat, Cursor = Cursors.Hand, Font = Theme.F(9.5f, true) };
            b.BackColor = primary ? Theme.Accent : Theme.Surface;
            b.ForeColor = primary ? Theme.OnAccent : Theme.Ink;
            b.FlatAppearance.BorderColor = primary ? Theme.Accent : Theme.Line;
            b.FlatAppearance.MouseOverBackColor = primary ? ControlPaint.Light(Theme.Accent) : Theme.Hover;
            Controls.Add(b); return b;
        }
        public CheckBox Check(string s, bool on, int x, int y)
        {
            var c = new CheckBox { Text = s, Checked = on, AutoSize = true, Location = new Point(Theme.P(x), Theme.P(y)), ForeColor = Theme.Ink, FlatStyle = FlatStyle.Flat };
            Controls.Add(c); return c;
        }
        public CheckBox Pill(string s, bool on, int x, int y, int w)
        {
            var c = new CheckBox { Text = s, Checked = on, Appearance = Appearance.Button, TextAlign = ContentAlignment.MiddleCenter, Location = new Point(Theme.P(x), Theme.P(y)), Size = new Size(Theme.P(w), Theme.P(32)), FlatStyle = FlatStyle.Flat, Cursor = Cursors.Hand, Font = Theme.F(9.5f, true) };
            c.FlatAppearance.BorderColor = Theme.Line;
            c.FlatAppearance.CheckedBackColor = Theme.Accent;
            c.FlatAppearance.MouseOverBackColor = Theme.Hover;
            Action paint = () => { c.ForeColor = c.Checked ? Theme.OnAccent : Theme.Ink; c.BackColor = c.Checked ? Theme.Accent : Theme.Surface; };
            c.CheckedChanged += (s2, e) => paint();
            paint();
            Controls.Add(c); return c;
        }
    }

    static class Editors
    {
        public static void AskAdd(Control owner, Point at, DateTime sel)
        {
            var m = new ContextMenuStrip();
            m.Items.Add("Compromisso · com início e fim", null, (s, e) => EditEvent(null, sel));
            m.Items.Add("Hábito · algo que você repete e marca", null, (s, e) => EditHabit(null));
            m.Items.Add("Tarefa · algo pra fazer uma vez", null, (s, e) => EditTask(null, sel));
            m.Show(at);
        }

        public static void Edit(string kind, string id)
        {
            if (kind == "e") EditEvent(Model.Task(id), DateTime.Today);
            else if (kind == "t") EditTask(Model.Task(id), DateTime.Today);
            else EditHabit(Model.Habit(id));
        }

        public static void Delete(string kind, string id)
        {
            var o = kind == "h" ? Model.Habit(id) : Model.Task(id);
            if (o == null) return;
            if (MessageBox.Show("Excluir \"" + Model.Str(o, "title") + "\"?", "Hábitos", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) return;
            o["deleted"] = true;
            if (kind == "h") Model.PutHabit(o); else Model.PutTask(o);
            Program.SyncSoon();
        }

        static Dictionary<string, object> Copy(Dictionary<string, object> o)
        {
            return o == null ? new Dictionary<string, object>() : new Dictionary<string, object>(o);
        }

        public static void DayPreview(DateTime d)
        {
            var lines = new List<string>();
            int habits = 0;
            foreach (var it in Model.ItemsFor(d))
            {
                if (it.Kind == "h") { habits++; continue; }
                lines.Add((it.Minutes >= 0 ? Model.HHMM(it.Minutes) + (it.End >= 0 ? "–" + Model.HHMM(it.End) : "") + "   " : "") + it.Title);
            }
            if (habits > 0) lines.Add("\n" + habits + " hábitos programados");
            MessageBox.Show(lines.Count == 0 ? "Nada programado." : string.Join("\n", lines), Model.LongDate(d));
        }

        public static void EditEvent(Dictionary<string, object> existing, DateTime sel)
        {
            bool isNew = existing == null;
            var ev = Copy(existing);
            var f = new DarkForm(isNew ? "Novo compromisso" : "Editar compromisso", 380, 470);
            f.Lbl("NOME", 22, 18);
            var title = f.Box(Model.Str(ev, "title"), 22, 40, 336);
            f.Lbl("LOCAL OU DETALHE (OPCIONAL)", 22, 82);
            var note = f.Box(Model.Str(ev, "note"), 22, 104, 336);

            var days = Model.Days(ev);
            bool weekly = days.Length > 0;
            f.Lbl("REPETE?", 22, 146);
            var once = f.Pill("Uma vez", !weekly, 22, 168, 164);
            var week = f.Pill("Toda semana", weekly, 194, 168, 164);
            var dateLbl = f.Lbl("DATA (DD/MM/AAAA)", 22, 214);
            string d0 = isNew ? Model.Key(sel < DateTime.Today ? DateTime.Today : sel) : Model.Str(ev, "date");
            var date = f.Box(d0 == "" ? DateTime.Today.ToString("dd/MM/yyyy") : Model.ParseDate(d0).ToString("dd/MM/yyyy"), 22, 236, 150);
            var pills = new CheckBox[7];
            for (int i = 0; i < 7; i++) pills[i] = f.Pill(Model.Dow1[i], days.Contains(i), 22 + i * 48, 236, 42);
            Action swap = () =>
            {
                bool wk = week.Checked;
                date.Visible = !wk; dateLbl.Text = wk ? "DIAS" : "DATA (DD/MM/AAAA)";
                foreach (var p in pills) p.Visible = wk;
            };
            once.Click += (s, e) => { once.Checked = true; week.Checked = false; swap(); };
            week.Click += (s, e) => { week.Checked = true; once.Checked = false; swap(); };
            swap();

            f.Lbl("INÍCIO", 22, 282);
            var start = f.Box(Model.Str(ev, "start"), 22, 304, 100);
            f.Lbl("FIM", 140, 282);
            var end = f.Box(Model.Str(ev, "end"), 140, 304, 100);
            f.Lbl("AVISAR (MIN ANTES)", 258, 282);
            var lead = f.Box(isNew ? "15" : Model.L(ev, "lead").ToString(), 258, 304, 100);
            var remind = f.Check("Lembrete", isNew || Model.Bool(ev, "remind", true), 22, 344);

            string color = isNew ? "azul" : (Model.Str(ev, "color") == "" ? "azul" : Model.Str(ev, "color"));
            var swatches = new List<Panel>();
            for (int i = 0; i < Model.ColorKeys.Length; i++)
            {
                var sw = new Panel { Location = new Point(Theme.P(130 + i * 34), Theme.P(340)), Size = new Size(Theme.P(28), Theme.P(28)), Cursor = Cursors.Hand, Tag = Model.ColorKeys[i] };
                sw.Paint += (s2, e2) =>
                {
                    Theme.Smooth(e2.Graphics);
                    e2.Graphics.Clear(Theme.Bg);
                    using (var b = new SolidBrush(Model.ColorOf((string)((Panel)s2).Tag))) e2.Graphics.FillEllipse(b, Theme.P(3), Theme.P(3), Theme.P(22), Theme.P(22));
                    if ((string)((Panel)s2).Tag == color) using (var pen = new Pen(Theme.Ink, Theme.P(2))) e2.Graphics.DrawEllipse(pen, Theme.P(1), Theme.P(1), Theme.P(26), Theme.P(26));
                };
                sw.Click += (s2, e2) => { color = (string)((Panel)s2).Tag; foreach (var x in swatches) x.Invalidate(); };
                swatches.Add(sw); f.Controls.Add(sw);
            }

            var save = f.Btn("Salvar", 238, 412, 120, true);
            f.Btn("Cancelar", 110, 412, 120).Click += (s, e) => f.Close();
            if (!isNew) f.Btn("Excluir", 22, 412, 80).Click += (s, e) => { f.Close(); Delete("e", Model.Str(ev, "id")); };
            f.AcceptButton = save;
            save.Click += (s, e) =>
            {
                var t = title.Text.Trim();
                if (t == "") { MessageBox.Show("Dê um nome ao compromisso."); return; }
                int st = Model.ParseTime(start.Text.Trim()), en = end.Text.Trim() == "" ? -1 : Model.ParseTime(end.Text.Trim());
                if (st < 0) { MessageBox.Show("Coloque o horário de início, no formato 08:00."); return; }
                if (end.Text.Trim() != "" && (en < 0 || en <= st)) { MessageBox.Show("O fim precisa ser depois do início (formato 17:10)."); return; }
                int ld; if (!int.TryParse(lead.Text.Trim(), out ld) || ld < 0) ld = 0;
                string dk = "";
                object[] dd = new object[0];
                if (week.Checked)
                {
                    dd = Enumerable.Range(0, 7).Where(i => pills[i].Checked).Cast<object>().ToArray();
                    if (dd.Length == 0) { MessageBox.Show("Escolha pelo menos um dia."); return; }
                }
                else
                {
                    DateTime x;
                    var txt = date.Text.Trim();
                    if (txt.Length <= 5) txt += "/" + DateTime.Today.Year;
                    if (!DateTime.TryParseExact(txt, new[] { "d/M/yyyy", "dd/MM/yyyy" }, System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.None, out x))
                    { MessageBox.Show("Data inválida. Use o formato 25/12/2026."); return; }
                    dk = Model.Key(x);
                }
                if (isNew) ev["id"] = Model.NewId("e");
                ev["type"] = "event"; ev["title"] = t; ev["note"] = note.Text.Trim(); ev["days"] = dd; ev["date"] = dk;
                ev["start"] = Model.HHMM(st); ev["end"] = en >= 0 ? Model.HHMM(en) : ""; ev["lead"] = ld; ev["remind"] = remind.Checked; ev["color"] = color;
                Model.PutTask(ev);
                Program.SyncSoon();
                f.Close();
            };
            f.Show();
            f.Activate();
        }

        public static void EditHabit(Dictionary<string, object> existing)
        {
            bool isNew = existing == null;
            var h = Copy(existing);
            var f = new DarkForm(isNew ? "Novo hábito" : "Editar hábito", 380, 404);
            f.Lbl("NOME", 22, 18);
            var title = f.Box(Model.Str(h, "title"), 22, 40, 336);
            f.Lbl("DETALHE (OPCIONAL)", 22, 82);
            var note = f.Box(Model.Str(h, "note"), 22, 104, 336);
            f.Lbl("DIAS", 22, 146);
            var days = isNew ? new[] { 0, 1, 2, 3, 4, 5, 6 } : Model.Days(h);
            var pills = new CheckBox[7];
            for (int i = 0; i < 7; i++) pills[i] = f.Pill(Model.Dow1[i], days.Contains(i), 22 + i * 48, 168, 42);
            f.Lbl("HORÁRIO (HH:MM, VAZIO = SEM HORÁRIO)", 22, 214);
            var time = f.Box(Model.Str(h, "time"), 22, 236, 100);
            var remind = f.Check("Lembrete no horário", Model.Bool(h, "remind", true), 200, 238);
            f.Lbl("COR", 22, 282);
            string color = isNew ? Model.ColorKeys[new Random().Next(Model.ColorKeys.Length)] : Model.ColorKey(h);
            var swatches = new List<Panel>();
            for (int i = 0; i < Model.ColorKeys.Length; i++)
            {
                var key = Model.ColorKeys[i];
                var sw = new Panel { Location = new Point(Theme.P(22 + i * 40), Theme.P(302)), Size = new Size(Theme.P(30), Theme.P(30)), Cursor = Cursors.Hand, Tag = key };
                sw.Paint += (s2, e2) =>
                {
                    Theme.Smooth(e2.Graphics);
                    e2.Graphics.Clear(Theme.Bg);
                    var rr = new RectangleF(Theme.P(3), Theme.P(3), Theme.P(24), Theme.P(24));
                    using (var b = new SolidBrush(Model.ColorOf((string)((Panel)s2).Tag))) e2.Graphics.FillEllipse(b, rr);
                    if ((string)((Panel)s2).Tag == color) using (var pen = new Pen(Theme.Ink, Theme.P(2))) e2.Graphics.DrawEllipse(pen, Theme.P(1), Theme.P(1), Theme.P(28), Theme.P(28));
                };
                sw.Click += (s2, e2) => { color = (string)((Panel)s2).Tag; foreach (var x in swatches) x.Invalidate(); };
                swatches.Add(sw);
                f.Controls.Add(sw);
            }
            var save = f.Btn("Salvar", 238, 352, 120, true);
            f.Btn("Cancelar", 110, 352, 120).Click += (s, e) => f.Close();
            if (!isNew) f.Btn("Excluir", 22, 352, 80).Click += (s, e) => { f.Close(); Delete("h", Model.Str(h, "id")); };
            f.AcceptButton = save;
            save.Click += (s, e) =>
            {
                var t = title.Text.Trim();
                if (t == "") { MessageBox.Show("Dê um nome ao hábito."); return; }
                var tm = time.Text.Trim();
                if (tm != "" && Model.ParseTime(tm) < 0) { MessageBox.Show("Horário inválido. Use o formato 19:00."); return; }
                if (tm != "") tm = Model.HHMM(Model.ParseTime(tm));
                var d = Enumerable.Range(0, 7).Where(i => pills[i].Checked).Cast<object>().ToArray();
                if (d.Length == 0) { MessageBox.Show("Escolha pelo menos um dia."); return; }
                if (isNew) { h["id"] = Model.NewId("h"); h["order"] = Model.NextOrder(); h["lead"] = 0; }
                if (tm != Model.Str(h, "time")) h.Remove("times");
                if (note.Text.Trim() != Model.Str(h, "note")) h.Remove("notes");
                h["title"] = t; h["note"] = note.Text.Trim(); h["days"] = d; h["time"] = tm; h["remind"] = remind.Checked; h["color"] = color;
                Model.PutHabit(h);
                Program.SyncSoon();
                f.Close();
            };
            f.Show();
            f.Activate();
        }

        public static void EditTask(Dictionary<string, object> existing, DateTime sel)
        {
            bool isNew = existing == null;
            var t = Copy(existing);
            var f = new DarkForm(isNew ? "Nova tarefa" : "Editar tarefa", 380, 360);
            f.Lbl("O QUE PRECISA FAZER?", 22, 18);
            var title = f.Box(Model.Str(t, "title"), 22, 40, 336);
            f.Lbl("DETALHE (OPCIONAL)", 22, 82);
            var note = f.Box(Model.Str(t, "note"), 22, 104, 336);
            string d0 = isNew ? Model.Key(sel < DateTime.Today ? sel : DateTime.Today) : Model.Str(t, "date");
            f.Lbl("DATA (DD/MM/AAAA)", 22, 146);
            var date = f.Box(d0 == "" ? "" : Model.ParseDate(d0).ToString("dd/MM/yyyy"), 22, 168, 150);
            f.Lbl("HORÁRIO (HH:MM)", 196, 146);
            var time = f.Box(Model.Str(t, "time"), 196, 168, 100);
            var remind = f.Check("Lembrete no horário", Model.Bool(t, "remind", true), 22, 214);
            var hint = new Label { Text = "Deixe a data vazia para uma tarefa sem prazo.", AutoSize = true, ForeColor = Theme.Muted, Location = new Point(Theme.P(22), Theme.P(248)) };
            f.Controls.Add(hint);
            var save = f.Btn("Salvar", 238, 302, 120, true);
            f.Btn("Cancelar", 110, 302, 120).Click += (s, e) => f.Close();
            if (!isNew) f.Btn("Excluir", 22, 302, 80).Click += (s, e) => { f.Close(); Delete("t", Model.Str(t, "id")); };
            f.AcceptButton = save;
            save.Click += (s, e) =>
            {
                var ti = title.Text.Trim();
                if (ti == "") { MessageBox.Show("Escreva a tarefa."); return; }
                string dk = "";
                if (date.Text.Trim() != "")
                {
                    DateTime dd;
                    var txt = date.Text.Trim();
                    if (txt.Length <= 5) txt += "/" + DateTime.Today.Year;
                    if (!DateTime.TryParseExact(txt, new[] { "d/M/yyyy", "dd/MM/yyyy" }, System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.None, out dd))
                    { MessageBox.Show("Data inválida. Use o formato 25/12/2026."); return; }
                    dk = Model.Key(dd);
                }
                var tm = time.Text.Trim();
                if (tm != "" && Model.ParseTime(tm) < 0) { MessageBox.Show("Horário inválido. Use o formato 19:00."); return; }
                if (tm != "") tm = Model.HHMM(Model.ParseTime(tm));
                if (isNew) { t["id"] = Model.NewId("t"); t["done"] = false; }
                t["title"] = ti; t["note"] = note.Text.Trim(); t["date"] = dk; t["time"] = dk == "" ? "" : tm; t["remind"] = remind.Checked;
                Model.PutTask(t);
                Program.SyncSoon();
                f.Close();
            };
            f.Show();
            f.Activate();
        }

        public static void Manage()
        {
            var f = new DarkForm("Hábitos e tarefas", 420, 480);
            var list = new ListBox { Location = new Point(Theme.P(22), Theme.P(20)), Size = new Size(Theme.P(376), Theme.P(370)), BackColor = Theme.Surface, ForeColor = Theme.Ink, BorderStyle = BorderStyle.None, Font = Theme.F(10.5f), DrawMode = DrawMode.OwnerDrawFixed, ItemHeight = Theme.P(30), IntegralHeight = false };
            f.Controls.Add(list);
            var rows = new List<KeyValuePair<string, string>>(); // kind, id
            Action fill = () =>
            {
                rows.Clear(); list.Items.Clear();
                rows.Add(new KeyValuePair<string, string>("x", "COMPROMISSOS")); list.Items.Add("COMPROMISSOS");
                foreach (var ev in Model.Events())
                {
                    var dd = Model.Days(ev);
                    string when = dd.Length == 0 ? (Model.Str(ev, "date") == "" ? "" : Model.ShortDate(Model.ParseDate(Model.Str(ev, "date")))) :
                        dd.Length == 7 ? "todo dia" : string.Join(" ", dd.OrderBy(x => x).Select(x => Model.Dow1[x]));
                    rows.Add(new KeyValuePair<string, string>("e", Model.Str(ev, "id")));
                    list.Items.Add(Model.Str(ev, "title") + "\t" + when + " · " + Model.Str(ev, "start"));
                }
                rows.Add(new KeyValuePair<string, string>("x", "HÁBITOS")); list.Items.Add("HÁBITOS");
                foreach (var h in Model.Habits())
                {
                    var days = Model.Days(h);
                    string ds = days.Length == 7 ? "todo dia" : string.Join(" ", days.OrderBy(x => x).Select(x => Model.Dow1[x]));
                    var tm = Model.Str(h, "time");
                    rows.Add(new KeyValuePair<string, string>("h", Model.Str(h, "id")));
                    list.Items.Add(Model.Str(h, "title") + "\t" + ds + (tm != "" ? " · " + tm : ""));
                }
                rows.Add(new KeyValuePair<string, string>("x", "TAREFAS")); list.Items.Add("TAREFAS");
                foreach (var t in Model.Tasks().Where(t => !Model.Bool(t, "done", false)))
                {
                    var d = Model.Str(t, "date");
                    rows.Add(new KeyValuePair<string, string>("t", Model.Str(t, "id")));
                    list.Items.Add(Model.Str(t, "title") + "\t" + (d == "" ? "sem data" : Model.ShortDate(Model.ParseDate(d))));
                }
            };
            list.DrawItem += (s, e) =>
            {
                if (e.Index < 0) return;
                var g = e.Graphics; Theme.Smooth(g);
                bool header = rows[e.Index].Key == "x";
                bool selected = (e.State & DrawItemState.Selected) != 0 && !header;
                using (var b = new SolidBrush(selected ? Theme.Hover : Theme.Surface)) g.FillRectangle(b, e.Bounds);
                var parts = list.Items[e.Index].ToString().Split('\t');
                var fmt = new StringFormat { LineAlignment = StringAlignment.Center, Trimming = StringTrimming.EllipsisCharacter, FormatFlags = StringFormatFlags.NoWrap };
                if (header)
                    using (var ft = Theme.F(8.5f, true)) using (var b = new SolidBrush(Theme.Muted)) g.DrawString(parts[0], ft, b, new RectangleF(e.Bounds.X + Theme.P(10), e.Bounds.Y + Theme.P(4), e.Bounds.Width, e.Bounds.Height), fmt);
                else
                {
                    float rw = 0;
                    if (parts.Length > 1)
                        using (var ft = Theme.F(9f)) using (var b = new SolidBrush(Theme.Muted))
                        {
                            rw = g.MeasureString(parts[1], ft).Width;
                            g.DrawString(parts[1], ft, b, new RectangleF(e.Bounds.Right - rw - Theme.P(10), e.Bounds.Y, rw + 4, e.Bounds.Height), fmt);
                        }
                    using (var ft = Theme.F(10f)) using (var b = new SolidBrush(Theme.Ink))
                        g.DrawString(parts[0], ft, b, new RectangleF(e.Bounds.X + Theme.P(10), e.Bounds.Y, e.Bounds.Width - rw - Theme.P(30), e.Bounds.Height), fmt);
                }
            };
            Action edit = () =>
            {
                int i = list.SelectedIndex;
                if (i < 0 || rows[i].Key == "x") return;
                Edit(rows[i].Key, rows[i].Value);
            };
            list.DoubleClick += (s, e) => edit();
            f.Btn("+ Criar", 22, 410, 120).Click += (s, e) => AskAdd(f, Cursor.Position, DateTime.Today);
            f.Btn("Editar", 278, 410, 120, true).Click += (s, e) => edit();
            Action changed = () => { if (f.IsHandleCreated && !f.IsDisposed) f.BeginInvoke(fill); };
            Model.Changed += changed;
            f.FormClosed += (s, e) => Model.Changed -= changed;
            fill();
            f.Show();
            f.Activate();
        }

        public static void SettingsForm(DesktopWidget w)
        {
            var f = new DarkForm("Ajustes", 440, 560);
            f.Lbl("SEU NOME (APARECE NO CUMPRIMENTO)", 22, 18);
            var name = f.Box(Settings.Get("name", ""), 22, 40, 396);

            f.Lbl("TEMA", 22, 84);
            string th = Settings.Get("theme", "escuro");
            var t1 = f.Pill("Automático", th == "sistema", 22, 106, 128);
            var t2 = f.Pill("Claro", th == "claro", 156, 106, 128);
            var t3 = f.Pill("Escuro", th == "escuro", 290, 106, 128);
            var tp = new[] { t1, t2, t3 };
            foreach (var p in tp) { var me = p; me.Click += (s, e) => { foreach (var q in tp) q.Checked = q == me; }; }

            f.Lbl("COR DE DESTAQUE", 22, 152);
            string accent = Settings.Get("accent", "verde");
            var sws = new List<Panel>();
            for (int i = 0; i < Model.ColorKeys.Length; i++)
            {
                var sw = new Panel { Location = new Point(Theme.P(22 + i * 38), Theme.P(172)), Size = new Size(Theme.P(30), Theme.P(30)), Cursor = Cursors.Hand, Tag = Model.ColorKeys[i] };
                sw.Paint += (s2, e2) =>
                {
                    Theme.Smooth(e2.Graphics); e2.Graphics.Clear(Theme.Bg);
                    using (var b = new SolidBrush(Model.ColorOf((string)((Panel)s2).Tag))) e2.Graphics.FillEllipse(b, Theme.P(3), Theme.P(3), Theme.P(24), Theme.P(24));
                    if ((string)((Panel)s2).Tag == accent) using (var pen = new Pen(Theme.Ink, Theme.P(2))) e2.Graphics.DrawEllipse(pen, Theme.P(1), Theme.P(1), Theme.P(28), Theme.P(28));
                };
                sw.Click += (s2, e2) => { accent = (string)((Panel)s2).Tag; foreach (var x in sws) x.Invalidate(); };
                sws.Add(sw); f.Controls.Add(sw);
            }

            f.Lbl("TRANSPARÊNCIA DO WIDGET", 22, 218);
            int op; if (!int.TryParse(Settings.Get("opacity", "100"), out op)) op = 100;
            var track = new TrackBar { Minimum = 50, Maximum = 100, TickFrequency = 10, Value = Math.Max(50, Math.Min(100, op)), Location = new Point(Theme.P(16), Theme.P(238)), Width = Theme.P(400), BackColor = Theme.Bg };
            track.Scroll += (s, e) => { w.Opacity = track.Value / 100.0; };
            f.Controls.Add(track);

            f.Lbl("SINCRONIZAÇÃO COM O CELULAR", 22, 296);
            var url = f.Box(Settings.Get("syncurl", ""), 22, 318, 396);
            var status = new Label { Text = Sync.Status == "" ? (Settings.Get("syncurl", "") == "" ? "Cole o link do script do Google e clique em Salvar." : "Ainda não sincronizado nesta sessão.") : Sync.Status, AutoSize = false, Size = new Size(Theme.P(396), Theme.P(34)), ForeColor = Theme.Muted, Location = new Point(Theme.P(22), Theme.P(350)) };
            f.Controls.Add(status);
            f.Lbl("WIDGET", 22, 392);
            var pin = f.Check("Fixo na área de trabalho (fica atrás das janelas)", w.Pinned, 22, 414);
            var startup = f.Check("Iniciar com o Windows", Program.IsStartup(), 22, 444);
            var save = f.Btn("Salvar", 298, 502, 120, true);
            f.Btn("Fechar", 170, 502, 120).Click += (s, e) => { w.ApplyLook(); f.Close(); };
            f.Controls.Add(new Label { Text = "Versão " + Updater.Version, AutoSize = true, ForeColor = Theme.Muted, Location = new Point(Theme.P(22), Theme.P(512)) });
            Action<bool, string> onSync = (ok, msg) => { if (!f.IsDisposed && f.IsHandleCreated) f.BeginInvoke((Action)(() => status.Text = msg)); };
            Sync.Finished += onSync;
            f.FormClosed += (s, e) => Sync.Finished -= onSync;
            save.Click += (s, e) =>
            {
                string newTheme = t2.Checked ? "claro" : t3.Checked ? "escuro" : "sistema";
                bool look = newTheme != Settings.Get("theme", "escuro") || accent != Settings.Get("accent", "verde");
                Settings.Set("name", name.Text.Trim());
                Settings.Set("theme", newTheme);
                Settings.Set("accent", accent);
                Settings.Set("opacity", track.Value.ToString());
                Settings.Set("syncurl", url.Text.Trim());
                w.SetPinned(pin.Checked);
                if (startup.Checked != Program.IsStartup()) Program.ToggleStartup();
                Theme.Apply();
                w.ApplyLook();
                w.Reload();
                Sync.Run();
                if (look) { f.Close(); SettingsForm(w); } else status.Text = url.Text.Trim() == "" ? "Salvo." : "Salvo. Sincronizando…";
            };
            f.Show();
            f.Activate();
        }
    }

    // ===================== programa =====================

    static class Program
    {
        static DesktopWidget widget;
        static NotifyIcon tray;
        static Timer tick, syncTimer, soonTimer;
        static DateTime lastCheck = DateTime.Now;
        static Action checkUpdateRef;
        const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

        [STAThread]
        static void Main(string[] args)
        {
            bool created;
            using (var mutex = new System.Threading.Mutex(false, "HabitosYago_v2"))
            {
                bool updated = args.Contains("--updated");
                try { created = mutex.WaitOne(updated ? 20000 : 0); } catch (System.Threading.AbandonedMutexException) { created = true; }
                if (!created) return;
                Updater.CleanupOld();
                try { Native.SetProcessDPIAware(); } catch { }
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                using (var g = Graphics.FromHwnd(IntPtr.Zero)) Theme.S = g.DpiX / 96f;
                Theme.Apply();

                widget = new DesktopWidget();
                var icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
                widget.Icon = icon;

                var menu = new ContextMenuStrip();
                menu.Items.Add("Mostrar / esconder widget", null, (s, e) => ToggleWidget());
                menu.Items.Add("Hábitos e tarefas…", null, (s, e) => Editors.Manage());
                menu.Items.Add("Ajustes…", null, (s, e) => Editors.SettingsForm(widget));
                menu.Items.Add("Sincronizar agora", null, (s, e) => Sync.Run());
                menu.Items.Add("Versão " + Updater.Version).Enabled = false;
                menu.Items.Add(new ToolStripSeparator());
                menu.Items.Add("Sair", null, (s, e) => { tray.Visible = false; Application.Exit(); });
                tray = new NotifyIcon { Icon = icon, Text = "Hábitos", Visible = true, ContextMenuStrip = menu };
                tray.MouseClick += (s, e) => { if (e.Button == MouseButtons.Left) ToggleWidget(); };
                tray.BalloonTipClicked += (s, e) => ShowWidget();

                var wmenu = new ContextMenuStrip();
                wmenu.Items.Add("Hábitos e tarefas…", null, (s, e) => Editors.Manage());
                wmenu.Items.Add("Ajustes…", null, (s, e) => Editors.SettingsForm(widget));
                wmenu.Items.Add("Sincronizar agora", null, (s, e) => Sync.Run());
                wmenu.Items.Add("Esconder widget", null, (s, e) => widget.Hide());
                wmenu.Items.Add("Procurar atualização", null, (s, e) => { if (checkUpdateRef != null) checkUpdateRef(); Notify("Hábitos", "Procurando atualização… Se houver, o app reabre sozinho."); });
                widget.OpenMenu += () => wmenu.Show(Cursor.Position);

                Sync.Finished += (ok, msg) => { if (widget.IsHandleCreated) widget.BeginInvoke((Action)(() => { tray.Text = Trunc("Hábitos · " + msg); })); };

                tick = new Timer { Interval = 20000 };
                tick.Tick += (s, e) => Tick();
                tick.Start();
                syncTimer = new Timer { Interval = 3 * 60 * 1000 };
                syncTimer.Tick += (s, e) => Sync.Run();
                syncTimer.Start();
                soonTimer = new Timer { Interval = 2500 };
                soonTimer.Tick += (s, e) => { soonTimer.Stop(); Sync.Run(); };

                if (Settings.Get("firstrun2", "1") == "1")
                {
                    Settings.Set("firstrun2", "0");
                    if (!IsStartup()) ToggleStartup();
                    else SetStartupPath();
                }
                else if (IsStartup()) SetStartupPath();

                if (Settings.Get("hidden", "0") != "1") widget.Show();
                Sync.Run();
                if (updated) Notify("Hábitos atualizado", "Você já está na versão nova.");
                Action checkUpdate = () => Updater.CheckAsync(notes => widget.BeginInvoke((Action)(() =>
                {
                    tray.Visible = false;
                    mutex.ReleaseMutex();
                    Updater.Restart();
                    Application.Exit();
                })));
                var updTimer = new Timer { Interval = 15 * 60 * 1000 };
                updTimer.Tick += (s, e) => checkUpdate();
                checkUpdateRef = checkUpdate;
                updTimer.Start();
                var firstCheck = new Timer { Interval = 5000 };
                menu.Items.Insert(4, new ToolStripMenuItem("Procurar atualização", null, (s, e) => { checkUpdate(); Notify("Hábitos", "Procurando atualização… Se houver, o app reabre sozinho."); }));
                firstCheck.Tick += (s, e) => { firstCheck.Stop(); checkUpdate(); };
                firstCheck.Start();
                Application.Run();
            }
        }

        static string Trunc(string s) { return s.Length > 63 ? s.Substring(0, 63) : s; }

        public static void SyncSoon() { soonTimer.Stop(); soonTimer.Start(); }

        static void ShowWidget() { widget.Show(); Settings.Set("hidden", "0"); widget.Reload(); }
        static void ToggleWidget()
        {
            if (widget.Visible) { widget.Hide(); Settings.Set("hidden", "1"); }
            else ShowWidget();
        }

        static void Tick()
        {
            var now = DateTime.Now;
            widget.Reload();
            var from = lastCheck;
            if ((now - from).TotalHours > 6) from = now.AddHours(-6);
            for (var d = from.Date.AddDays(-1); d <= now.Date; d = d.AddDays(1))
            {
                foreach (var h in Model.Habits())
                {
                    if (!Model.Bool(h, "remind", true) || !Model.AppliesOn(h, d)) continue;
                    int m = Model.TimeOf(h, d);
                    if (m < 0) continue;
                    int lead = (int)Model.L(h, "lead");
                    var t = d.AddMinutes(m - lead);
                    if (t > from && t <= now && !Model.Marked(d, Model.Str(h, "id")))
                    {
                        var sub = Model.HabitSub(h, d);
                        Notify(Model.Str(h, "title"), lead > 0 ? "Daqui a " + lead + " min · " + sub : (sub == "" ? "Hora de fazer." : sub));
                    }
                }
            }
            for (var d = from.Date.AddDays(-1); d <= now.Date.AddDays(1); d = d.AddDays(1))
                foreach (var ev in Model.Events())
                {
                    if (!Model.Bool(ev, "remind", true) || !Model.EventOn(ev, d)) continue;
                    int m = Model.ParseTime(Model.Str(ev, "start"));
                    if (m < 0) continue;
                    int lead = (int)Model.L(ev, "lead");
                    var at = d.AddMinutes(m - lead);
                    if (at > from && at <= now)
                        Notify(Model.Str(ev, "title"), (lead > 0 ? "Começa em " + (lead % 60 == 0 ? lead / 60 + " h" : lead + " min") : "Começando agora") + " · " + Model.EventSub(ev));
                }
            foreach (var t in Model.Tasks())
            {
                if (Model.Bool(t, "done", false) || !Model.Bool(t, "remind", true)) continue;
                var ds = Model.Str(t, "date"); int m = Model.ParseTime(Model.Str(t, "time"));
                if (ds == "" || m < 0) continue;
                var at = Model.ParseDate(ds).AddMinutes(m);
                if (at > from && at <= now) Notify(Model.Str(t, "title"), Model.Str(t, "note") == "" ? "Tarefa de hoje" : Model.Str(t, "note"));
            }
            lastCheck = now;
        }

        static void Notify(string title, string text)
        {
            tray.ShowBalloonTip(10000, title, text == "" ? " " : text, ToolTipIcon.None);
        }

        public static bool IsStartup()
        {
            try { using (var k = Registry.CurrentUser.OpenSubKey(RunKey)) return k != null && k.GetValue("Habitos") != null; }
            catch { return false; }
        }

        static void SetStartupPath()
        {
            try { using (var k = Registry.CurrentUser.OpenSubKey(RunKey, true)) if (k != null) k.SetValue("Habitos", "\"" + Application.ExecutablePath + "\""); } catch { }
        }

        public static void ToggleStartup()
        {
            try
            {
                using (var k = Registry.CurrentUser.OpenSubKey(RunKey, true) ?? Registry.CurrentUser.CreateSubKey(RunKey))
                {
                    if (k.GetValue("Habitos") != null) k.DeleteValue("Habitos", false);
                    else k.SetValue("Habitos", "\"" + Application.ExecutablePath + "\"");
                }
            }
            catch { }
        }
    }
}

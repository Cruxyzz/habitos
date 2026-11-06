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
[assembly: System.Reflection.AssemblyVersion("2.3.0.0")]

namespace Habitos
{
    static class Theme
    {
        public static readonly Color Bg = Color.FromArgb(0x13, 0x18, 0x1C);
        public static readonly Color Surface = Color.FromArgb(0x1B, 0x22, 0x27);
        public static readonly Color Hover = Color.FromArgb(0x21, 0x2A, 0x30);
        public static readonly Color Ink = Color.FromArgb(0xEE, 0xF2, 0xF5);
        public static readonly Color Muted = Color.FromArgb(0x8C, 0x9A, 0xA6);
        public static readonly Color Faint = Color.FromArgb(0x4A, 0x56, 0x60);
        public static readonly Color Line = Color.FromArgb(0x29, 0x32, 0x38);
        public static readonly Color Accent = Color.FromArgb(0x7F, 0xD1, 0xAE);
        public static readonly Color OnAccent = Color.FromArgb(0x0E, 0x1A, 0x15);
        public static readonly Color Late = Color.FromArgb(0xE8, 0xA1, 0x5C);
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
        List<Model.Item> items = new List<Model.Item>();
        List<KeyValuePair<RectangleF, Model.Item>> hits = new List<KeyValuePair<RectangleF, Model.Item>>();
        RectangleF rPlus, rMenu, rPrev, rNext, rHeader;
        string hoverKey;
        public bool Pinned = true; // fixo na área de trabalho (atrás das janelas)
        ContextMenuStrip itemMenu;
        Model.Item menuItem;
        public event Action OpenMenu;
        const int W = 320;

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

            var wa = Screen.PrimaryScreen.WorkingArea;
            int x, y;
            if (int.TryParse(Settings.Get("x", ""), out x) && int.TryParse(Settings.Get("y", ""), out y) && Screen.AllScreens.Any(s => s.WorkingArea.Contains(x + 20, y + 20)))
                Location = new Point(x, y);
            else
                Location = new Point(wa.Right - Width - Theme.P(24), wa.Top + Theme.P(24));

            itemMenu = new ContextMenuStrip();
            itemMenu.Items.Add("Editar…", null, (s, e) => { if (menuItem != null) Editors.Edit(menuItem.Kind, menuItem.Id); });
            itemMenu.Items.Add("Excluir", null, (s, e) => { if (menuItem != null) Editors.Delete(menuItem.Kind, menuItem.Id); });

            Model.Changed += () => { if (IsHandleCreated) BeginInvoke((Action)Reload); };
            Reload();
        }

        protected override CreateParams CreateParams
        {
            get
            {
                var cp = base.CreateParams;
                cp.ExStyle |= 0x80;      // WS_EX_TOOLWINDOW: fora do Alt+Tab
                cp.ClassStyle |= 0x20000; // CS_DROPSHADOW
                return cp;
            }
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            Native.RoundCorners(Handle);
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            if (Pinned) SendToBottom();
        }

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
            // Mantém o widget atrás das outras janelas, como parte da área de trabalho.
            if (m.Msg == 0x0046 && Pinned) // WM_WINDOWPOSCHANGING
            {
                var wp = (Native.WINDOWPOS)Marshal.PtrToStructure(m.LParam, typeof(Native.WINDOWPOS));
                wp.hwndInsertAfter = Native.HWND_BOTTOM;
                wp.flags &= ~0x0004u; // SWP_NOZORDER
                Marshal.StructureToPtr(wp, m.LParam, false);
            }
            base.WndProc(ref m);
        }

        Dictionary<string, object> next;
        const int HeroTop = 76, HeroH = 76, NextH = 52, RowH = 40, FootH = 96;

        int ListTop() { return Theme.P(HeroTop + HeroH + 12) + (next != null ? Theme.P(NextH + 10) : 0) + Theme.P(4); }

        public void Reload()
        {
            if (DateTime.Today > sel && sel == lastToday) sel = DateTime.Today;
            lastToday = DateTime.Today;
            items = Model.ItemsFor(sel);
            items = items.Where(i => !i.Done).Concat(items.Where(i => i.Done)).ToList();
            next = sel == DateTime.Today ? Model.NextTask() : null;
            int h = ListTop() + Math.Max(1, items.Count) * Theme.P(RowH) + Theme.P(FootH) + Theme.P(14);
            int max = Screen.FromControl(this).WorkingArea.Height - Theme.P(40);
            Height = Math.Min(h, max);
            Invalidate();
        }
        DateTime lastToday = DateTime.Today;

        static Color Alpha(Color c, int a) { return Color.FromArgb(a, c.R, c.G, c.B); }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            Theme.Smooth(g);
            g.Clear(Theme.Bg);
            float pad = Theme.P(18), w = Width;
            var today = DateTime.Today;
            bool isToday = sel == today;
            var fmtL = new StringFormat { Trimming = StringTrimming.EllipsisCharacter, FormatFlags = StringFormatFlags.NoWrap, LineAlignment = StringAlignment.Center };

            // Cabeçalho
            using (var f = Theme.F(9f)) using (var b = new SolidBrush(Theme.Muted))
                g.DrawString(isToday ? Model.Greeting() : "Histórico", f, b, new RectangleF(pad - Theme.P(2), Theme.P(14), w - Theme.P(150), Theme.P(18)), fmtL);
            string title = isToday ? "Hoje" : (sel == today.AddDays(-1) ? "Ontem" : Model.DowNames[(int)sel.DayOfWeek]);
            float tw;
            using (var f = Theme.Light(22f)) using (var b = new SolidBrush(Theme.Ink))
            {
                tw = g.MeasureString(title, f).Width;
                g.DrawString(title, f, b, pad - Theme.P(5), Theme.P(30));
            }
            using (var f = Theme.F(9f)) using (var b = new SolidBrush(Theme.Muted))
                g.DrawString(Model.DowNames[(int)sel.DayOfWeek].ToLower() + ", " + Model.ShortDate(sel), f, b, pad - Theme.P(5) + tw, Theme.P(46));
            rHeader = new RectangleF(0, 0, w - Theme.P(130), Theme.P(72));

            float bs = Theme.P(28), by = Theme.P(14);
            rMenu = new RectangleF(w - pad - bs + Theme.P(4), by, bs, bs);
            rPlus = new RectangleF(rMenu.X - bs - Theme.P(2), by, bs, bs);
            rNext = new RectangleF(rPlus.X - bs * 0.8f - Theme.P(8), by, bs * 0.8f, bs);
            rPrev = new RectangleF(rNext.X - bs * 0.8f, by, bs * 0.8f, bs);
            IconButton(g, rPrev, "prev", sel > Model.Start ? Theme.Muted : Theme.Line);
            IconButton(g, rNext, "next", sel < today ? Theme.Muted : Theme.Line);
            IconButton(g, rPlus, "plus", Theme.Ink);
            IconButton(g, rMenu, "menu", Theme.Ink);

            // Cartão de resumo com anel
            var p = Model.Progress(sel);
            float frac = p[1] == 0 ? 0 : (float)p[0] / p[1];
            var hero = new RectangleF(Theme.P(10), Theme.P(HeroTop), w - Theme.P(20), Theme.P(HeroH));
            using (var path = Theme.Round(hero, Theme.P(16))) using (var b = new SolidBrush(Theme.Surface)) g.FillPath(b, path);
            float rs = Theme.P(52), rx = hero.X + Theme.P(14), ry = hero.Y + (hero.Height - rs) / 2;
            using (var pen = new Pen(Theme.Line, Theme.P(6))) g.DrawEllipse(pen, rx, ry, rs, rs);
            if (frac > 0) using (var pen = new Pen(Theme.Accent, Theme.P(6)) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                g.DrawArc(pen, rx, ry, rs, rs, -90, Math.Max(4, 360 * frac));
            using (var f = Theme.F(9.5f, true)) using (var b = new SolidBrush(Theme.Ink))
                g.DrawString((int)Math.Round(frac * 100) + "%", f, b, new RectangleF(rx, ry, rs, rs), new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center });
            int left = p[1] - p[0];
            string headline = p[1] == 0 ? "Dia livre" : left == 0 ? "Tudo feito!" : p[0] == 0 ? "Vamos começar" : "Faltam " + left;
            float hx = rx + rs + Theme.P(16), hw = hero.Right - hx - Theme.P(10);
            int ps = Model.PerfectStreak();
            float ty = hero.Y + (ps > 0 ? Theme.P(10) : Theme.P(18));
            using (var f = Theme.F(12f, true)) using (var b = new SolidBrush(Theme.Ink)) g.DrawString(headline, f, b, new RectangleF(hx, ty, hw, Theme.P(22)), fmtL);
            using (var f = Theme.F(9f)) using (var b = new SolidBrush(Theme.Muted)) g.DrawString(p[0] + " de " + p[1] + " concluídos", f, b, new RectangleF(hx, ty + Theme.P(22), hw, Theme.P(18)), fmtL);
            if (ps > 0) using (var f = Theme.F(8.5f, true)) using (var b = new SolidBrush(Theme.Accent))
                g.DrawString(ps + (ps == 1 ? " dia seguido" : " dias seguidos") + " com tudo feito", f, b, new RectangleF(hx, ty + Theme.P(40), hw, Theme.P(18)), fmtL);

            // A seguir
            float y = Theme.P(HeroTop + HeroH + 12);
            if (next != null)
            {
                var nr = new RectangleF(Theme.P(10), y, w - Theme.P(20), Theme.P(NextH));
                using (var path = Theme.Round(nr, Theme.P(14))) using (var b = new SolidBrush(Alpha(Theme.Accent, 28))) g.FillPath(b, path);
                var d = Model.Str(next, "date"); var tm = Model.Str(next, "time");
                string when = Model.RelDay(d) + (tm != "" ? " · " + tm : "");
                float ww;
                using (var f = Theme.F(8.5f, true)) using (var b = new SolidBrush(Theme.Accent))
                {
                    ww = g.MeasureString(when, f).Width;
                    g.DrawString(when, f, b, new RectangleF(nr.Right - ww - Theme.P(12), nr.Y, ww + 4, nr.Height), fmtL);
                    g.DrawString("A SEGUIR", Theme.F(7.5f, true), b, nr.X + Theme.P(12), nr.Y + Theme.P(7));
                }
                using (var f = Theme.F(10f, true)) using (var b = new SolidBrush(Theme.Ink))
                    g.DrawString(Model.Str(next, "title"), f, b, new RectangleF(nr.X + Theme.P(12), nr.Y + Theme.P(20), nr.Width - ww - Theme.P(36), Theme.P(24)), fmtL);
                y += Theme.P(NextH + 10);
            }
            y += Theme.P(4);

            // Itens
            hits.Clear();
            float rh = Theme.P(RowH);
            float listEnd = Height - Theme.P(FootH) - Theme.P(8);
            if (items.Count == 0)
                using (var f = Theme.F(9.5f)) using (var b = new SolidBrush(Theme.Muted))
                    g.DrawString("Nada pra este dia. Clique em + pra criar.", f, b, pad - Theme.P(2), y + Theme.P(10));
            foreach (var it in items)
            {
                if (y + rh > listEnd + 1) break;
                var row = new RectangleF(Theme.P(8), y, w - Theme.P(16), rh - Theme.P(2));
                string key = it.Kind + it.Id;
                if (key == hoverKey)
                    using (var path = Theme.Round(row, Theme.P(10))) using (var b = new SolidBrush(Theme.Hover)) g.FillPath(b, path);
                float cs = Theme.P(18);
                var col = it.Overdue ? Theme.Late : (it.Color == "" ? Theme.Accent : Model.ColorOf(it.Color));
                DrawColorCheck(g, new RectangleF(pad, y + (rh - cs) / 2 - Theme.P(1), cs, cs), it.Done, col, it.Kind == "t");

                string right = it.Overdue ? "atrasada" : (it.Streak >= 2 ? it.Streak + "d · " : "") + (it.Minutes >= 0 ? Model.HHMM(it.Minutes) : "");
                float rxx = w - pad;
                if (right != "")
                    using (var f = Theme.F(8.5f)) using (var b = new SolidBrush(it.Overdue ? Theme.Late : Theme.Muted))
                    {
                        var sz = g.MeasureString(right, f);
                        rxx = w - pad - sz.Width;
                        g.DrawString(right, f, b, new RectangleF(rxx, y, sz.Width + 2, rh - Theme.P(2)), fmtL);
                    }
                float tx = pad + cs + Theme.P(12);
                using (var f = Theme.F(10f, !it.Done)) using (var fs = new Font(f, FontStyle.Strikeout)) using (var b = new SolidBrush(it.Done ? Theme.Faint : Theme.Ink))
                    g.DrawString(it.Title, it.Done ? fs : f, b, new RectangleF(tx, y, rxx - tx - Theme.P(8), rh - Theme.P(2)), fmtL);
                hits.Add(new KeyValuePair<RectangleF, Model.Item>(row, it));
                y += rh;
            }

            // Últimos 7 dias
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
            using (var f = Theme.F(8.5f, true)) using (var b = new SolidBrush(Theme.Muted))
                g.DrawString("ÚLTIMOS 7 DIAS", f, b, foot.X + Theme.P(8), foot.Y + Theme.P(10));
            using (var f = Theme.F(8.5f, true)) using (var b = new SolidBrush(Theme.Accent))
            {
                string pc = (tot == 0 ? 0 : (int)Math.Round(100.0 * dn / tot)) + "%";
                var sz = g.MeasureString(pc, f);
                g.DrawString(pc, f, b, foot.Right - sz.Width - Theme.P(8), foot.Y + Theme.P(10));
            }
            float barTop = foot.Y + Theme.P(32), barH = Theme.P(34), bw = Theme.P(10);
            for (int i = 0; i < 7; i++)
            {
                float cx = foot.X + Theme.P(8) + (foot.Width - Theme.P(16)) * (i + 0.5f) / 7;
                var bg = new RectangleF(cx - bw / 2, barTop, bw, barH);
                using (var path = Theme.Round(bg, bw / 2)) using (var b = new SolidBrush(Theme.Line)) g.FillPath(b, path);
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
            if (rPlus.Contains(p)) return "btn_plus";
            if (rMenu.Contains(p)) return "btn_menu";
            if (rPrev.Contains(p)) return "btn_prev";
            if (rNext.Contains(p)) return "btn_next";
            foreach (var h in hits) if (h.Key.Contains(p)) return h.Value.Kind + h.Value.Id;
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
            var k = HitKey(e.Location);
            if (e.Button == MouseButtons.Left && k == null)
            {
                // Arrastar o widget pela área vazia.
                Native.ReleaseCapture();
                Native.SendMessage(Handle, 0xA1, new IntPtr(2), IntPtr.Zero);
                Settings.Set("x", Left.ToString());
                Settings.Set("y", Top.ToString());
                if (Pinned) SendToBottom();
            }
        }

        protected override void OnMouseClick(MouseEventArgs e)
        {
            base.OnMouseClick(e);
            var k = HitKey(e.Location);
            if (k == null) return;
            if (e.Button == MouseButtons.Left)
            {
                if (k == "btn_plus") Editors.AskAdd(this, PointToScreen(new Point((int)rPlus.X, (int)rPlus.Bottom)), sel);
                else if (k == "btn_menu") { var h = OpenMenu; if (h != null) h(); }
                else if (k == "btn_prev") { if (sel > Model.Start) { sel = sel.AddDays(-1); Reload(); } }
                else if (k == "btn_next") { if (sel < DateTime.Today) { sel = sel.AddDays(1); Reload(); } }
                else
                {
                    var it = hits.First(h => h.Value.Kind + h.Value.Id == k).Value;
                    Model.Toggle(sel, it.Kind, it.Id);
                    Program.SyncSoon();
                }
            }
            else if (e.Button == MouseButtons.Right && !k.StartsWith("btn_"))
            {
                menuItem = hits.First(h => h.Value.Kind + h.Value.Id == k).Value;
                itemMenu.Show(this, e.Location);
            }
            if (Pinned) SendToBottom();
        }

        protected override void OnMouseDoubleClick(MouseEventArgs e)
        {
            base.OnMouseDoubleClick(e);
            if (rHeader.Contains(e.Location) && HitKey(e.Location) == null) { sel = DateTime.Today; Reload(); }
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
        protected override void OnHandleCreated(EventArgs e) { base.OnHandleCreated(e); Native.DarkTitle(Handle); }

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
            b.FlatAppearance.MouseOverBackColor = primary ? Color.FromArgb(0x95, 0xDC, 0xBD) : Theme.Hover;
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
            m.Items.Add("Novo hábito (repete nos dias escolhidos)", null, (s, e) => EditHabit(null));
            m.Items.Add("Nova tarefa (uma vez só)", null, (s, e) => EditTask(null, sel));
            m.Show(at);
        }

        public static void Edit(string kind, string id)
        {
            if (kind == "t") EditTask(Model.Task(id), DateTime.Today); else EditHabit(Model.Habit(id));
        }

        public static void Delete(string kind, string id)
        {
            var o = kind == "t" ? Model.Task(id) : Model.Habit(id);
            if (o == null) return;
            if (MessageBox.Show("Excluir \"" + Model.Str(o, "title") + "\"?", "Hábitos", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) return;
            o["deleted"] = true;
            if (kind == "t") Model.PutTask(o); else Model.PutHabit(o);
            Program.SyncSoon();
        }

        static Dictionary<string, object> Copy(Dictionary<string, object> o)
        {
            return o == null ? new Dictionary<string, object>() : new Dictionary<string, object>(o);
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
            f.Btn("Novo hábito", 22, 410, 120).Click += (s, e) => EditHabit(null);
            f.Btn("Nova tarefa", 150, 410, 120).Click += (s, e) => EditTask(null, DateTime.Today);
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
            var f = new DarkForm("Ajustes", 440, 330);
            f.Lbl("SINCRONIZAÇÃO COM O CELULAR", 22, 18);
            var url = f.Box(Settings.Get("syncurl", ""), 22, 42, 396);
            var status = new Label { Text = Sync.Status == "" ? (Settings.Get("syncurl", "") == "" ? "Cole o link do script do Google e clique em Salvar." : "Ainda não sincronizado nesta sessão.") : Sync.Status, AutoSize = false, Size = new Size(Theme.P(396), Theme.P(36)), ForeColor = Theme.Muted, Location = new Point(Theme.P(22), Theme.P(76)) };
            f.Controls.Add(status);
            f.Lbl("WIDGET", 22, 124);
            var pin = f.Check("Fixo na área de trabalho (fica atrás das janelas)", w.Pinned, 22, 148);
            var startup = f.Check("Iniciar com o Windows", Program.IsStartup(), 22, 178);
            var save = f.Btn("Salvar e sincronizar", 238, 270, 180, true);
            f.Btn("Fechar", 110, 270, 120).Click += (s, e) => f.Close();
            Action<bool, string> onSync = (ok, msg) => { if (!f.IsDisposed && f.IsHandleCreated) f.BeginInvoke((Action)(() => status.Text = msg)); };
            Sync.Finished += onSync;
            f.FormClosed += (s, e) => Sync.Finished -= onSync;
            save.Click += (s, e) =>
            {
                Settings.Set("syncurl", url.Text.Trim());
                w.SetPinned(pin.Checked);
                if (startup.Checked != Program.IsStartup()) Program.ToggleStartup();
                status.Text = url.Text.Trim() == "" ? "Sincronização desligada." : "Sincronizando…";
                Sync.Run();
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
                var updTimer = new Timer { Interval = 6 * 60 * 60 * 1000 };
                updTimer.Tick += (s, e) => checkUpdate();
                updTimer.Start();
                var firstCheck = new Timer { Interval = 15000 };
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

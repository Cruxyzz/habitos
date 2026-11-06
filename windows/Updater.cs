using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace Habitos
{
    // Confere no GitHub se há versão nova; se houver, baixa, troca o .exe e reabre.
    static class Updater
    {
        public const int Version = 5;
        const string Latest = "https://raw.githubusercontent.com/Cruxyzz/habitos/main/latest.json";
        static bool busy;

        public static void CleanupOld()
        {
            try { var old = Application.ExecutablePath + ".old"; if (File.Exists(old)) File.Delete(old); } catch { }
        }

        public static void CheckAsync(Action<string> onUpdated)
        {
            if (busy) return;
            busy = true;
            System.Threading.ThreadPool.QueueUserWorkItem(_ =>
            {
                try
                {
                    ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
                    string json;
                    using (var wc = new WebClient()) { wc.Encoding = System.Text.Encoding.UTF8; json = wc.DownloadString(Latest + "?t=" + DateTime.Now.Ticks); }
                    var root = new JavaScriptSerializer().DeserializeObject(json) as Dictionary<string, object>;
                    var w = root == null ? null : Model.Obj(root, "windows");
                    if (w == null || Model.L(w, "version") <= Version) return;
                    var exe = Application.ExecutablePath;
                    var tmp = exe + ".new";
                    using (var wc = new WebClient()) wc.DownloadFile(Model.Str(w, "url") + "?t=" + DateTime.Now.Ticks, tmp);
                    if (new FileInfo(tmp).Length < 20000) { File.Delete(tmp); return; }
                    var old = exe + ".old";
                    if (File.Exists(old)) File.Delete(old);
                    File.Move(exe, old);   // o Windows deixa renomear o .exe em uso
                    File.Move(tmp, exe);
                    var notes = Model.Str(w, "notes");
                    if (onUpdated != null) onUpdated(notes);
                }
                catch { }
                finally { busy = false; }
            });
        }

        public static void Restart()
        {
            Process.Start(Application.ExecutablePath, "--updated");
        }
    }
}

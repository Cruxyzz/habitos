package com.yago.habitos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Sincroniza com o script do Google (Apps Script) da conta do usuário. */
public final class Sync {

    public interface Done { void run(boolean ok, String msg); }

    private static boolean running, again;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable debounced = new Runnable() { public void run() { if (appCtx != null) now(appCtx, null); } };
    private static Context appCtx;

    public static String url(Context c) { return Model.prefs(c).getString("sync_url", "").trim(); }

    public static void setUrl(Context c, String u) { Model.prefs(c).edit().putString("sync_url", u.trim()).apply(); }

    public static String status(Context c) { return Model.prefs(c).getString("sync_status", ""); }

    /** Agenda uma sincronização em ~2 s (junta várias alterações seguidas). */
    public static void soon(Context c) {
        appCtx = c.getApplicationContext();
        main.removeCallbacks(debounced);
        main.postDelayed(debounced, 2000);
    }

    /** Sincroniza agora em segundo plano. "done" roda na thread principal. */
    public static void now(final Context ctx, final Done done) {
        final Context c = ctx.getApplicationContext();
        final String u = url(c);
        if (u.isEmpty()) { if (done != null) done.run(false, "Sincronização não configurada"); return; }
        synchronized (Sync.class) {
            if (running) { again = true; if (done != null) done.run(true, "Sincronizando…"); return; }
            running = true;
        }
        new Thread(new Runnable() {
            public void run() {
                boolean ok = false;
                String msg;
                try {
                    String body;
                    synchronized (Model.class) { body = Model.get(c).toString(); }
                    String resp = post(u, body);
                    JSONObject r = new JSONObject(resp);
                    if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "Resposta inválida"));
                    Model.mergeIn(c, r.getJSONObject("state"));
                    ok = true;
                    msg = "Sincronizado às " + new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
                } catch (Exception e) {
                    msg = "Falha ao sincronizar: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
                Model.prefs(c).edit().putString("sync_status", msg).apply();
                final boolean fok = ok;
                final String fmsg = msg;
                boolean rerun;
                synchronized (Sync.class) { running = false; rerun = again; again = false; }
                main.post(new Runnable() {
                    public void run() {
                        WidgetProvider.refreshAll(c);
                        Reminders.scheduleNext(c);
                        if (done != null) done.run(fok, fmsg);
                        if (listener != null) listener.run(fok, fmsg);
                    }
                });
                if (rerun) now(c, null);
            }
        }).start();
    }

    /** Ouvinte da tela principal, para redesenhar quando chegam dados. */
    public static Done listener;

    /** Versão bloqueante, para uso em receivers com goAsync(). */
    public static void blocking(Context ctx) {
        Context c = ctx.getApplicationContext();
        String u = url(c);
        if (u.isEmpty()) return;
        try {
            String body;
            synchronized (Model.class) { body = Model.get(c).toString(); }
            JSONObject r = new JSONObject(post(u, body));
            if (r.optBoolean("ok")) {
                Model.mergeIn(c, r.getJSONObject("state"));
                Model.prefs(c).edit().putString("sync_status", "Sincronizado às "
                        + new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date())).apply();
            }
        } catch (Exception ignored) {
        }
    }

    static String post(String u, String body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setInstanceFollowRedirects(false);
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        OutputStream os = con.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        int code = con.getResponseCode();
        // O Apps Script responde com redirecionamento para o resultado: buscar com GET.
        int hops = 0;
        while ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && hops++ < 5) {
            String loc = con.getHeaderField("Location");
            con.disconnect();
            con = (HttpURLConnection) new URL(loc).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            code = con.getResponseCode();
        }
        if (code != 200) throw new Exception("HTTP " + code);
        InputStream in = con.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        String s = bo.toString("UTF-8");
        if (!s.trim().startsWith("{")) throw new Exception("o link não é do script (confira se o acesso é \"Qualquer pessoa\")");
        return s;
    }

    private Sync() {}
}

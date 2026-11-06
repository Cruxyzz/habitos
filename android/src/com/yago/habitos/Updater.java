package com.yago.habitos;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Confere no GitHub se há versão nova e instala. */
public final class Updater {

    static final String LATEST = "https://raw.githubusercontent.com/Cruxyzz/habitos/main/latest.json";

    public interface Cb { void run(); }

    /** Versão nova disponível (null se não há). */
    public static JSONObject available;
    private static final Handler main = new Handler(Looper.getMainLooper());

    public static int currentVersion(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void check(final Context ctx, final Cb onFound) { check(ctx, onFound, null); }

    /** Confere sempre (sem intervalo). "result" recebe: "nova", "atual" ou a mensagem de erro. */
    public static void check(final Context ctx, final Cb onFound, final Progress result) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject j = new JSONObject(get(LATEST + "?t=" + System.currentTimeMillis()));
                    JSONObject a = j.getJSONObject("android");
                    Model.prefs(c).edit().putLong("upd_check", System.currentTimeMillis()).apply();
                    if (a.getInt("version") > currentVersion(c)) {
                        available = a;
                        main.post(new Runnable() { public void run() {
                            if (onFound != null) onFound.run();
                            if (result != null) result.done(true, "nova");
                        } });
                    } else {
                        available = null;
                        main.post(new Runnable() { public void run() { if (result != null) result.done(true, "atual"); } });
                    }
                } catch (final Exception e) {
                    main.post(new Runnable() { public void run() { if (result != null) result.done(false, "Sem conexão com o GitHub"); } });
                }
            }
        }).start();
    }

    public interface Progress { void done(boolean ok, String msg); }

    public static void downloadAndInstall(final Context ctx, final Progress p) {
        final Context c = ctx.getApplicationContext();
        final JSONObject a = available;
        if (a == null) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    File dir = new File(c.getCacheDir(), "update");
                    dir.mkdirs();
                    File f = new File(dir, "Habitos.apk");
                    download(a.getString("url") + "?t=" + System.currentTimeMillis(), f);
                    Uri uri = Uri.parse("content://" + ApkProvider.AUTH + "/Habitos.apk");
                    final Intent it = new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    main.post(new Runnable() { public void run() {
                        try { c.startActivity(it); if (p != null) p.done(true, ""); }
                        catch (Exception e) { if (p != null) p.done(false, "Não consegui abrir o instalador"); }
                    } });
                } catch (final Exception e) {
                    main.post(new Runnable() { public void run() {
                        if (p != null) p.done(false, "Falha ao baixar: " + e.getMessage());
                    } });
                }
            }
        }).start();
    }

    static HttpURLConnection open(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setInstanceFollowRedirects(true);
        con.setUseCaches(false);
        int code = con.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        return con;
    }

    static String get(String u) throws Exception {
        HttpURLConnection con = open(u);
        InputStream in = con.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    static void download(String u, File f) throws Exception {
        HttpURLConnection con = open(u);
        InputStream in = con.getInputStream();
        FileOutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
    }

    private Updater() {}
}

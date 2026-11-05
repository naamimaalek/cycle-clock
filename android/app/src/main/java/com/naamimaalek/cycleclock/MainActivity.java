package com.naamimaalek.cycleclock;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cycle Clock: the web app in a WebView, plus a bridge that hands alarms to the phone's Clock app.
 *
 * The page is always served from one local address, so settings survive updates. It is either the copy
 * built into this APK or a newer copy downloaded from the website, whichever has the higher version.
 * The app also checks GitHub for a newer APK and hands it to Android's installer when asked.
 */
public class MainActivity extends Activity {
  static final String HOST = "appassets.androidplatform.net";
  static final String PAGE = "https://" + HOST + "/index.html";
  static final String WEB_URL = "https://naamimaalek.github.io/cycle-clock/index.html";
  static final String RELEASES = "https://api.github.com/repos/naamimaalek/cycle-clock/releases/latest";
  static final Pattern VERSION = Pattern.compile("cc-version\" content=\"cycle-clock-v(\\d+)");

  private WebView web;
  private int shownVersion = 0;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    web.setBackgroundColor(Color.parseColor("#EEF1F8"));
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setTextZoom(100);
    web.addJavascriptInterface(new Bridge(), "CycleClockAndroid");
    web.setWebViewClient(new WebViewClient() {
      @Override
      public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri u = request.getUrl();
        if (!HOST.equals(u.getHost())) return null;
        if ("/index.html".equals(u.getPath()) || "/".equals(u.getPath())) {
          byte[] html = bestPage();
          return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(html));
        }
        WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        r.setStatusCodeAndReasonPhrase(404, "Not Found");
        return r;
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri u = request.getUrl();
        if (HOST.equals(u.getHost())) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
        return true;
      }
    });
    setContentView(web);
    web.loadUrl(PAGE);
    fetchNewerPage();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (web != null) web.evaluateJavascript("window.onNativeResume && window.onNativeResume()", null);
  }

  @Override
  public void onBackPressed() {
    web.evaluateJavascript("window.onNativeBack ? window.onNativeBack() : false", value -> {
      if (!"true".equals(value)) finish();
    });
  }

  // ---------- the page: built-in copy or a newer downloaded one ----------

  private File savedPage() { return new File(getFilesDir(), "web/index.html"); }

  static int versionOf(byte[] html) {
    if (html == null) return 0;
    Matcher m = VERSION.matcher(new String(html, 0, Math.min(html.length, 4096), StandardCharsets.UTF_8));
    return m.find() ? Integer.parseInt(m.group(1)) : 0;
  }

  private byte[] bundledPage() {
    try (InputStream in = getAssets().open("index.html")) { return readAll(in); } catch (Exception e) { return null; }
  }

  private byte[] downloadedPage() {
    File f = savedPage();
    if (!f.exists()) return null;
    try (InputStream in = new FileInputStream(f)) { return readAll(in); } catch (Exception e) { return null; }
  }

  private synchronized byte[] bestPage() {
    byte[] a = bundledPage(), b = downloadedPage();
    byte[] best = versionOf(b) > versionOf(a) ? b : a;
    if (best == null) best = "<p>Cycle Clock could not load.</p>".getBytes(StandardCharsets.UTF_8);
    shownVersion = versionOf(best);
    return best;
  }

  /** Quietly download the website's copy; if it is newer, keep it and tell the page it can reload. */
  private void fetchNewerPage() {
    new Thread(() -> {
      try {
        byte[] html = httpGet(WEB_URL + "?t=" + System.currentTimeMillis());
        if (html == null || versionOf(html) <= Math.max(shownVersion, versionOf(bundledPage()))) return;
        String text = new String(html, StandardCharsets.UTF_8);
        if (!text.contains("CycleClockAndroid")) return; // not a page this app can run
        File f = savedPage();
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) { out.write(html); }
        if (!tmp.renameTo(f)) return;
        js("window.onWebUpdate && window.onWebUpdate()");
      } catch (Exception ignored) { }
    }).start();
  }

  // ---------- helpers ----------

  static byte[] readAll(InputStream in) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[16384];
    int n;
    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    return out.toByteArray();
  }

  static byte[] httpGet(String url) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(8000);
    c.setReadTimeout(15000);
    c.setRequestProperty("User-Agent", "CycleClock-Android");
    c.setRequestProperty("Accept", "application/vnd.github+json, text/html, */*");
    try {
      if (c.getResponseCode() != 200) return null;
      try (InputStream in = c.getInputStream()) { return readAll(in); }
    } finally { c.disconnect(); }
  }

  private void js(String code) { runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); }); }

  private int appBuild() {
    try {
      PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
      return pi.versionCode;
    } catch (Exception e) { return 0; }
  }

  private AudioManager audio() { return (AudioManager) getSystemService(AUDIO_SERVICE); }

  /** Methods the page can call as window.CycleClockAndroid.*  */
  class Bridge {
    @JavascriptInterface
    public String setAlarm(int hour, int minute, String label) {
      Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
          .putExtra(AlarmClock.EXTRA_HOUR, hour)
          .putExtra(AlarmClock.EXTRA_MINUTES, minute)
          .putExtra(AlarmClock.EXTRA_MESSAGE, label)
          .putExtra(AlarmClock.EXTRA_VIBRATE, true)
          .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try { startActivity(i); return "ok"; }
      catch (ActivityNotFoundException e) { return "noclock"; }
      catch (Exception e) { return "error"; }
    }

    /** Several alarms at once (reminders): [{"h":22,"m":15,"label":"30 min to bed"}, ...] */
    @JavascriptInterface
    public String setAlarms(String json) {
      try {
        JSONArray list = new JSONArray(json);
        for (int k = 0; k < list.length(); k++) {
          JSONObject a = list.getJSONObject(k);
          String r = setAlarm(a.getInt("h"), a.getInt("m"), a.optString("label", "Cycle Clock"));
          if (!"ok".equals(r)) return r;
          Thread.sleep(450); // let the Clock app finish saving before the next one
        }
        return "ok";
      } catch (Exception e) { return "error"; }
    }

    /** Trigger time (ms) of the next alarm the system knows about, or -1. */
    @JavascriptInterface
    public long nextAlarm() {
      AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
      AlarmManager.AlarmClockInfo next = am == null ? null : am.getNextAlarmClock();
      return next == null ? -1 : next.getTriggerTime();
    }

    /** Alarm volume and whether Do Not Disturb is on "total silence" (the one mode that mutes alarms). */
    @JavascriptInterface
    public String alarmStatus() {
      AudioManager a = audio();
      int vol = a.getStreamVolume(AudioManager.STREAM_ALARM);
      int max = a.getStreamMaxVolume(AudioManager.STREAM_ALARM);
      NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      boolean blocked = nm != null && nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_NONE;
      return "{\"vol\":" + vol + ",\"max\":" + max + ",\"dndBlocks\":" + blocked + "}";
    }

    @JavascriptInterface
    public String raiseAlarmVolume(double fraction) {
      try {
        AudioManager a = audio();
        int max = a.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        int target = (int) Math.ceil(max * Math.max(0, Math.min(1, fraction)));
        if (a.getStreamVolume(AudioManager.STREAM_ALARM) < target)
          a.setStreamVolume(AudioManager.STREAM_ALARM, target, AudioManager.FLAG_SHOW_UI);
        return "ok";
      } catch (Exception e) { return "error"; }
    }

    @JavascriptInterface
    public void openClock() {
      try { startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
      catch (Exception ignored) { }
    }

    @JavascriptInterface
    public void openDnd() {
      try { startActivity(new Intent("android.settings.ZEN_MODE_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
      catch (Exception e) {
        try { startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) { }
      }
    }

    // ---------- updates ----------

    @JavascriptInterface
    public int appBuild() { return MainActivity.this.appBuild(); }

    @JavascriptInterface
    public int webVersion() { return shownVersion; }

    @JavascriptInterface
    public void reload() { runOnUiThread(() -> web.reload()); }

    /** Ask GitHub for the newest APK; answers window.onAppUpdate({current, latest, url}) or ({error:true}). */
    @JavascriptInterface
    public void checkUpdate() {
      new Thread(() -> {
        JSONObject out = new JSONObject();
        try {
          out.put("current", appBuild());
          byte[] body = httpGet(RELEASES);
          if (body == null) throw new Exception("no answer");
          JSONObject rel = new JSONObject(new String(body, StandardCharsets.UTF_8));
          Matcher m = Pattern.compile("build (\\d+)").matcher(rel.optString("name", ""));
          out.put("latest", m.find() ? Integer.parseInt(m.group(1)) : 0);
          JSONArray assets = rel.optJSONArray("assets");
          for (int k = 0; assets != null && k < assets.length(); k++) {
            JSONObject a = assets.getJSONObject(k);
            if (a.optString("name").endsWith(".apk")) out.put("url", a.optString("browser_download_url"));
          }
        } catch (Exception e) {
          try { out.put("error", true); } catch (Exception ignored) { }
        }
        js("window.onAppUpdate && window.onAppUpdate(" + out + ")");
      }).start();
    }

    /** Download the APK, then open Android's installer. Progress: window.onAppUpdateState(state, percent). */
    @JavascriptInterface
    public void installUpdate(String url) {
      new Thread(() -> {
        File apk = new File(getCacheDir(), "update.apk");
        try {
          HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
          c.setConnectTimeout(10000);
          c.setReadTimeout(30000);
          c.setRequestProperty("User-Agent", "CycleClock-Android");
          if (c.getResponseCode() != 200) throw new Exception("http " + c.getResponseCode());
          long total = c.getContentLengthLong(), done = 0;
          int last = -1;
          try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
              out.write(buf, 0, n);
              done += n;
              int pct = total > 0 ? (int) (100 * done / total) : -1;
              if (pct != last) { last = pct; js("window.onAppUpdateState && window.onAppUpdateState('downloading'," + pct + ")"); }
            }
          } finally { c.disconnect(); }
        } catch (Exception e) {
          js("window.onAppUpdateState && window.onAppUpdateState('failed',0)");
          return;
        }
        runOnUiThread(() -> {
          if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            try {
              startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
            web.evaluateJavascript("window.onAppUpdateState && window.onAppUpdateState('permission',100)", null);
            return;
          }
          try {
            Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("content://" + ApkProvider.AUTHORITY + "/update.apk"), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            web.evaluateJavascript("window.onAppUpdateState && window.onAppUpdateState('installing',100)", null);
          } catch (Exception e) {
            web.evaluateJavascript("window.onAppUpdateState && window.onAppUpdateState('failed',0)", null);
          }
        });
      }).start();
    }
  }
}

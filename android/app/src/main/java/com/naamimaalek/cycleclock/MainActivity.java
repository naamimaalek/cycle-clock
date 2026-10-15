package com.naamimaalek.cycleclock;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Cycle Clock: the offline web app in a WebView, plus a bridge that hands alarms to the phone's Clock app. */
public class MainActivity extends Activity {
  private WebView web;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    web.setBackgroundColor(Color.parseColor("#0D0B12"));
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setTextZoom(100);
    web.addJavascriptInterface(new Bridge(), "CycleClockAndroid");
    web.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri u = request.getUrl();
        if ("file".equals(u.getScheme())) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
        return true;
      }
    });
    setContentView(web);
    if (state != null) web.restoreState(state);
    else web.loadUrl("file:///android_asset/index.html");
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    web.saveState(out);
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
        org.json.JSONArray list = new org.json.JSONArray(json);
        for (int k = 0; k < list.length(); k++) {
          org.json.JSONObject a = list.getJSONObject(k);
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
  }
}

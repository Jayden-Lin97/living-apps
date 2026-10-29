package dev.livingapps;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.livingapps.core.*;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Run on a connected device. No paid API, private source app or fabricated compatibility claim. */
@RunWith(AndroidJUnit4.class)
public class AppLanguageDeviceTest {
  private boolean contains(View v, String text) {
    if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return true;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        if (contains(((ViewGroup) v).getChildAt(i), text)) return true;
    return false;
  }

  @Test
  public void bothLanguagesAndRestartKeepBusinessRecords() throws Exception {
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    Locale oldLocale = Locale.getDefault();
    boolean hadLocalChoice = context.getSharedPreferences("language", 0).contains("choice");
    String oldLocalChoice =
        context.getSharedPreferences("language", 0).getString("choice", "system");
    String previous =
        Build.VERSION.SDK_INT >= 33
            ? context.getSystemService(LocaleManager.class).getApplicationLocales().toLanguageTags()
            : context.getSharedPreferences("language", 0).getString("choice", "system");
    MainActivity activity = null;
    String appId = null;
    Store store = null;
    try {
      activity =
          (MainActivity)
              instrumentation.startActivitySync(
                  new Intent(context, MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
      store = ((LivingApplication) activity.getApplication()).store;
      appId =
          store.create(Templates.all().get(1).with("name", "语言验收 / Language check"), "device-test");
      store.saveRecord(
          appId,
          store.app(appId).versionId,
          null,
          J.obj("date", "2026-10-29", "volume", 321, "note", "保存原文 / Keep original"));
      String definition = store.app(appId).spec.toString(),
          record = store.records(appId).get(0).data.toString();
      for (String choice : new String[] {Languages.ENGLISH, Languages.CHINESE, Languages.ENGLISH}) {
        MainActivity current = activity;
        instrumentation.runOnMainSync(() -> Languages.choose(current, choice));
        instrumentation.waitForIdleSync();
        activity =
            (MainActivity)
                instrumentation.startActivitySync(
                    new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();
        MainActivity shown = activity;
        String heading = choice.equals(Languages.CHINESE) ? "我的应用" : "My apps";
        instrumentation.runOnMainSync(
            () -> assertTrue(contains(shown.getWindow().getDecorView(), heading)));
        assertEquals(definition, store.app(appId).spec.toString());
        assertEquals(record, store.records(appId).get(0).data.toString());
      }
    } finally {
      if (store != null && appId != null) store.deleteApp(appId);
      if (activity != null) {
        MainActivity current = activity;
        instrumentation.runOnMainSync(current::finish);
      }
      if (Build.VERSION.SDK_INT >= 33)
        context
            .getSystemService(LocaleManager.class)
            .setApplicationLocales(LocaleList.forLanguageTags(previous));
      android.content.SharedPreferences.Editor restore =
          context.getSharedPreferences("language", 0).edit();
      if (hadLocalChoice) restore.putString("choice", oldLocalChoice);
      else restore.remove("choice");
      restore.commit();
      Locale.setDefault(oldLocale);
      instrumentation.waitForIdleSync();
    }
  }
}

package dev.livingapps;

import android.app.LocaleManager;
import android.content.Context;
import android.os.Build;
import android.os.LocaleList;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Locale;
import org.junit.rules.ExternalResource;

/** Existing Chinese UI scenarios choose their fixture locale, then restore the user's setting. */
public final class ChineseUiRule extends ExternalResource {
  private Context context;
  private Locale oldLocale;
  private String oldChoice, oldTags;

  @Override
  protected void before() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    oldLocale = Locale.getDefault();
    oldChoice = context.getSharedPreferences("language", 0).getString("choice", "system");
    if (Build.VERSION.SDK_INT >= 33) {
      LocaleManager m = context.getSystemService(LocaleManager.class);
      oldTags = m.getApplicationLocales().toLanguageTags();
      m.setApplicationLocales(LocaleList.forLanguageTags("zh-CN"));
    } else context.getSharedPreferences("language", 0).edit().putString("choice", "zh-CN").commit();
    Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  }

  @Override
  protected void after() {
    if (Build.VERSION.SDK_INT >= 33)
      context
          .getSystemService(LocaleManager.class)
          .setApplicationLocales(LocaleList.forLanguageTags(oldTags));
    else context.getSharedPreferences("language", 0).edit().putString("choice", oldChoice).commit();
    Locale.setDefault(oldLocale);
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  }
}

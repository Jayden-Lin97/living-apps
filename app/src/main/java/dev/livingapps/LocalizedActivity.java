package dev.livingapps;

import android.app.Activity;
import android.content.Context;

/** Reattaches a saved screen when an older Android activity resumes after an in-app switch. */
abstract class LocalizedActivity extends Activity {
  private String attachedLanguage;

  @Override
  protected void attachBaseContext(Context base) {
    attachedLanguage = Languages.effective(base).toLanguageTag();
    super.attachBaseContext(Languages.wrap(base));
    android.content.res.Configuration override = new android.content.res.Configuration();
    override.setLocales(android.os.LocaleList.forLanguageTags(attachedLanguage));
    applyOverrideConfiguration(override);
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Compare the attached choice, not the OS resource configuration. Unsupported system
    // locales may still appear there; comparing that value can cause a recreation loop.
    if (!attachedLanguage.equals(Languages.effective(this).toLanguageTag())) recreate();
  }
}

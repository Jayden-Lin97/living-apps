package dev.livingapps;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;

/**
 * On-device preference and configuration only; never rewrites an AppSpec, record or API setting.
 */
final class Languages {
  static final String SYSTEM = "system", CHINESE = "zh-CN", ENGLISH = "en";
  private static final String PREFS = "language", CHOICE = "choice", MIGRATED = "platformMigrated";

  private Languages() {}

  static String requested(Context context) {
    if (Build.VERSION.SDK_INT >= 33) return Platform.requested(context);
    return valid(context.getSharedPreferences(PREFS, 0).getString(CHOICE, SYSTEM));
  }

  private static String valid(String choice) {
    return CHINESE.equals(choice) || ENGLISH.equals(choice) ? choice : SYSTEM;
  }

  static Locale effective(Context context) {
    String choice = requested(context);
    if (choice.equals(CHINESE)) return Locale.SIMPLIFIED_CHINESE;
    if (choice.equals(ENGLISH)) return Locale.ENGLISH;
    LocaleList locales =
        Build.VERSION.SDK_INT >= 33
            ? Platform.system(context)
            : Resources.getSystem().getConfiguration().getLocales();
    for (int i = 0; i < locales.size(); i++) {
      String language = locales.get(i).getLanguage();
      if (language.equals("zh")) return Locale.SIMPLIFIED_CHINESE;
      if (language.equals("en")) return Locale.ENGLISH;
    }
    return Locale.ENGLISH;
  }

  static Context wrap(Context context) {
    Locale locale = effective(context);
    Locale.setDefault(locale);
    Configuration config = new Configuration(context.getResources().getConfiguration());
    config.setLocales(new LocaleList(locale));
    config.setLayoutDirection(locale);
    return context.createConfigurationContext(config);
  }

  static void migrate(Context context) {
    if (Build.VERSION.SDK_INT < 33) return;
    android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
    if (prefs.getBoolean(MIGRATED, false)) return;
    String previous = valid(prefs.getString(CHOICE, SYSTEM));
    if (Platform.requested(context).equals(SYSTEM) && !previous.equals(SYSTEM))
      Platform.set(context, previous);
    prefs.edit().putBoolean(MIGRATED, true).apply();
  }

  static void choose(Activity activity, String choice) {
    if (!valid(choice).equals(choice))
      throw new IllegalArgumentException("Unsupported app language");
    if (requested(activity).equals(choice)) return;
    // Do not replay an old overlay's pending actions across a configuration change.
    if (RebuildAccessibilityService.connected != null) RebuildAccessibilityService.connected.stop();
    activity.getSharedPreferences(PREFS, 0).edit().putString(CHOICE, choice).apply();
    if (Build.VERSION.SDK_INT >= 33) Platform.set(activity, choice);
    else activity.recreate();
  }

  @TargetApi(33)
  private static final class Platform {
    static String requested(Context context) {
      LocaleManager manager = context.getSystemService(LocaleManager.class);
      LocaleList locales =
          manager == null ? LocaleList.getEmptyLocaleList() : manager.getApplicationLocales();
      if (locales.isEmpty()) return SYSTEM;
      return locales.get(0).getLanguage().equals("zh") ? CHINESE : ENGLISH;
    }

    static LocaleList system(Context context) {
      LocaleManager manager = context.getSystemService(LocaleManager.class);
      LocaleList locales =
          manager == null ? LocaleList.getEmptyLocaleList() : manager.getSystemLocales();
      return locales.isEmpty() ? Resources.getSystem().getConfiguration().getLocales() : locales;
    }

    static void set(Context context, String choice) {
      LocaleManager manager = context.getSystemService(LocaleManager.class);
      if (manager == null) throw new IllegalStateException("App language service unavailable");
      manager.setApplicationLocales(
          choice.equals(SYSTEM)
              ? LocaleList.getEmptyLocaleList()
              : LocaleList.forLanguageTags(choice));
    }
  }
}

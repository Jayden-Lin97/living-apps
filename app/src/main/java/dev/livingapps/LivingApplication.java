package dev.livingapps;

import android.app.Application;
import android.app.job.*;
import android.content.*;

public final class LivingApplication extends Application {
  public Store store;
  private String uiLanguage;

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(Languages.wrap(base));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Languages.migrate(this);
    uiLanguage = Languages.effective(this).getLanguage();
    store = new Store(this);
    schedule();
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration configuration) {
    super.onConfigurationChanged(configuration);
    java.util.Locale locale = Languages.effective(this);
    boolean changed = uiLanguage != null && !uiLanguage.equals(locale.getLanguage());
    uiLanguage = locale.getLanguage();
    java.util.Locale.setDefault(locale);
    if (changed && RebuildAccessibilityService.connected != null)
      RebuildAccessibilityService.connected.stop();
  }

  private void schedule() {
    JobScheduler jobs = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
    if (jobs != null)
      jobs.schedule(
          new JobInfo.Builder(1701, new ComponentName(this, ReflectionJobService.class))
              .setPeriodic(6 * 60 * 60 * 1000L)
              .setRequiresBatteryNotLow(true)
              .setPersisted(true)
              .build());
  }

  public boolean automatic() {
    return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("automatic", false);
  }
}

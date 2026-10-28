package dev.livingapps;

import android.app.Application;
import android.app.job.*;
import android.content.*;

public final class LivingApplication extends Application {
  public Store store;

  @Override
  public void onCreate() {
    super.onCreate();
    store = new Store(this);
    schedule();
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

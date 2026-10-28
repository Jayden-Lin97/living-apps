package dev.livingapps;

import android.app.job.*;
import java.util.concurrent.*;

public final class ReflectionJobService extends JobService {
  private ExecutorService executor;
  private Future<?> work;

  @Override
  public boolean onStartJob(JobParameters params) {
    executor = Executors.newSingleThreadExecutor();
    work =
        executor.submit(
            () -> {
              try {
                LivingApplication app = (LivingApplication) getApplication();
                app.store.reflect(app.automatic());
              } finally {
                jobFinished(params, false);
                executor.shutdown();
              }
            });
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    if (work != null) work.cancel(true);
    if (executor != null) executor.shutdownNow();
    return true;
  }
}

package dev.livingapps;

import static dev.livingapps.core.Messages.tr;

import android.app.*;
import android.os.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Dedicated local-only data workflow: dry run, checked backup, atomic commit, copy recovery. */
final class MigrationControls implements AutoCloseable {
  private final Activity activity;
  private final Ui ui;
  private final Store store;
  private final Supplier<EditSession> session;
  private final Runnable committed;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private boolean closed;
  private ProgressDialog progressDialog;

  MigrationControls(
      Activity activity, Ui ui, Store store, Supplier<EditSession> session, Runnable committed) {
    this.activity = activity;
    this.ui = ui;
    this.store = store;
    this.session = session;
    this.committed = committed;
  }

  private void error(Exception error) {
    if (closed || activity.isFinishing()) return;
    new AlertDialog.Builder(activity)
        .setTitle(tr("转换尚未完成"))
        .setMessage(error.getMessage())
        .setPositiveButton(tr("知道了"), null)
        .show();
  }

  private <T> void local(String message, Callable<T> task, Consumer<T> next) {
    ProgressDialog progress = new ProgressDialog(activity);
    progressDialog = progress;
    progress.setMessage(message);
    progress.setCancelable(false);
    progress.show();
    worker.submit(
        () -> {
          try {
            T value = task.call();
            main.post(
                () -> {
                  progress.dismiss();
                  if (!closed && !activity.isFinishing()) next.accept(value);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  progress.dismiss();
                  error(e);
                });
          }
        });
  }

  void open() {
    EditSession draft = session.get();
    if (draft.changed() || !store.app(draft.appId).versionId.equals(draft.baseVersion)) {
      error(new IllegalStateException(tr("请先保存普通草稿或完成基线合并，再进入独立的数据转换流程")));
      return;
    }
    List<AppSpec.Field> fields = draft.spec.fields();
    String[] labels = new String[fields.size()];
    for (int i = 0; i < labels.length; i++) labels[i] = fields.get(i).label;
    new AlertDialog.Builder(activity)
        .setTitle(tr("选择要转换的数据项"))
        .setItems(labels, (d, i) -> choose(fields.get(i).id))
        .setNegativeButton(tr("返回编辑"), null)
        .show();
  }

  private void choose(String fieldId) {
    AppSpec spec = session.get().spec;
    AppSpec.Field field = spec.field(fieldId);
    List<String> labels = new ArrayList<>();
    List<Runnable> actions = new ArrayList<>();
    for (String target : MigrationPlan.targets(field.type)) {
      labels.add(tr("转换为") + typeName(target));
      actions.add(() -> prepare(MigrationPlan.type(spec, fieldId, target)));
    }
    if (field.type.equals("number") && spec.metricField().equals(fieldId)) {
      labels.add(tr("换算数值与单位"));
      actions.add(() -> scale(fieldId));
    }
    if (field.type.equals("choice")) {
      labels.add(tr("映射选项内容"));
      actions.add(() -> mapChoices(fieldId));
    }
    new AlertDialog.Builder(activity)
        .setTitle(field.label + " · " + typeName(field.type))
        .setItems(
            labels.toArray(new String[0]),
            (d, i) -> {
              try {
                actions.get(i).run();
              } catch (Exception e) {
                error(e);
              }
            })
        .setNegativeButton(tr("返回"), null)
        .show();
  }

  private String typeName(String type) {
    switch (type) {
      case "number":
        return tr("数字");
      case "boolean":
        return tr("开关");
      case "date":
        return tr("日期");
      case "choice":
        return tr("选项");
      default:
        return tr("文字");
    }
  }

  private LinearLayout body() {
    LinearLayout body = ui.col();
    body.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(12));
    return body;
  }

  private ScrollView scroll(LinearLayout body) {
    ScrollView view = new ScrollView(activity);
    view.addView(body);
    return view;
  }

  private void scale(String fieldId) {
    LinearLayout body = body();
    body.addView(ui.body(tr("换算后数值 = 原数值 × 倍率。默认值、固定动作预设也一同换算。不会自动猜测单位关系。")));
    ui.space(body, 12);
    EditText factor = ui.input(tr("倍率，如元转分填写 100"), false), unit = ui.input(tr("换算后单位，如分"), false);
    factor.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    factor.setTag("migration-factor");
    unit.setTag("migration-unit");
    body.addView(factor);
    ui.space(body, 12);
    body.addView(unit);
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(tr("换算累计数值"))
            .setView(scroll(body))
            .setNegativeButton(tr("取消"), null)
            .setPositiveButton(tr("先演算，不写入"), null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        MigrationPlan plan =
                            MigrationPlan.scale(
                                session.get().spec,
                                fieldId,
                                Double.parseDouble(factor.getText().toString()),
                                unit.getText().toString().trim());
                        dialog.dismiss();
                        prepare(plan);
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    dialog.show();
    ui.styleDialog(dialog);
  }

  private void mapChoices(String fieldId) {
    LinearLayout body = body();
    body.addView(ui.body(tr("为每个旧选项填写目标内容。相同目标会合并选项；没有匹配的历史值会阻止转换。")));
    Map<String, EditText> inputs = new LinkedHashMap<>();
    for (String option : session.get().spec.field(fieldId).options) {
      ui.space(body, 10);
      body.addView(ui.text(option + " →", 12, Ui.INK, true));
      EditText input = ui.input(tr("目标选项"), false);
      input.setText(option);
      body.addView(input);
      inputs.put(option, input);
    }
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(tr("映射选项"))
            .setView(scroll(body))
            .setNegativeButton(tr("取消"), null)
            .setPositiveButton(tr("先演算，不写入"), null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        Map<String, String> values = new LinkedHashMap<>();
                        for (Map.Entry<String, EditText> entry : inputs.entrySet())
                          values.put(entry.getKey(), entry.getValue().getText().toString().trim());
                        MigrationPlan plan =
                            MigrationPlan.choices(session.get().spec, fieldId, values);
                        dialog.dismiss();
                        prepare(plan);
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    dialog.show();
    ui.styleDialog(dialog);
  }

  private void prepare(MigrationPlan plan) {
    EditSession draft = session.get();
    local(
        tr("正在本机演算转换，不写入记录"),
        () -> store.prepareMigration(draft.appId, draft.baseVersion, plan),
        prepared -> {
          AlertDialog dialog =
              new AlertDialog.Builder(activity)
                  .setTitle(tr("检查数据转换"))
                  .setMessage(
                      plan.description
                          + "\n\n"
                          + prepared.result.summary()
                          + tr(
                              "\n\n"
                                  + "缺项继续使用已换算的默认值；空值保持为空。其他记录项、稳定 ID 与记录时间保留。\n\n"
                                  + "将校验转换前备份后原子提交。试用副本重新开始，备份可恢复为独立应用副本。旧 UI 版本不能跨转换直接恢复。")
                          + (prepared.result.allowed()
                              ? ""
                              : tr("\n\n存在失败记录，不会部分转换。请先在正式记录中处理无效值，再重新演算。")))
                  .setNegativeButton(tr("返回，不转换"), null)
                  .setPositiveButton(tr("备份并转换"), null)
                  .create();
          dialog.setOnShowListener(
              d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(prepared.result.allowed());
                dialog
                    .getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(
                        v -> {
                          dialog.dismiss();
                          local(
                              tr("正在备份并事务提交"),
                              () -> store.commitMigration(prepared),
                              result -> committed.run());
                        });
              });
          dialog.show();
          ui.styleDialog(dialog);
        });
  }

  void backups() {
    String appId = session.get().appId;
    List<Store.MigrationBackup> backups = store.migrationBackups(appId);
    if (backups.isEmpty()) {
      new AlertDialog.Builder(activity)
          .setTitle(tr("转换前备份"))
          .setMessage(tr("完成数据转换后，会在本机保存校验过的转换前备份。"))
          .setPositiveButton(tr("知道了"), null)
          .show();
      return;
    }
    String[] labels = new String[backups.size()];
    for (int i = 0; i < labels.length; i++)
      labels[i] =
          java.time.Instant.ofEpochMilli(backups.get(i).created)
                  .atZone(java.time.ZoneId.systemDefault())
                  .toLocalDate()
              + " · "
              + backups.get(i).reason;
    new AlertDialog.Builder(activity)
        .setTitle(tr("选择转换前备份"))
        .setItems(
            labels,
            (d, i) -> {
              new AlertDialog.Builder(activity)
                  .setTitle(tr("恢复为独立应用副本？"))
                  .setMessage(tr("会校验备份并创建包含转换前记录的应用副本。当前应用与此后新增的记录全部保留。"))
                  .setNegativeButton(tr("取消"), null)
                  .setPositiveButton(
                      tr("创建副本"),
                      (a, b) -> {
                        local(
                            tr("正在校验并创建恢复副本"),
                            () -> store.recoverMigrationCopy(appId, backups.get(i).id),
                            result -> {
                              new AlertDialog.Builder(activity)
                                  .setTitle(tr("副本已创建"))
                                  .setMessage(tr("返回应用首页即可找到“转换前副本”。当前草稿与记录保留。"))
                                  .setPositiveButton(tr("知道了"), null)
                                  .show();
                            });
                      })
                  .show();
            })
        .setNegativeButton(tr("返回"), null)
        .show();
  }

  @Override
  public void close() {
    closed = true;
    if (progressDialog != null) progressDialog.dismiss();
    worker.shutdown();
    main.removeCallbacksAndMessages(null);
  }
}

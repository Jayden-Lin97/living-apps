package dev.livingapps;

import android.app.*;
import android.view.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.util.*;
import java.util.function.*;
import org.json.*;

/** Explicitly configured local record presets; adding a button never executes it. */
final class ActionControls {
  private final Activity activity;
  private final Ui ui;
  private final EditSession session;
  private final EditorControls.Apply apply;

  ActionControls(Activity activity, Ui ui, EditSession session, EditorControls.Apply apply) {
    this.activity = activity;
    this.ui = ui;
    this.session = session;
    this.apply = apply;
  }

  private void error(Exception e) {
    new AlertDialog.Builder(activity)
        .setTitle("动作尚未配置完整")
        .setMessage(e.getMessage())
        .setPositiveButton("知道了", null)
        .show();
  }

  void manage() {
    List<ActionSpec> actions = session.spec.actions();
    List<String> labels = new ArrayList<>();
    for (ActionSpec action : actions) labels.add(action.label);
    labels.add("＋ 添加固定动作");
    new AlertDialog.Builder(activity)
        .setTitle("点击后新增一条记录")
        .setItems(
            labels.toArray(new String[0]),
            (d, i) -> edit(i < actions.size() ? actions.get(i).id : null))
        .setNegativeButton("返回", null)
        .show();
  }

  void panel(LinearLayout parent, String id) {
    parent.addView(ui.body("该按钮只会在使用或试用时，由你点击后新增一条记录。配置本身不会执行。"));
    ui.space(parent, 12);
    parent.addView(ui.button("配置固定动作", false, () -> edit(id)));
    ui.space(parent, 8);
    parent.addView(
        ui.button(
            "移除这个按钮",
            false,
            () -> {
              try {
                apply.run(withAction(id, null), "移除固定动作按钮；记录保留");
                session.selected = "app";
              } catch (Exception e) {
                error(e);
              }
            }));
  }

  private AppSpec withAction(String id, JSONObject candidate) {
    JSONObject root = session.spec.json();
    JSONArray rows = new JSONArray(), old = root.optJSONArray("actions");
    boolean replaced = false;
    for (int i = 0; old != null && i < old.length(); i++) {
      JSONObject action = old.optJSONObject(i);
      if (action.optString("id").equals(id)) {
        if (candidate != null) rows.put(candidate);
        replaced = true;
      } else rows.put(action);
    }
    if (candidate != null && !replaced) rows.put(candidate);
    J.put(root, "actions", rows);
    J.put(root, "schemaVersion", 2);
    return AppSpec.parse(root.toString());
  }

  private void edit(String id) {
    JSONObject existing = null;
    JSONArray rows = session.spec.json().optJSONArray("actions");
    for (int i = 0; rows != null && i < rows.length(); i++)
      if (rows.optJSONObject(i).optString("id").equals(id)) existing = rows.optJSONObject(i);
    JSONObject values = existing == null ? J.obj() : existing.optJSONObject("values");
    LinearLayout body = ui.col();
    body.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12));
    body.addView(ui.body("能力：你点击按钮后，在本机新增一条配置好的记录。无脚本、无联网、无自动后台执行。"));
    ui.space(body, 12);
    body.addView(ui.text("按钮名称", 13, Ui.INK, true));
    EditText label = ui.input("如：记一杯水", false);
    label.setText(existing == null ? "" : existing.optString("label"));
    label.setTag("action-label");
    body.addView(label);
    ui.space(body, 16);
    RuntimeRenderer renderer = new RuntimeRenderer(activity, ui);
    Map<String, Supplier<Object>> readers = new LinkedHashMap<>();
    Map<String, Switch> dates = new HashMap<>();
    for (AppSpec.Field field : session.spec.fields()) {
      Object value = values.has(field.id) ? values.opt(field.id) : field.defaultValue;
      boolean today = field.type.equals("date") && (value == null || "$today".equals(value));
      readers.put(
          field.id,
          renderer.field(
              body,
              field,
              "$today".equals(value) ? java.time.LocalDate.now().toString() : value,
              true));
      if (field.type.equals("date")) {
        Switch dynamic = ui.toggle("“" + field.label + "”随点击当天变化", today);
        dates.put(field.id, dynamic);
        body.addView(dynamic);
        ui.space(body, 10);
      }
    }
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(body);
    final String actionId =
        existing == null
            ? "action_" + UUID.randomUUID().toString().substring(0, 8)
            : existing.optString("id");
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(existing == null ? "添加固定动作" : "配置固定动作")
            .setView(scroll)
            .setNegativeButton("稍后再改", null)
            .setPositiveButton("加入草稿，不执行", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        JSONObject configured = J.obj();
                        for (Map.Entry<String, Supplier<Object>> reader : readers.entrySet()) {
                          Object value =
                              dates.containsKey(reader.getKey())
                                      && dates.get(reader.getKey()).isChecked()
                                  ? "$today"
                                  : reader.getValue().get();
                          J.put(
                              configured, reader.getKey(), value == null ? JSONObject.NULL : value);
                        }
                        JSONObject action =
                            J.obj(
                                "id",
                                actionId,
                                "label",
                                label.getText().toString().trim(),
                                "kind",
                                "add_record",
                                "values",
                                configured);
                        apply.run(
                            withAction(actionId, action), "配置固定动作：" + action.optString("label"));
                        dialog.dismiss();
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    dialog.show();
    ui.styleDialog(dialog);
  }
}

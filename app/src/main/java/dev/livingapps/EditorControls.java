package dev.livingapps;

import static dev.livingapps.core.Messages.tr;

import android.app.*;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.util.*;
import java.util.function.*;
import org.json.*;

/** Local property controls use the same draft transaction path as model proposals. */
final class EditorControls {
  interface Apply {
    void run(AppSpec next, String reason);
  }

  private final Activity activity;
  private final Ui ui;
  private final EditSession session;
  private final Runnable save, selectApp;
  private final Apply apply;

  EditorControls(
      Activity activity,
      Ui ui,
      EditSession session,
      Runnable save,
      Runnable selectApp,
      Apply apply) {
    this.activity = activity;
    this.ui = ui;
    this.session = session;
    this.save = save;
    this.selectApp = selectApp;
    this.apply = apply;
  }

  private void error(Exception error) {
    new AlertDialog.Builder(activity)
        .setTitle(tr("请调整这项设置"))
        .setMessage(error.getMessage())
        .setPositiveButton(tr("知道了"), null)
        .show();
  }

  private void execute(Supplier<AppSpec> next, String reason) {
    try {
      apply.run(next.get(), reason);
    } catch (Exception error) {
      error(error);
    }
  }

  private EditText input(String key, String initial, String hint, boolean multiline) {
    EditText input = ui.input(hint, multiline);
    input.setText(session.controls.optString(key, initial));
    input.setTag(key);
    input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4000)});
    input.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            J.put(session.controls, key, s.toString());
            save.run();
          }

          public void afterTextChanged(Editable e) {}
        });
    return input;
  }

  private LinearLayout content() {
    LinearLayout c = ui.col();
    c.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12));
    return c;
  }

  private ScrollView scroll(LinearLayout content) {
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(content);
    return scroll;
  }

  private void label(LinearLayout parent, String label) {
    ui.space(parent, 12);
    parent.addView(ui.text(label, 12, Ui.INK, true));
    ui.space(parent, 7);
  }

  private void clearInputs(String prefix) {
    List<String> keys = new ArrayList<>();
    session
        .controls
        .keys()
        .forEachRemaining(
            k -> {
              if (k.startsWith(prefix)) keys.add(k);
            });
    for (String key : keys) session.controls.remove(key);
    save.run();
  }

  public void build(LinearLayout parent) {
    String selected = session.selected;
    if (selected.startsWith("action:")) {
      new ActionControls(activity, ui, session, apply).panel(parent, selected.substring(7));
      return;
    }
    if (selected.startsWith("field:") && session.spec.field(selected.substring(6)) != null)
      fieldPanel(parent, session.spec.field(selected.substring(6)));
    else appPanel(parent);
  }

  private void fieldPanel(LinearLayout parent, AppSpec.Field field) {
    parent.addView(ui.text(tr("显示名称"), 12, Ui.INK, true));
    ui.space(parent, 6);
    LinearLayout row = ui.row();
    String key = "rename:" + field.id;
    EditText name = input(key, field.label, tr("记录项名称"), false);
    name.setSingleLine(true);
    row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    ui.gap(row, 8);
    row.addView(
        ui.button(
            tr("应用"),
            true,
            () ->
                execute(
                    () -> {
                      AppSpec next =
                          session.field(field.id, "label", name.getText().toString().trim());
                      session.controls.remove(key);
                      return next;
                    },
                    tr("调整“") + field.label + tr("”显示名称"))));
    parent.addView(row);
    ui.space(parent, 8);
    LinearLayout moves = ui.row();
    ui.weighted(
        moves,
        ui.button(tr("上移"), false, () -> execute(() -> session.move(field.id, -1), tr("调整记录项顺序"))));
    ui.weighted(
        moves,
        ui.button(tr("下移"), false, () -> execute(() -> session.move(field.id, 1), tr("调整记录项顺序"))));
    ui.weighted(moves, ui.button(tr("更多设置"), false, () -> fieldDialog(field.id, false)));
    parent.addView(moves);
    ui.space(parent, 8);
    parent.addView(ui.button(tr("修改整个应用"), false, selectApp));
  }

  private void appPanel(LinearLayout parent) {
    String key = "app:name";
    parent.addView(ui.text(tr("应用名称"), 12, Ui.INK, true));
    ui.space(parent, 6);
    EditText name = input(key, session.spec.name(), tr("应用名称"), false);
    LinearLayout row = ui.row();
    row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    ui.gap(row, 8);
    row.addView(
        ui.button(
            tr("应用"),
            true,
            () ->
                execute(
                    () -> {
                      AppSpec next = session.spec.with("name", name.getText().toString().trim());
                      session.controls.remove(key);
                      return next;
                    },
                    tr("调整应用名称"))));
    parent.addView(row);
    ui.space(parent, 8);
    LinearLayout options = ui.row();
    ui.weighted(options, ui.button(tr("主题与图标"), false, this::appearance));
    ui.weighted(options, ui.button(tr("首页与统计"), false, this::navigation));
    parent.addView(options);
    ui.space(parent, 8);
    Switch compact = ui.toggle(tr("紧凑记录卡片"), session.spec.compact());
    compact.setTag("editor-compact");
    compact.setOnCheckedChangeListener(
        (v, checked) -> execute(() -> session.spec.with("compact", checked), tr("调整记录布局")));
    parent.addView(compact);
    Switch quick = ui.toggle(tr("快速记录入口"), session.spec.quickAdd());
    quick.setOnCheckedChangeListener(
        (v, checked) -> execute(() -> session.spec.with("quickAdd", checked), tr("调整快速记录入口")));
    parent.addView(quick);
    parent.addView(ui.action(tr("添加记录项"), "plus", false, this::addField));
    ui.space(parent, 8);
    parent.addView(
        ui.action(
            tr("固定动作 · 点击新增记录"),
            "plus",
            false,
            () -> new ActionControls(activity, ui, session, apply).manage()));
  }

  public void addField() {
    fieldDialog("new", true);
  }

  private void fieldDialog(String id, boolean adding) {
    AppSpec.Field old = adding ? null : session.spec.field(id);
    if (!adding && old == null) return;
    String prefix = adding ? "new:" : "config:" + id + ":";
    LinearLayout body = content();
    body.addView(
        ui.body(adding ? tr("新增项会加入草稿；默认选填，不改变旧记录。") : tr("显示名称和填写方式可以调整；已发布的类型、标识和旧选项会保留。")));
    label(body, tr("名称"));
    EditText label = input(prefix + "label", adding ? "" : old.label, tr("如：备注、心情"), false);
    body.addView(label);
    String[] types = {tr("文本"), tr("数字"), tr("日期"), tr("选项"), tr("开关")},
        ids = {"text", "number", "date", "choice", "boolean"};
    Spinner type =
        ui.spinner(
            types,
            adding
                ? session.controls.optInt(prefix + "type", 0)
                : Arrays.asList(ids).indexOf(old.type));
    if (adding) {
      label(body, tr("记录内容类型"));
      body.addView(type);
    } else {
      ui.space(body, 9);
      body.addView(ui.tag(tr("类型：") + types[Arrays.asList(ids).indexOf(old.type)]));
    }
    label(body, tr("填写提示"));
    EditText placeholder =
        input(prefix + "placeholder", adding ? "" : old.placeholder, tr("选填"), false);
    body.addView(placeholder);
    Switch required =
        ui.toggle(
            tr("必填"),
            session.controls.has(prefix + "required")
                ? session.controls.optBoolean(prefix + "required")
                : !adding && old.required);
    required.setOnCheckedChangeListener(
        (v, checked) -> {
          J.put(session.controls, prefix + "required", checked);
          save.run();
        });
    body.addView(required);
    label(body, tr("分组名称"));
    EditText group = input(prefix + "group", adding ? "" : old.group, tr("选填，如：基本信息"), false);
    body.addView(group);
    Switch hidden =
        ui.toggle(
            tr("在表单和记录列表中隐藏"),
            session.controls.has(prefix + "hidden")
                ? session.controls.optBoolean(prefix + "hidden")
                : !adding && old.hidden);
    hidden.setOnCheckedChangeListener(
        (v, c) -> {
          J.put(session.controls, prefix + "hidden", c);
          save.run();
        });
    body.addView(hidden);
    body.addView(ui.body(tr("隐藏保留原数据；标题项不能隐藏，必填项需有合法默认值。显式统计仍可引用它。")));
    LinearLayout dynamic = ui.col();
    body.addView(dynamic);
    final EditText[] optionBox = {null}, defaultBox = {null};
    final Switch[] hasDefault = {null};
    Runnable render =
        () -> {
          dynamic.removeAllViews();
          String selected = ids[type.getSelectedItemPosition()];
          if (selected.equals("choice")) {
            label(dynamic, tr("选项，每行一个；旧选项不能删除"));
            optionBox[0] =
                input(
                    prefix + "options",
                    adding ? "" : String.join("\n", old.options),
                    tr("如：开心\n平静\n疲惫"),
                    true);
            optionBox[0].setMinLines(3);
            dynamic.addView(optionBox[0]);
          } else optionBox[0] = null;
          boolean enabled =
              session.controls.has(prefix + "hasDefault")
                  ? session.controls.optBoolean(prefix + "hasDefault")
                  : !adding && old.defaultValue != null;
          hasDefault[0] = ui.toggle(tr("设置默认值"), enabled);
          hasDefault[0].setOnCheckedChangeListener(
              (v, c) -> {
                J.put(session.controls, prefix + "hasDefault", c);
                save.run();
              });
          dynamic.addView(hasDefault[0]);
          String hint =
              selected.equals("boolean")
                  ? tr("true 表示是，false 表示否")
                  : selected.equals("date") ? "YYYY-MM-DD" : tr("缺少该项时使用的值");
          defaultBox[0] =
              input(
                  prefix + "default",
                  adding || old.defaultValue == null ? "" : String.valueOf(old.defaultValue),
                  hint,
                  false);
          dynamic.addView(defaultBox[0]);
          ui.space(dynamic, 8);
          dynamic.addView(ui.body(tr("默认值只用于缺失项，不覆盖用户主动清空的值。新增必填项需有合法默认值，保存前会检查旧记录。")));
        };
    type.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int pos, long row) {
            J.put(session.controls, prefix + "type", pos);
            save.run();
            render.run();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    render.run();
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(adding ? tr("添加记录项") : tr("记录项设置"))
            .setView(scroll(body))
            .setNegativeButton(tr("稍后再改"), null)
            .setPositiveButton(tr("应用到草稿"), null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        String selected = ids[type.getSelectedItemPosition()];
                        JSONObject next =
                            adding
                                ? J.obj(
                                    "id",
                                    "field_" + UUID.randomUUID().toString().substring(0, 8),
                                    "type",
                                    selected)
                                : EditChanges.fields(session.spec.json()).get(id);
                        J.put(next, "label", label.getText().toString().trim());
                        J.put(next, "placeholder", placeholder.getText().toString().trim());
                        J.put(next, "required", required.isChecked());
                        String groupName = group.getText().toString().trim();
                        boolean extended =
                            session.spec.json().optInt("schemaVersion") == 2
                                || hidden.isChecked()
                                || !groupName.isEmpty();
                        if (extended) {
                          J.put(next, "group", groupName);
                          J.put(next, "hidden", hidden.isChecked());
                        }
                        if (selected.equals("choice")) {
                          JSONArray options = new JSONArray();
                          for (String option : optionBox[0].getText().toString().split("\n"))
                            if (!option.trim().isEmpty()) options.put(option.trim());
                          J.put(next, "options", options);
                        } else next.remove("options");
                        if (hasDefault[0].isChecked())
                          J.put(
                              next,
                              "defaultValue",
                              parseDefault(selected, defaultBox[0].getText().toString().trim()));
                        else next.remove("defaultValue");
                        JSONObject raw = session.spec.json();
                        if (extended) J.put(raw, "schemaVersion", 2);
                        JSONArray fields = new JSONArray();
                        for (JSONObject field : EditChanges.fields(raw).values())
                          fields.put(!adding && field.optString("id").equals(id) ? next : field);
                        if (adding) fields.put(next);
                        J.put(raw, "fields", fields);
                        apply.run(
                            AppSpec.parse(raw.toString()),
                            adding ? tr("新增记录项：") + next.optString("label") : tr("调整记录项设置"));
                        List<String> remove = new ArrayList<>();
                        session
                            .controls
                            .keys()
                            .forEachRemaining(
                                k -> {
                                  if (k.startsWith(prefix)) remove.add(k);
                                });
                        for (String key : remove) session.controls.remove(key);
                        save.run();
                        dialog.dismiss();
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    dialog.show();
    ui.styleDialog(dialog);
  }

  private Object parseDefault(String type, String value) {
    if (type.equals("number")) {
      try {
        double n = Double.parseDouble(value);
        if (Double.isFinite(n)) return n;
      } catch (Exception ignored) {
      }
      throw new IllegalArgumentException(tr("默认值须为有限数字"));
    }
    if (type.equals("boolean")) {
      if (value.equals("true") || value.equals("是")) return true;
      if (value.equals("false") || value.equals("否")) return false;
      throw new IllegalArgumentException(tr("开关默认值请填写 true 或 false"));
    }
    return value;
  }

  private void appearance() {
    LinearLayout body = content();
    label(body, tr("说明"));
    EditText description =
        input("appearance:description", session.spec.description(), tr("应用说明"), true);
    description.setMinLines(2);
    body.addView(description);
    label(body, tr("图标符号"));
    EditText symbol = input("appearance:icon", session.spec.icon(), tr("一个符号或 emoji"), false);
    body.addView(symbol);
    label(body, tr("主题颜色"));
    EditText color = input("appearance:accent", session.spec.accent(), "#1C3F35", false);
    body.addView(color);
    ui.space(body, 8);
    LinearLayout palette = ui.row();
    for (String hex : new String[] {"#176B56", "#356E9D", "#967245", "#965F77"}) {
      Button choice = ui.button("●", false, () -> color.setText(hex));
      choice.setTextColor(Color.parseColor(hex));
      ui.weighted(palette, choice);
    }
    body.addView(palette);
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(tr("主题与图标"))
            .setView(scroll(body))
            .setNegativeButton(tr("稍后再改"), null)
            .setPositiveButton(tr("应用到草稿"), null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        apply.run(
                            session
                                .spec
                                .with("description", description.getText().toString().trim())
                                .with("icon", symbol.getText().toString().trim())
                                .with("accent", color.getText().toString().trim()),
                            tr("调整应用主题与说明"));
                        clearInputs("appearance:");
                        dialog.dismiss();
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    dialog.show();
    ui.styleDialog(dialog);
  }

  private void navigation() {
    LinearLayout body = content();
    String[] screenIds = {"overview", "records", "stats"};
    label(body, tr("默认打开"));
    Spinner start =
        ui.spinner(
            new String[] {tr("概览"), tr("记录"), tr("统计")},
            Arrays.asList(screenIds).indexOf(session.spec.startScreen()));
    body.addView(start);
    List<String> ids = new ArrayList<>(),
        labels = new ArrayList<>(),
        numbers = new ArrayList<>(),
        numberLabels = new ArrayList<>();
    numbers.add("");
    numberLabels.add(tr("不显示累计"));
    for (AppSpec.Field f : session.spec.fields()) {
      ids.add(f.id);
      labels.add(f.label);
      if (f.type.equals("number")) {
        numbers.add(f.id);
        numberLabels.add(f.label);
      }
    }
    label(body, tr("记录卡片的标题"));
    Spinner title =
        ui.spinner(labels.toArray(new String[0]), ids.indexOf(session.spec.titleField()));
    body.addView(title);
    label(body, tr("累计统计的数字项"));
    Spinner metric =
        ui.spinner(
            numberLabels.toArray(new String[0]), numbers.indexOf(session.spec.metricField()));
    body.addView(metric);
    label(body, tr("显示单位"));
    EditText unit = input("navigation:unit", session.spec.unit(), tr("如：元、页、ml"), false);
    body.addView(unit);
    ui.space(body, 8);
    body.addView(ui.body(tr("这里只改变单位文字，不会换算或改写历史数值。")));
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(tr("首页与统计"))
            .setView(scroll(body))
            .setNegativeButton(tr("稍后再改"), null)
            .setPositiveButton(tr("应用到草稿"), null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        apply.run(
                            session
                                .spec
                                .with("startScreen", screenIds[start.getSelectedItemPosition()])
                                .with("titleField", ids.get(title.getSelectedItemPosition()))
                                .with("metricField", numbers.get(metric.getSelectedItemPosition()))
                                .with("unit", unit.getText().toString().trim()),
                            tr("调整默认入口和统计"));
                        clearInputs("navigation:");
                        dialog.dismiss();
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    dialog.show();
    ui.styleDialog(dialog);
  }
}

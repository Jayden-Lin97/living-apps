package dev.livingapps;

import android.app.*;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.json.*;

/** Shared native form, record card and statistics renderer for use, drafts and isolated trials. */
final class RuntimeRenderer {
  private final Activity activity;
  private final Ui ui;

  RuntimeRenderer(Activity activity, Ui ui) {
    this.activity = activity;
    this.ui = ui;
  }

  public static Object effective(AppSpec.Field f, JSONObject data) {
    if (!data.has(f.id)) return f.defaultValue;
    Object value = data.opt(f.id);
    return value == JSONObject.NULL ? null : value;
  }

  public static double total(AppSpec spec, List<Store.Entry> entries) {
    AppSpec.Field field = spec.field(spec.metricField());
    if (field == null) return 0;
    double sum = 0;
    for (Store.Entry entry : entries) {
      Object value = effective(field, entry.data);
      if (value instanceof Number) sum += ((Number) value).doubleValue();
    }
    return sum;
  }

  private String number(double n) {
    return new java.text.DecimalFormat("#,##0.##").format(n);
  }

  private String when(long n) {
    return java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .format(java.time.Instant.ofEpochMilli(n).atZone(java.time.ZoneId.systemDefault()));
  }

  private void section(LinearLayout parent, String title, String hint) {
    ui.space(parent, 12);
    parent.addView(ui.text(title, 18, Ui.INK, true));
    if (hint != null) {
      ui.space(parent, 4);
      parent.addView(ui.body(hint));
    }
    ui.space(parent, 12);
  }

  public Supplier<Object> field(LinearLayout parent, AppSpec.Field field, Object value) {
    return field(parent, field, value, false);
  }

  public Supplier<Object> field(
      LinearLayout parent, AppSpec.Field field, Object value, boolean revealHidden) {
    if (field.hidden && !revealHidden) return () -> value == null ? JSONObject.NULL : value;
    if (!field.group.isEmpty() && !field.group.equals(parent.getTag(R.id.runtime_group))) {
      ui.space(parent, 10);
      parent.addView(ui.text(field.group, 17, Ui.GREEN, true));
      ui.space(parent, 12);
    }
    parent.setTag(R.id.runtime_group, field.group);
    parent.addView(ui.text(field.label + (field.required ? " *" : ""), 14, Ui.INK, true));
    ui.space(parent, 6);
    Supplier<Object> reader;
    if (field.type.equals("boolean")) {
      Switch toggle = ui.toggle("是 / 否", Boolean.TRUE.equals(value));
      toggle.setTag(field.id);
      parent.addView(toggle);
      reader = toggle::isChecked;
    } else if (field.type.equals("choice")) {
      List<String> choices = new ArrayList<>();
      choices.add("请选择");
      choices.addAll(field.options);
      Spinner spinner =
          ui.spinner(
              choices.toArray(new String[0]),
              value == null ? 0 : Math.max(0, choices.indexOf(String.valueOf(value))));
      spinner.setTag(field.id);
      parent.addView(spinner);
      reader =
          () ->
              spinner.getSelectedItemPosition() == 0
                  ? JSONObject.NULL
                  : spinner.getSelectedItem().toString();
    } else {
      EditText input =
          ui.input(
              field.placeholder.isEmpty() ? field.label : field.placeholder,
              field.type.equals("text") && field.id.contains("note"));
      input.setTag(field.id);
      if (field.type.equals("number"))
        input.setInputType(
            InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
      input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4000)});
      input.setText(
          value != null
              ? String.valueOf(value)
              : field.type.equals("date") ? LocalDate.now().toString() : "");
      if (field.type.equals("date")) {
        input.setFocusable(false);
        input.setOnClickListener(
            v -> {
              LocalDate selected;
              try {
                selected = LocalDate.parse(input.getText().toString());
              } catch (Exception e) {
                selected = LocalDate.now();
              }
              new DatePickerDialog(
                      activity,
                      (picker, year, month, day) ->
                          input.setText(LocalDate.of(year, month + 1, day).toString()),
                      selected.getYear(),
                      selected.getMonthValue() - 1,
                      selected.getDayOfMonth())
                  .show();
            });
      }
      parent.addView(input);
      reader =
          () -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return JSONObject.NULL;
            if (field.type.equals("number")) {
              try {
                double number = Double.parseDouble(text);
                return Double.isFinite(number) ? number : text;
              } catch (Exception e) {
                return text;
              }
            }
            return text;
          };
    }
    ui.space(parent, 16);
    return reader;
  }

  public void watchFields(View root, Map<String, Supplier<Object>> readers, Runnable changed) {
    if (root instanceof EditText && readers.containsKey(String.valueOf(root.getTag())))
      ((EditText) root)
          .addTextChangedListener(
              new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                  changed.run();
                }

                public void afterTextChanged(Editable text) {}
              });
    else if (root instanceof Switch && readers.containsKey(String.valueOf(root.getTag())))
      ((Switch) root).setOnCheckedChangeListener((v, c) -> changed.run());
    else if (root instanceof Spinner && readers.containsKey(String.valueOf(root.getTag())))
      ((Spinner) root)
          .setOnItemSelectedListener(
              new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                  changed.run();
                }

                public void onNothingSelected(AdapterView<?> parent) {}
              });
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++)
        watchFields(group.getChildAt(i), readers, changed);
    }
  }

  public LinearLayout recordCard(AppSpec spec, Store.Entry entry, Runnable action) {
    LinearLayout card = ui.card();
    if (spec.compact()) card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
    Object title = effective(spec.field(spec.titleField()), entry.data);
    LinearLayout top = ui.row();
    TextView heading =
        ui.text(
            title == null || String.valueOf(title).isEmpty() ? "未命名记录" : String.valueOf(title),
            16,
            Ui.INK,
            true);
    heading.setMaxLines(1);
    heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
    top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    if (!spec.metricField().isEmpty()
        && !spec.titleField().equals(spec.metricField())
        && !spec.field(spec.metricField()).hidden) {
      Object value = effective(spec.field(spec.metricField()), entry.data);
      if (value instanceof Number) {
        ui.gap(top, 8);
        top.addView(
            ui.text(
                number(((Number) value).doubleValue()) + " " + spec.unit(),
                15,
                Color.parseColor(spec.accent()),
                true));
      }
    }
    card.addView(top);
    List<String> details = new ArrayList<>();
    for (AppSpec.Field f : spec.fields()) {
      if (f.hidden || f.id.equals(spec.titleField()) || f.id.equals(spec.metricField())) continue;
      Object value = effective(f, entry.data);
      if (value != null && !String.valueOf(value).isEmpty()) {
        details.add(
            value instanceof Boolean
                ? f.label + " · " + ((Boolean) value ? "是" : "否")
                : String.valueOf(value));
        if (details.size() >= 2) break;
      }
    }
    ui.space(card, 8);
    TextView detail =
        ui.text(
            details.isEmpty() ? when(entry.created) : String.join("  ·  ", details),
            12,
            Ui.MUTED,
            false);
    detail.setMaxLines(2);
    detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
    card.addView(detail);
    ui.touch(card, 22, action);
    return card;
  }

  public void stats(LinearLayout b, AppSpec spec, List<Store.Entry> entries) {
    LinearLayout card = ui.card();
    card.setBackground(ui.shape(Ui.GREEN, 24, false));
    card.setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(22));
    card.addView(ui.eyebrow("YOUR LITTLE PROGRESS", Ui.LIME));
    ui.space(card, 18);
    boolean hasMetric = !spec.metricField().isEmpty();
    card.addView(
        ui.text(
            hasMetric ? spec.field(spec.metricField()).label + "累计" : "已留下的记录",
            13,
            0xffc0d3c1,
            false));
    ui.space(card, 12);
    LinearLayout total = ui.row();
    total.setGravity(Gravity.BOTTOM);
    total.addView(
        ui.text(
            hasMetric ? number(total(spec, entries)) : String.valueOf(entries.size()),
            42,
            Color.WHITE,
            true));
    ui.gap(total, 8);
    TextView unit = ui.text(hasMetric ? spec.unit() : "条", 15, Ui.LIME, false);
    unit.setPadding(0, 0, 0, ui.dp(7));
    total.addView(unit);
    card.addView(total);
    ui.space(card, 17);
    card.addView(ui.text("每一次记录，都算数。", 11, 0xffb4c9b9, false));
    b.addView(card);
    long recent =
        entries.stream()
            .filter(e -> e.created >= System.currentTimeMillis() - 7 * ReflectionEngine.DAY)
            .count();
    LinearLayout numbers = ui.row();
    for (int i = 0; i < 2; i++) {
      LinearLayout tile = ui.card();
      tile.addView(ui.text(i == 0 ? "全部记录" : "近 7 天新增", 12, Ui.MUTED, false));
      ui.space(tile, 10);
      tile.addView(ui.text(String.valueOf(i == 0 ? entries.size() : recent), 26, Ui.INK, true));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
      if (i == 0) params.rightMargin = ui.dp(6);
      else params.leftMargin = ui.dp(6);
      numbers.addView(tile, params);
    }
    b.addView(numbers);
    AppSpec.Field choice = null;
    for (AppSpec.Field f : spec.fields())
      if (f.type.equals("choice")) {
        choice = f;
        break;
      }
    if (choice != null) {
      section(b, choice.label + "分布", "看看记录都落在了哪里。");
      LinearLayout groups = ui.card();
      int color = Color.parseColor(spec.accent());
      for (String option : choice.options) {
        int count = 0;
        for (Store.Entry e : entries) if (option.equals(effective(choice, e.data))) count++;
        LinearLayout label = ui.row();
        label.addView(ui.text(option, 13, Ui.INK, false), new LinearLayout.LayoutParams(0, -2, 1));
        label.addView(ui.text(count + " 条", 12, Ui.MUTED, false));
        groups.addView(label);
        ui.space(groups, 8);
        LinearLayout track = ui.row();
        track.setBackground(ui.shape(Ui.SOFT, 3, false));
        View fill = new View(activity);
        fill.setBackground(ui.shape(color, 3, false));
        track.addView(fill, new LinearLayout.LayoutParams(0, ui.dp(5), count));
        track.addView(
            new Space(activity),
            new LinearLayout.LayoutParams(0, ui.dp(5), Math.max(1, entries.size()) - count));
        track.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        groups.addView(track);
        ui.space(groups, 18);
      }
      b.addView(groups);
    }
  }
}

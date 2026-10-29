package dev.livingapps.core;

import static dev.livingapps.core.Messages.tr;

import java.util.ArrayList;
import java.util.List;

/** Records never move with UI versions. Preserve stable IDs and types. */
public final class VersionPolicy {
  private VersionPolicy() {}

  public static List<String> errors(AppSpec before, AppSpec after) {
    List<String> errors = new ArrayList<>();
    for (AppSpec.Field old : before.fields()) {
      AppSpec.Field next = after.field(old.id);
      if (next == null) errors.add(tr("不能删除已有字段：") + old.label);
      else if (!old.type.equals(next.type)) errors.add(tr("不能改变已有字段类型：") + old.label);
      else {
        if (!next.options.containsAll(old.options)) errors.add(tr("不能移除已有选项：") + old.label);
        if (!old.required && next.required && next.defaultValue == null)
          errors.add(tr("已有可选字段改为必填须提供默认值：") + old.label);
      }
    }
    for (AppSpec.Field next : after.fields())
      if (before.field(next.id) == null && next.required && next.defaultValue == null)
        errors.add(tr("新增必填字段须提供默认值：") + next.label);
    return errors;
  }

  public static List<String> describe(AppSpec before, AppSpec after) {
    List<String> changes = new ArrayList<>();
    if (!before.name().equals(after.name())) changes.add(tr("应用名称 → ") + after.name());
    if (!before.accent().equals(after.accent())) changes.add(tr("调整主题颜色"));
    if (before.quickAdd() != after.quickAdd())
      changes.add(after.quickAdd() ? tr("首页增加快速记录入口") : tr("关闭快速记录入口"));
    if (before.compact() != after.compact())
      changes.add(after.compact() ? tr("记录列表使用紧凑布局") : tr("记录列表使用舒展布局"));
    if (!before.startScreen().equals(after.startScreen()))
      changes.add(tr("默认打开页面 → ") + after.startScreen());
    for (AppSpec.Field f : after.fields()) {
      AppSpec.Field old = before.field(f.id);
      if (old == null) changes.add(tr("新增字段：") + f.label);
      else if (!old.label.equals(f.label)
          || !old.options.equals(f.options)
          || !String.valueOf(old.defaultValue).equals(String.valueOf(f.defaultValue)))
        changes.add(tr("调整字段：") + f.label);
    }
    if (changes.isEmpty()) changes.add(tr("调整应用说明、字段顺序或统计设置"));
    return changes;
  }

  public static boolean autoAllowed(AppSpec before, AppSpec after) {
    if (before.quickAdd() || !after.quickAdd()) return false;
    return before.with("quickAdd", true).toString().equals(after.toString());
  }
}

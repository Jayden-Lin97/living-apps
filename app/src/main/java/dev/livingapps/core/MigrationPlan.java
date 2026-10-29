package dev.livingapps.core;

import static dev.livingapps.core.Messages.tr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Explicit, deterministic local transformations. Not accepted by the ordinary model edit path. */
public final class MigrationPlan {
  public final AppSpec before, after;
  public final String fieldId, description;
  private final String target;
  private final Double factor;
  private final Map<String, String> mapping;

  private MigrationPlan(
      AppSpec before,
      String fieldId,
      String target,
      Double factor,
      String unit,
      Map<String, String> mapping) {
    this.before = before;
    this.fieldId = fieldId;
    this.target = target;
    this.factor = factor;
    this.mapping =
        mapping == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(mapping));
    AppSpec.Field field = before.field(fieldId);
    if (field == null) throw new IllegalArgumentException(tr("记录项已不存在"));
    if (factor != null) {
      if (!field.type.equals("number")
          || !before.metricField().equals(fieldId)
          || !Double.isFinite(factor)
          || factor <= 0
          || factor == 1) throw new IllegalArgumentException(tr("只对累计数字项使用有限、正数且不为 1 的换算倍率"));
      if (unit == null || unit.trim().isEmpty() || unit.length() > 12)
        throw new IllegalArgumentException(tr("请填写换算后的单位，最多 12 字"));
      description =
          field.label + tr("：全部非空数值 × ") + factor + tr("；单位 ") + before.unit() + " → " + unit;
    } else if (mapping != null) {
      if (!field.type.equals("choice") || !mapping.keySet().equals(new HashSet<>(field.options)))
        throw new IllegalArgumentException(tr("必须为每个旧选项指定目标内容"));
      description = field.label + tr("：映射旧选项 ") + mapping;
    } else {
      if (!targets(field.type).contains(target))
        throw new IllegalArgumentException(tr("暂不支持这种类型转换"));
      description =
          field.label
              + "："
              + field.type
              + " → "
              + target
              + (before.metricField().equals(fieldId) ? tr("；关闭该项原累计与单位") : "");
    }
    JSONObject root = before.json();
    JSONArray fields = root.optJSONArray("fields");
    for (int i = 0; i < fields.length(); i++) {
      JSONObject raw = fields.optJSONObject(i);
      if (!raw.optString("id").equals(fieldId)) continue;
      if (target != null) {
        J.put(raw, "type", target);
        raw.remove("options");
      }
      if (mapping != null)
        J.put(raw, "options", new JSONArray(new LinkedHashSet<>(mapping.values())));
      if (raw.has("defaultValue") && raw.opt("defaultValue") != JSONObject.NULL)
        J.put(raw, "defaultValue", convert(raw.opt("defaultValue")));
    }
    if (factor != null) J.put(root, "unit", unit.trim());
    if (target != null && !target.equals("number") && before.metricField().equals(fieldId)) {
      J.put(root, "metricField", "");
      J.put(root, "unit", "");
    }
    JSONArray actions = root.optJSONArray("actions");
    for (int i = 0; actions != null && i < actions.length(); i++) {
      JSONObject values = actions.optJSONObject(i).optJSONObject("values");
      if (!values.has(fieldId) || values.opt(fieldId) == JSONObject.NULL) continue;
      if (field.type.equals("date") && "$today".equals(values.opt(fieldId)))
        throw new IllegalArgumentException(tr("请先移除或修改引用该日期的“点击当天”固定动作，再转换类型"));
      J.put(values, fieldId, convert(values.opt(fieldId)));
    }
    after = AppSpec.parse(root.toString());
  }

  public static List<String> targets(String from) {
    if (from.equals("text")) return Arrays.asList("number", "date", "boolean");
    if (Arrays.asList("number", "date", "boolean").contains(from))
      return Collections.singletonList("text");
    return Collections.emptyList();
  }

  public static MigrationPlan type(AppSpec spec, String field, String target) {
    return new MigrationPlan(spec, field, target, null, null, null);
  }

  public static MigrationPlan scale(AppSpec spec, String field, double factor, String unit) {
    return new MigrationPlan(spec, field, null, factor, unit, null);
  }

  public static MigrationPlan choices(AppSpec spec, String field, Map<String, String> mapping) {
    return new MigrationPlan(spec, field, null, null, null, mapping);
  }

  private Object convert(Object value) {
    if (value == null || value == JSONObject.NULL) return JSONObject.NULL;
    if (mapping != null) {
      if (!(value instanceof String) || !mapping.containsKey(value))
        throw new IllegalArgumentException(tr("存在未映射的旧选项"));
      return mapping.get(value);
    }
    if (factor != null) {
      if (!(value instanceof Number)) throw new IllegalArgumentException(tr("原值不是数字"));
      double result =
          BigDecimal.valueOf(((Number) value).doubleValue())
              .multiply(BigDecimal.valueOf(factor))
              .doubleValue();
      if (!Double.isFinite(result)) throw new IllegalArgumentException(tr("换算结果超出范围"));
      return result;
    }
    String from = before.field(fieldId).type;
    if (target.equals("text")) {
      if (from.equals("number")) {
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue()))
          throw new IllegalArgumentException(tr("原值不是有效数字"));
        return BigDecimal.valueOf(((Number) value).doubleValue())
            .stripTrailingZeros()
            .toPlainString();
      }
      if (from.equals("boolean")) {
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(tr("原值不是开关"));
        return value.toString();
      }
      LocalDate.parse(String.valueOf(value));
      return value.toString();
    }
    if (!(value instanceof String)) throw new IllegalArgumentException(tr("原值不是文字"));
    String text = ((String) value).trim();
    if (target.equals("number")) {
      if (!text.matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"))
        throw new IllegalArgumentException(tr("文字不是数字"));
      double n = Double.parseDouble(text);
      if (!Double.isFinite(n)) throw new IllegalArgumentException(tr("数字超出范围"));
      return n;
    }
    if (target.equals("date")) {
      LocalDate.parse(text);
      return text;
    }
    if (text.equals("true") || text.equals("是")) return true;
    if (text.equals("false") || text.equals("否")) return false;
    throw new IllegalArgumentException(tr("开关只接受 true、false、是、否"));
  }

  public static final class Result {
    public int converted, empty, missing, failed;
    public final List<JSONObject> records = new ArrayList<>();

    public boolean allowed() {
      return failed == 0;
    }

    public String summary() {
      return tr("可转换 ")
          + converted
          + tr(" 条 · 空值 ")
          + empty
          + tr(" 条 · 缺项 ")
          + missing
          + tr(" 条 · 失败 ")
          + failed
          + tr(" 条");
    }
  }

  public Result evaluate(List<JSONObject> rows) {
    Result result = new Result();
    AppSpec.Field field = after.field(fieldId);
    for (JSONObject row : rows) {
      JSONObject next = J.parse(row.toString());
      if (!row.has(fieldId)) {
        result.missing++;
        result.records.add(next);
        continue;
      }
      if (row.opt(fieldId) == JSONObject.NULL) {
        result.empty++;
        result.records.add(next);
        continue;
      }
      try {
        Object converted = convert(row.opt(fieldId));
        if (field.error(converted, field.required) != null)
          throw new IllegalArgumentException(tr("转换后不合法"));
        J.put(next, fieldId, converted);
        result.converted++;
      } catch (Exception e) {
        result.failed++;
      }
      result.records.add(next);
    }
    return result;
  }
}

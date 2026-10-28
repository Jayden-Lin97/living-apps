package dev.livingapps.core;

import java.time.LocalDate;
import java.util.*;
import org.json.*;

/**
 * Explicit capability: a user-tapped preset can add one validated record. No scripts or network.
 */
public final class ActionSpec {
  public final String id, label;
  private final JSONObject values;

  private ActionSpec(JSONObject raw) {
    id = raw.optString("id");
    label = raw.optString("label");
    values = J.parse(raw.optJSONObject("values").toString());
  }

  public static List<ActionSpec> read(AppSpec spec) {
    List<ActionSpec> out = new ArrayList<>();
    JSONArray array = spec.json().optJSONArray("actions");
    for (int i = 0; array != null && i < array.length(); i++)
      out.add(new ActionSpec(array.optJSONObject(i)));
    return out;
  }

  public static void validate(AppSpec spec) {
    JSONObject root = spec.json();
    if (!root.has("actions")) return;
    JSONArray array = root.optJSONArray("actions");
    if (array == null || array.length() > 6) throw new IllegalArgumentException("固定动作最多 6 个");
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < array.length(); i++) {
      JSONObject raw = array.optJSONObject(i);
      if (raw == null) throw new IllegalArgumentException("固定动作格式错误");
      Iterator<String> keys = raw.keys();
      while (keys.hasNext())
        if (!Arrays.asList("id", "label", "kind", "values").contains(keys.next()))
          throw new IllegalArgumentException("固定动作包含未支持能力");
      if (!(raw.opt("id") instanceof String)
          || !(raw.opt("label") instanceof String)
          || !(raw.opt("kind") instanceof String))
        throw new IllegalArgumentException("动作 ID、名称和类型须为文字");
      String id = raw.optString("id"), label = raw.optString("label");
      if (!id.matches("[a-z][a-z0-9_]{0,39}")
          || !ids.add(id)
          || label.trim().isEmpty()
          || label.length() > 30
          || !raw.optString("kind").equals("add_record")
          || raw.optJSONObject("values") == null)
        throw new IllegalArgumentException("仅支持命名的点击后新增记录动作");
      JSONObject values = raw.optJSONObject("values");
      Iterator<String> valueKeys = values.keys();
      while (valueKeys.hasNext())
        if (spec.field(valueKeys.next()) == null)
          throw new IllegalArgumentException("动作引用了不存在的记录项");
      ActionSpec action = new ActionSpec(raw);
      List<String> errors = spec.validateRecord(action.resolved(spec));
      if (!errors.isEmpty())
        throw new IllegalArgumentException("动作“" + label + "”无法填写完整记录：" + String.join("；", errors));
    }
  }

  public JSONObject resolved(AppSpec spec) {
    JSONObject result = J.obj();
    for (AppSpec.Field field : spec.fields()) {
      Object value = values.has(field.id) ? values.opt(field.id) : field.defaultValue;
      if (field.type.equals("date") && "$today".equals(value)) value = LocalDate.now().toString();
      J.put(result, field.id, value == null ? JSONObject.NULL : value);
    }
    return result;
  }
}

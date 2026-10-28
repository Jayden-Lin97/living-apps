package dev.livingapps.core;

import java.util.*;
import org.json.*;

/** Semantic diffs use stable field IDs, never array indexes or model-written explanations. */
public final class EditChanges {
  private EditChanges() {}

  public static final class Change {
    public final String path, label;
    public final Object before, after;
    public final boolean present;

    Change(String path, String label, Object before, Object after, boolean present) {
      this.path = path;
      this.label = label;
      this.before = before;
      this.after = after;
      this.present = present;
    }

    public String description() {
      if (path.equals("app/actions")) return actionDescription(before, after);
      if (path.equals("app/schemaVersion")) return "调整应用支持的分组、隐藏或固定动作能力";
      if (path.equals("order")) return "调整记录项顺序";
      if (path.startsWith("field/") && path.split("/").length == 2)
        return (present ? "新增记录项：" : "移除未发布记录项：") + label;
      return label + "：" + value(before) + " → " + value(after);
    }
  }

  private static Map<String, JSONObject> actionMap(Object value) {
    Map<String, JSONObject> out = new LinkedHashMap<>();
    if (value instanceof JSONArray) {
      JSONArray rows = (JSONArray) value;
      for (int i = 0; i < rows.length(); i++) {
        JSONObject action = rows.optJSONObject(i);
        out.put(action.optString("id"), action);
      }
    }
    return out;
  }

  private static String actionDescription(Object before, Object after) {
    Map<String, JSONObject> a = actionMap(before), b = actionMap(after);
    Set<String> ids = new LinkedHashSet<>(a.keySet());
    ids.addAll(b.keySet());
    List<String> lines = new ArrayList<>();
    for (String id : ids) {
      JSONObject old = a.get(id), next = b.get(id);
      if (same(old, next)) continue;
      if (next == null) {
        lines.add("移除固定动作“" + old.optString("label") + "”（保留记录）");
        continue;
      }
      String name = next.optString("label");
      if (old == null) lines.add("新增固定动作“" + name + "”：点击新增 1 条记录");
      else if (!old.optString("label").equals(name))
        lines.add("固定动作名称：" + old.optString("label") + " → " + name);
      JSONObject left = old == null ? J.obj() : old.optJSONObject("values"),
          right = next.optJSONObject("values");
      for (String key : keys(left, right))
        if (!same(left.opt(key), right.opt(key)))
          lines.add(
              "动作“"
                  + name
                  + "”预设 "
                  + key
                  + "："
                  + value(left.opt(key))
                  + " → "
                  + value(right.opt(key)));
    }
    return String.join("\n", lines);
  }

  public static boolean same(Object a, Object b) {
    if (a == null || a == JSONObject.NULL) return b == null || b == JSONObject.NULL;
    if (b == null || b == JSONObject.NULL) return false;
    if (a instanceof JSONObject && b instanceof JSONObject) {
      JSONObject x = (JSONObject) a, y = (JSONObject) b;
      if (x.length() != y.length()) return false;
      Iterator<String> keys = x.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        if (!y.has(k) || !same(x.opt(k), y.opt(k))) return false;
      }
      return true;
    }
    if (a instanceof JSONArray && b instanceof JSONArray) {
      JSONArray x = (JSONArray) a, y = (JSONArray) b;
      if (x.length() != y.length()) return false;
      for (int i = 0; i < x.length(); i++) if (!same(x.opt(i), y.opt(i))) return false;
      return true;
    }
    if (a instanceof Number && b instanceof Number)
      return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
    return a.equals(b);
  }

  public static String value(Object value) {
    if (value == null || value == JSONObject.NULL) return "未设置";
    if (value instanceof Boolean) return (Boolean) value ? "开启" : "关闭";
    String text = String.valueOf(value);
    return text.length() > 80 ? text.substring(0, 80) + "…" : text;
  }

  private static Set<String> keys(JSONObject a, JSONObject b) {
    Set<String> keys = new TreeSet<>();
    a.keys().forEachRemaining(keys::add);
    b.keys().forEachRemaining(keys::add);
    return keys;
  }

  public static Map<String, JSONObject> fields(JSONObject root) {
    Map<String, JSONObject> out = new LinkedHashMap<>();
    JSONArray a = root.optJSONArray("fields");
    for (int i = 0; a != null && i < a.length(); i++) {
      JSONObject f = a.optJSONObject(i);
      if (f != null) out.put(f.optString("id"), f);
    }
    return out;
  }

  private static JSONArray order(JSONObject spec) {
    return new JSONArray(new ArrayList<>(fields(spec).keySet()));
  }

  private static String rootLabel(String k) {
    switch (k) {
      case "name":
        return "应用名称";
      case "description":
        return "应用说明";
      case "icon":
        return "应用图标";
      case "accent":
        return "主题颜色";
      case "startScreen":
        return "默认首页";
      case "quickAdd":
        return "快速记录入口";
      case "compact":
        return "紧凑布局";
      case "titleField":
        return "列表标题来源";
      case "metricField":
        return "统计记录项";
      case "unit":
        return "显示单位";
      case "actions":
        return "固定动作";
      case "schemaVersion":
        return "应用能力格式";
      default:
        return k;
    }
  }

  private static String propertyLabel(String k) {
    switch (k) {
      case "label":
        return "显示名称";
      case "placeholder":
        return "填写提示";
      case "required":
        return "必填设置";
      case "defaultValue":
        return "默认值";
      case "options":
        return "可选内容";
      case "type":
        return "类型";
      case "group":
        return "分组";
      case "hidden":
        return "隐藏设置";
      default:
        return k;
    }
  }

  public static List<Change> between(AppSpec before, AppSpec after) {
    JSONObject a = before.json(), b = after.json();
    List<Change> out = new ArrayList<>();
    for (String k : keys(a, b))
      if (!k.equals("fields") && !same(a.opt(k), b.opt(k)))
        out.add(new Change("app/" + k, rootLabel(k), a.opt(k), b.opt(k), b.has(k)));
    Map<String, JSONObject> af = fields(a), bf = fields(b);
    Set<String> ids = new LinkedHashSet<>(af.keySet());
    ids.addAll(bf.keySet());
    for (String id : ids) {
      JSONObject old = af.get(id), next = bf.get(id);
      if (old == null || next == null) {
        out.add(
            new Change(
                "field/" + id,
                (next == null ? old : next).optString("label"),
                old,
                next,
                next != null));
        continue;
      }
      for (String k : keys(old, next))
        if (!same(old.opt(k), next.opt(k)))
          out.add(
              new Change(
                  "field/" + id + "/" + k,
                  next.optString("label") + "·" + propertyLabel(k),
                  old.opt(k),
                  next.opt(k),
                  next.has(k)));
    }
    if (!same(order(a), order(b))) out.add(new Change("order", "记录项顺序", order(a), order(b), true));
    return out;
  }

  public static boolean withinField(AppSpec before, AppSpec after, String fieldId) {
    for (Change c : between(before, after)) {
      if (c.path.equals("app/schemaVersion")) continue;
      if (c.path.startsWith("field/" + fieldId + "/")) continue;
      if (c.path.equals("order")) {
        List<String> left = new ArrayList<>(fields(before.json()).keySet()),
            right = new ArrayList<>(fields(after.json()).keySet());
        left.remove(fieldId);
        right.remove(fieldId);
        if (left.equals(right)) continue;
      }
      return false;
    }
    return true;
  }

  public static boolean withinTarget(AppSpec before, AppSpec after, String target) {
    if (target.startsWith("field:")) return withinField(before, after, target.substring(6));
    if (target.startsWith("action:")) {
      for (Change change : between(before, after))
        if (!change.path.equals("app/schemaVersion") && !change.path.equals("app/actions"))
          return false;
      return same(
          otherActions(before, target.substring(7)), otherActions(after, target.substring(7)));
    }
    return true;
  }

  private static JSONArray otherActions(AppSpec spec, String excluded) {
    JSONArray out = new JSONArray(), actions = spec.json().optJSONArray("actions");
    for (int i = 0; actions != null && i < actions.length(); i++)
      if (!actions.optJSONObject(i).optString("id").equals(excluded))
        out.put(actions.optJSONObject(i));
    return out;
  }

  private static void putChange(JSONObject target, Change c) {
    if (c.path.startsWith("app/")) {
      String k = c.path.substring(4);
      if (c.present) J.put(target, k, c.after);
      else target.remove(k);
      return;
    }
    Map<String, JSONObject> map = fields(target);
    if (c.path.equals("order")) {
      Map<String, JSONObject> sorted = new LinkedHashMap<>();
      JSONArray ids = (JSONArray) c.after;
      for (int i = 0; i < ids.length(); i++) {
        String id = ids.optString(i);
        if (map.containsKey(id)) sorted.put(id, map.get(id));
      }
      for (String id : map.keySet()) sorted.putIfAbsent(id, map.get(id));
      map = sorted;
    } else {
      String[] parts = c.path.split("/");
      String id = parts[1];
      if (parts.length == 2) {
        if (c.present) map.put(id, J.parse(c.after.toString()));
        else map.remove(id);
      } else {
        JSONObject f = map.get(id);
        if (f == null) throw new IllegalArgumentException("修改目标已不存在");
        if (c.present) J.put(f, parts[2], c.after);
        else f.remove(parts[2]);
      }
    }
    JSONArray array = new JSONArray();
    for (JSONObject f : map.values()) array.put(f);
    J.put(target, "fields", array);
  }

  public static final class Merge {
    public final AppSpec spec;
    public final List<Change> conflicts;

    Merge(AppSpec spec, List<Change> conflicts) {
      this.spec = spec;
      this.conflicts = conflicts;
    }
  }

  /** Three-way merge; unresolved conflicts keep current values until the user chooses. */
  public static Merge merge(
      AppSpec base, AppSpec current, AppSpec proposed, Map<String, Boolean> choices) {
    List<Change> incoming = between(base, proposed),
        local = between(base, current),
        conflicts = new ArrayList<>();
    JSONObject result = current.json();
    for (Change change : incoming) {
      boolean collision = false;
      for (Change other : local) {
        boolean overlaps =
            change.path.equals(other.path)
                || change.path.startsWith(other.path + "/")
                || other.path.startsWith(change.path + "/");
        if (overlaps && !same(change.after, other.after)) collision = true;
      }
      if (collision && !choices.containsKey(change.path)) {
        conflicts.add(change);
        continue;
      }
      if (!collision || Boolean.TRUE.equals(choices.get(change.path))) putChange(result, change);
    }
    return new Merge(AppSpec.parse(result.toString()), conflicts);
  }

  public static final class Impact {
    public final int records, invalid;
    public final List<String> problems;

    Impact(int records, int invalid, List<String> problems) {
      this.records = records;
      this.invalid = invalid;
      this.problems = problems;
    }

    public boolean allowed() {
      return problems.isEmpty() && invalid == 0;
    }
  }

  public static Impact impact(AppSpec before, AppSpec after, List<JSONObject> records) {
    List<String> problems = new ArrayList<>(VersionPolicy.errors(before, after));
    int invalid = 0;
    for (JSONObject data : records) {
      boolean damaged = false;
      for (AppSpec.Field field : after.fields()) {
        AppSpec.Field old = before.field(field.id);
        Object next = data.has(field.id) ? data.opt(field.id) : field.defaultValue;
        if (field.error(next, field.required) == null) continue;
        Object previous =
            old == null ? null : data.has(old.id) ? data.opt(old.id) : old.defaultValue;
        if (old == null || old.error(previous, old.required) == null || field.hidden && !old.hidden)
          damaged = true;
      }
      if (damaged) invalid++;
    }
    if (invalid > 0) problems.add(invalid + " 条已有记录无法满足新设置，请先解决必填项、默认值或选项问题；记录尚未改变");
    return new Impact(records.size(), invalid, problems);
  }
}

package dev.livingapps.core;

import java.time.LocalDate;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** A bounded data protocol, never executable code. Immutable by defensive copy. */
public final class AppSpec {
  private final JSONObject json;
  private final List<Field> fields;
  private static final Set<String> ROOT_KEYS =
      new HashSet<>(
          Arrays.asList(
              "schemaVersion",
              "name",
              "description",
              "icon",
              "accent",
              "fields",
              "titleField",
              "metricField",
              "unit",
              "startScreen",
              "quickAdd",
              "compact"));
  private static final Set<String> V2_ROOT_KEYS = new HashSet<>(ROOT_KEYS);
  private static final Set<String> V2_FIELD_KEYS;
  private static final Set<String> FIELD_KEYS =
      new HashSet<>(
          Arrays.asList(
              "id", "label", "type", "required", "options", "defaultValue", "placeholder"));

  static {
    V2_ROOT_KEYS.add("actions");
    V2_FIELD_KEYS = new HashSet<>(FIELD_KEYS);
    V2_FIELD_KEYS.add("group");
    V2_FIELD_KEYS.add("hidden");
  }

  public static final Set<String> TYPES =
      Collections.unmodifiableSet(
          new HashSet<>(Arrays.asList("text", "number", "boolean", "date", "choice")));

  private AppSpec(JSONObject json, List<Field> fields) {
    this.json = json;
    this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
  }

  public static AppSpec parse(String text) {
    if (text == null || text.length() > 64000) throw new IllegalArgumentException("应用描述过大或为空");
    try {
      JSONObject json = J.parse(J.cleanJson(text));
      Object protocolVersion = json.get("schemaVersion");
      if (!(protocolVersion instanceof Number)
          || (((Number) protocolVersion).doubleValue() != 1.0
              && ((Number) protocolVersion).doubleValue() != 2.0))
        throw new IllegalArgumentException("暂不支持该应用协议版本");
      boolean extended = ((Number) protocolVersion).doubleValue() == 2.0;
      checkKeys(json, extended ? V2_ROOT_KEYS : ROOT_KEYS);
      requireString(json, "name", 1, 40);
      requireString(json, "description", 0, 240);
      requireString(json, "icon", 1, 12);
      if (!requireString(json, "accent", 7, 7).matches("#[0-9a-fA-F]{6}"))
        throw new IllegalArgumentException("主题颜色格式不正确");
      String screen = requireString(json, "startScreen", 1, 20);
      if (!Arrays.asList("overview", "records", "stats").contains(screen))
        throw new IllegalArgumentException("不支持的首页类型");
      if (!(json.get("quickAdd") instanceof Boolean) || !(json.get("compact") instanceof Boolean))
        throw new IllegalArgumentException("布局开关须为布尔值");
      requireString(json, "unit", 0, 12);
      JSONArray array = json.getJSONArray("fields");
      if (array.length() < 1 || array.length() > 20)
        throw new IllegalArgumentException("字段数量须为 1–20 个");
      List<Field> fields = new ArrayList<>();
      Set<String> ids = new HashSet<>();
      for (int i = 0; i < array.length(); i++) {
        JSONObject f = array.getJSONObject(i);
        checkKeys(f, extended ? V2_FIELD_KEYS : FIELD_KEYS);
        String id = requireString(f, "id", 1, 40);
        if (!id.matches("[a-z][a-z0-9_]{0,39}") || !ids.add(id))
          throw new IllegalArgumentException("字段 ID 重复或格式不正确");
        String type = requireString(f, "type", 1, 12);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("暂不支持字段类型：" + type);
        String label = requireString(f, "label", 1, 40);
        if (!(f.get("required") instanceof Boolean))
          throw new IllegalArgumentException("required 须为布尔值");
        List<String> options = new ArrayList<>();
        JSONArray opts = f.optJSONArray("options");
        if (f.has("options") && opts == null) throw new IllegalArgumentException("options 须为数组");
        if (opts != null)
          for (int k = 0; k < opts.length(); k++) {
            Object o = opts.get(k);
            if (!(o instanceof String)
                || ((String) o).trim().isEmpty()
                || ((String) o).length() > 80
                || options.contains(o)) throw new IllegalArgumentException("选项格式不正确");
            options.add((String) o);
          }
        if (type.equals("choice") && (options.isEmpty() || options.size() > 30))
          throw new IllegalArgumentException("选择字段须含 1–30 个选项");
        if (!type.equals("choice") && !options.isEmpty())
          throw new IllegalArgumentException("只有选择字段支持 options");
        String placeholder = f.has("placeholder") ? requireString(f, "placeholder", 0, 120) : "";
        Object defaultValue = f.opt("defaultValue");
        if (defaultValue == JSONObject.NULL) defaultValue = null;
        boolean hidden = f.optBoolean("hidden", false);
        if (f.has("hidden") && !(f.opt("hidden") instanceof Boolean))
          throw new IllegalArgumentException("隐藏设置须为开关值");
        String group = f.has("group") ? requireString(f, "group", 0, 40) : "";
        if (hidden && f.getBoolean("required") && defaultValue == null)
          throw new IllegalArgumentException("隐藏必填记录项须有合法默认值");
        Field field =
            new Field(
                id,
                label,
                type,
                f.getBoolean("required"),
                options,
                defaultValue,
                placeholder,
                hidden,
                group);
        if (defaultValue != null) {
          String error = field.error(defaultValue, field.required);
          if (error != null) throw new IllegalArgumentException("默认值无效：" + label + "，" + error);
        }
        fields.add(field);
      }
      String title = requireString(json, "titleField", 1, 40);
      String metric = requireString(json, "metricField", 0, 40);
      if (!ids.contains(title)) throw new IllegalArgumentException("标题字段不存在");
      if (fields.stream().anyMatch(f -> f.id.equals(title) && f.hidden))
        throw new IllegalArgumentException("列表标题项不能隐藏，请先更换标题来源");
      if (!metric.isEmpty()
          && fields.stream().noneMatch(f -> f.id.equals(metric) && f.type.equals("number")))
        throw new IllegalArgumentException("统计字段必须是已有数字字段");
      AppSpec result = new AppSpec(json, fields);
      ActionSpec.validate(result);
      return result;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("应用描述缺少必要属性或类型不正确", e);
    }
  }

  private static void checkKeys(JSONObject object, Set<String> allowed) {
    Iterator<String> keys = object.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!allowed.contains(key)) throw new IllegalArgumentException("不支持的应用属性：" + key);
    }
  }

  private static String requireString(JSONObject json, String key, int min, int max)
      throws Exception {
    Object value = json.get(key);
    if (!(value instanceof String)) throw new IllegalArgumentException(key + " 须为字符串");
    String s = (String) value;
    if (s.length() < min || s.length() > max) throw new IllegalArgumentException(key + " 长度不正确");
    return s;
  }

  public String name() {
    return json.optString("name");
  }

  public String description() {
    return json.optString("description");
  }

  public String icon() {
    return json.optString("icon");
  }

  public String accent() {
    return json.optString("accent");
  }

  public String titleField() {
    return json.optString("titleField");
  }

  public String metricField() {
    return json.optString("metricField");
  }

  public String unit() {
    return json.optString("unit");
  }

  public String startScreen() {
    return json.optString("startScreen");
  }

  public boolean quickAdd() {
    return json.optBoolean("quickAdd");
  }

  public boolean compact() {
    return json.optBoolean("compact");
  }

  public List<ActionSpec> actions() {
    return ActionSpec.read(this);
  }

  public List<Field> fields() {
    return fields;
  }

  public Field field(String id) {
    return fields.stream().filter(f -> f.id.equals(id)).findFirst().orElse(null);
  }

  public JSONObject json() {
    return J.parse(json.toString());
  }

  public AppSpec with(String key, Object value) {
    JSONObject copy = json();
    J.put(copy, key, value);
    return parse(copy.toString());
  }

  public List<String> validateRecord(JSONObject data) {
    List<String> errors = new ArrayList<>();
    Iterator<String> keys = data.keys();
    while (keys.hasNext()) {
      if (field(keys.next()) == null) errors.add("记录包含未知字段");
    }
    for (Field field : fields) {
      String error = field.error(data.opt(field.id), field.required);
      if (error != null) errors.add(field.label + "：" + error);
    }
    return errors;
  }

  @Override
  public String toString() {
    return json.toString();
  }

  public static final class Field {
    public final String id, label, type, placeholder;
    public final boolean required;
    public final boolean hidden;
    public final String group;
    public final List<String> options;
    public final Object defaultValue;

    private Field(
        String id,
        String label,
        String type,
        boolean required,
        List<String> options,
        Object defaultValue,
        String placeholder,
        boolean hidden,
        String group) {
      this.id = id;
      this.label = label;
      this.type = type;
      this.required = required;
      this.options = Collections.unmodifiableList(new ArrayList<>(options));
      this.defaultValue = defaultValue;
      this.placeholder = placeholder;
      this.hidden = hidden;
      this.group = group;
    }

    public String error(Object value, boolean require) {
      if (value == null || value == JSONObject.NULL || value.equals(""))
        return require ? "请填写此项" : null;
      switch (type) {
        case "number":
          return value instanceof Number && Double.isFinite(((Number) value).doubleValue())
              ? null
              : "请输入有限数字";
        case "boolean":
          return value instanceof Boolean ? null : "须为开关值";
        case "date":
          try {
            if (!(value instanceof String)) return "日期格式应为 YYYY-MM-DD";
            LocalDate.parse((String) value);
            return null;
          } catch (Exception e) {
            return "日期格式应为 YYYY-MM-DD";
          }
        case "choice":
          return value instanceof String && options.contains(value) ? null : "请选择已有选项";
        default:
          return value instanceof String && ((String) value).length() <= 4000
              ? null
              : "文本不能超过 4000 字";
      }
    }
  }
}

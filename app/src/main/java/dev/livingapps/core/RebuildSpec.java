package dev.livingapps.core;

import java.util.*;
import org.json.*;

/** Local, declarative UI bindings. No scripts, URLs, coordinates or generated executable code. */
public final class RebuildSpec {
  private final JSONObject data;

  public RebuildSpec(JSONObject source) {
    data = J.parse(source.toString());
    keys(data, "schema,name,package,version,certificate,dark,compact,widgets");
    if (!(data.opt("schema") instanceof Number) || data.optDouble("schema") != 1) bad("不支持的重构版本");
    string(data, "name", 60, false);
    String pkg = string(data, "package", 200, false);
    if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) bad("应用标识无效");
    if (!(data.opt("version") instanceof Number)
        || data.optLong("version") < 0
        || !Double.isFinite(data.optDouble("version"))
        || data.optDouble("version") != (double) data.optLong("version")) bad("应用版本无效");
    if (!string(data, "certificate", 64, false).matches("[0-9a-f]{64}")) bad("应用签名无效");
    bool(data, "dark");
    bool(data, "compact");
    JSONArray widgets = data.optJSONArray("widgets");
    if (widgets == null || widgets.length() > 24) bad("最多组合 24 个组件");
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < widgets.length(); i++) {
      JSONObject w = widgets.optJSONObject(i);
      if (w == null) bad("组件无效");
      keys(w, "id,label,kind,selector,steps,page,interactive");
      String id = string(w, "id", 50, false);
      if (!id.matches("[a-zA-Z][a-zA-Z0-9_]*") || !ids.add(id)) bad("组件 ID 无效或重复");
      string(w, "label", 60, false);
      String kind = string(w, "kind", 20, false);
      if (kind.equals("read") || kind.equals("list")) {
        selector(w.optJSONObject("selector"));
        page(w);
        if (kind.equals("list")) bool(w, "interactive");
        else if (w.has("interactive")) bad("信息组件不能点击");
        if (w.has("steps")) bad("信息组件不执行动作");
      } else if (kind.equals("action") || kind.equals("input")) {
        if (w.has("selector") || w.has("page") || w.has("interactive")) bad("动作必须使用步骤");
        JSONArray steps = w.optJSONArray("steps");
        if (steps == null || steps.length() < 1 || steps.length() > 8) bad("每个动作需要 1–8 个步骤");
        int inputs = 0;
        for (int s = 0; s < steps.length(); s++) {
          JSONObject step = steps.optJSONObject(s);
          if (step == null) bad("步骤无效");
          keys(step, "op,selector,page");
          String op = step.optString("op");
          if (!Arrays.asList("click", "input", "scroll").contains(op)) bad("不支持的动作");
          if (op.equals("input")) inputs++;
          selector(step.optJSONObject("selector"));
          page(step);
        }
        if (inputs > 1 || inputs != (kind.equals("input") ? 1 : 0)) bad("输入组件需要且仅有一个输入步骤");
      } else bad("未知组件");
    }
  }

  public static RebuildSpec create(String name, String pkg, long version, String certificate) {
    return new RebuildSpec(
        J.obj(
            "schema",
            1,
            "name",
            name,
            "package",
            pkg,
            "version",
            version,
            "certificate",
            certificate,
            "dark",
            false,
            "compact",
            false,
            "widgets",
            J.arr()));
  }

  public JSONObject json() {
    return J.parse(data.toString());
  }

  public String name() {
    return data.optString("name");
  }

  public String pkg() {
    return data.optString("package");
  }

  public long version() {
    return data.optLong("version");
  }

  public String certificate() {
    return data.optString("certificate");
  }

  public boolean dark() {
    return data.optBoolean("dark");
  }

  public boolean compact() {
    return data.optBoolean("compact");
  }

  public JSONArray widgets() {
    return json().optJSONArray("widgets");
  }

  public JSONObject widget(String id) {
    JSONArray list = widgets();
    for (int i = 0; i < list.length(); i++)
      if (list.optJSONObject(i).optString("id").equals(id)) return list.optJSONObject(i);
    throw new IllegalArgumentException("组件不存在");
  }

  public RebuildSpec with(String key, Object value) {
    JSONObject copy = json();
    J.put(copy, key, value);
    return new RebuildSpec(copy);
  }

  public void assertTarget(String pkg, long version, String certificate) {
    if (!pkg().equals(pkg) || version() != version || !certificate().equals(certificate))
      bad("原应用已更新或签名不匹配，请重新检查适配");
  }

  public static void selector(JSONObject s) {
    if (s == null) bad("缺少控件定位");
    keys(s, "viewId,text,description,className");
    String id = string(s, "viewId", 240, true),
        text = string(s, "text", 180, true),
        desc = string(s, "description", 180, true);
    string(s, "className", 160, false);
    if (id.isEmpty() && text.isEmpty() && desc.isEmpty()) bad("控件缺少可重复定位的信息");
  }

  public static boolean matches(JSONObject s, JSONObject node) {
    for (String key : new String[] {"viewId", "text", "description", "className"})
      if (!s.optString(key).isEmpty() && !s.optString(key).equals(node.optString(key)))
        return false;
    return true;
  }

  public static int resolve(JSONObject s, List<JSONObject> nodes) {
    int found = -1;
    for (int i = 0; i < nodes.size(); i++)
      if (matches(s, nodes.get(i))) {
        if (found != -1) bad("匹配到多个控件，已停止；请重新示范");
        found = i;
      }
    if (found < 0) bad("当前页面找不到目标，请回原应用打开对应页面");
    return found;
  }

  public static boolean sensitive(String text) {
    String t = text.toLowerCase(Locale.ROOT);
    for (String term :
        new String[] {
          "密码",
          "验证码",
          "支付",
          "付款",
          "转账",
          "购买",
          "充值",
          "删除",
          "注销",
          "发送",
          "提交订单",
          "password",
          "verification code",
          "payment",
          "checkout",
          "purchase",
          "delete",
          "transfer",
          "send message"
        }) if (t.contains(term)) return true;
    return false;
  }

  private static void page(JSONObject o) {
    if (!string(o, "page", 64, false).matches("[0-9a-f]{64}")) bad("缺少页面结构基线");
  }

  public static String pageSignature(List<JSONObject> nodes) {
    List<String> shape = new ArrayList<>();
    Set<String> unique = new HashSet<>();
    for (JSONObject n : nodes)
      unique.add(
          n.optString("viewId")
              + "|"
              + n.optString("className")
              + "|"
              + n.optBoolean("click")
              + "|"
              + n.optBoolean("input")
              + "|"
              + n.optBoolean("scroll"));
    shape.addAll(unique);
    Collections.sort(shape);
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(String.join("\n", shape).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void bool(JSONObject o, String key) {
    if (!(o.opt(key) instanceof Boolean)) bad("布尔值无效：" + key);
  }

  private static String string(JSONObject o, String key, int max, boolean empty) {
    Object v = o.opt(key);
    if (!(v instanceof String)) bad("文本无效：" + key);
    String s = (String) v;
    if (s.length() > max || (!empty && s.trim().isEmpty()) || s.indexOf('\u0000') >= 0)
      bad("文本长度无效：" + key);
    return s;
  }

  private static void keys(JSONObject o, String allowed) {
    Set<String> names = new HashSet<>(Arrays.asList(allowed.split(",")));
    for (Iterator<String> it = o.keys(); it.hasNext(); )
      if (!names.contains(it.next())) bad("包含未允许的能力");
  }

  private static void bad(String message) {
    throw new IllegalArgumentException(message);
  }
}

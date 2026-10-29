package dev.livingapps.core;

import static dev.livingapps.core.Messages.tr;

import org.json.JSONArray;
import org.json.JSONObject;

public final class J {
  private J() {}

  public static JSONObject obj(Object... pairs) {
    try {
      JSONObject result = new JSONObject();
      for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
      return result;
    } catch (Exception e) {
      throw new IllegalArgumentException(tr("JSON 数据无效"), e);
    }
  }

  public static JSONArray arr(Object... values) {
    JSONArray result = new JSONArray();
    for (Object value : values) result.put(value);
    return result;
  }

  public static void put(JSONObject object, String key, Object value) {
    try {
      object.put(key, value);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static JSONObject parse(String text) {
    try {
      return new JSONObject(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(tr("返回内容不是有效 JSON"), e);
    }
  }

  public static String cleanJson(String text) {
    String value = text.trim();
    if (value.startsWith("```")) {
      int first = value.indexOf('\n'), last = value.lastIndexOf("```");
      if (first >= 0 && last > first) value = value.substring(first + 1, last).trim();
    }
    return value;
  }
}

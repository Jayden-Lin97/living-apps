package dev.livingapps.core;

import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;

public final class Templates {
  private static JSONObject field(
      String id, String label, String type, boolean required, String... options) {
    JSONObject f =
        J.obj("id", id, "label", label, "type", type, "required", required, "placeholder", "");
    if (options.length > 0) J.put(f, "options", J.arr((Object[]) options));
    return f;
  }

  private static AppSpec make(
      String name,
      String description,
      String icon,
      String accent,
      String title,
      String metric,
      String unit,
      JSONObject... fields) {
    return AppSpec.parse(
        J.obj(
                "schemaVersion",
                1,
                "name",
                name,
                "description",
                description,
                "icon",
                icon,
                "accent",
                accent,
                "titleField",
                title,
                "metricField",
                metric,
                "unit",
                unit,
                "startScreen",
                "overview",
                "quickAdd",
                false,
                "compact",
                false,
                "fields",
                J.arr((Object[]) fields))
            .toString());
  }

  public static List<AppSpec> all() {
    return Arrays.asList(
        make(
            "随手记账",
            "只记你关心的每一笔，慢慢看清生活。",
            "◈",
            "#176B56",
            "item",
            "amount",
            "元",
            field("item", "花在哪里", "text", true),
            field("amount", "金额", "number", true),
            field("category", "分类", "choice", true, "餐饮", "交通", "购物", "其他"),
            field("date", "日期", "date", true)),
        make(
            "喝水日记",
            "记录一杯水，也照顾一下自己。",
            "◉",
            "#356E9D",
            "date",
            "volume",
            "ml",
            field("volume", "饮水量", "number", true),
            field("date", "日期", "date", true),
            field("note", "此刻的感受", "text", false)),
        make(
            "阅读花园",
            "把读过的页数和想到的句子留下来。",
            "▤",
            "#967245",
            "book",
            "pages",
            "页",
            field("book", "书名", "text", true),
            field("pages", "阅读页数", "number", true),
            field("thought", "随想", "text", false),
            field("date", "日期", "date", true)));
  }

  /** Localize freshly created built-ins only. Never call this on a saved user definition. */
  public static List<AppSpec> localized() {
    List<AppSpec> result = new java.util.ArrayList<>();
    for (AppSpec original : all()) {
      JSONObject spec = original.json();
      for (String key : new String[] {"name", "description", "unit"})
        J.put(spec, key, Messages.tr(spec.optString(key)));
      org.json.JSONArray fields = spec.optJSONArray("fields");
      for (int i = 0; i < fields.length(); i++) {
        JSONObject field = fields.optJSONObject(i);
        J.put(field, "label", Messages.tr(field.optString("label")));
        org.json.JSONArray options = field.optJSONArray("options");
        if (options != null) {
          org.json.JSONArray localizedOptions = J.arr();
          for (int j = 0; j < options.length(); j++)
            localizedOptions.put(Messages.tr(options.optString(j)));
          J.put(field, "options", localizedOptions);
        }
      }
      result.add(AppSpec.parse(spec.toString()));
    }
    return result;
  }
}

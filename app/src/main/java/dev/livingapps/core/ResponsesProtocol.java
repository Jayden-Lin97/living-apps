package dev.livingapps.core;

import java.net.URI;
import java.util.*;
import org.json.*;

public final class ResponsesProtocol {
  public static String validateBase(String base) {
    try {
      URI uri = new URI(base.trim());
      if (uri.getHost() == null
          || uri.getRawUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null) throw new IllegalArgumentException();
      boolean https = "https".equalsIgnoreCase(uri.getScheme());
      boolean allowedHttp =
          "http".equalsIgnoreCase(uri.getScheme())
              && Arrays.asList("localhost", "127.0.0.1", "10.0.2.2")
                  .contains(uri.getHost());
      if (!https && !allowedHttp) throw new IllegalArgumentException();
      return base.trim().replaceAll("/+$", "");
    } catch (Exception e) {
      throw new IllegalArgumentException("请配置 HTTPS API 地址；HTTP 仅限本机开发，地址不能包含账号、查询串或片段");
    }
  }

  public static JSONObject request(String model, String instructions, String input) {
    if (model.trim().isEmpty() || model.length() > 120)
      throw new IllegalArgumentException("模型名称无效");
    return J.obj(
        "model",
        model,
        "store",
        false,
        "stream",
        false,
        "max_output_tokens",
        6000,
        "reasoning",
        J.obj("effort", "low"),
        "instructions",
        instructions,
        "input",
        input);
  }

  public static String outputText(String response) {
    JSONObject json = J.parse(response);
    if (json.has("error")
        || Arrays.asList("failed", "incomplete", "cancelled").contains(json.optString("status")))
      throw new IllegalArgumentException("模型未完整返回结果，请重试；当前应用没有改变");
    StringBuilder text = new StringBuilder();
    JSONArray output = json.optJSONArray("output");
    if (output != null)
      for (int i = 0; i < output.length(); i++) {
        JSONObject item = output.optJSONObject(i);
        if (item == null || !item.optString("type").equals("message")) continue;
        JSONArray content = item.optJSONArray("content");
        if (content == null) continue;
        for (int k = 0; k < content.length(); k++) {
          JSONObject block = content.optJSONObject(k);
          if (block != null && block.optString("type").equals("output_text"))
            text.append(block.optString("text"));
        }
      }
    if (text.length() == 0) text.append(json.optString("output_text"));
    if (text.length() == 0 || text.length() > 64000)
      throw new IllegalArgumentException("模型返回内容为空或过大");
    return text.toString();
  }

  public static final class Generated {
    public final AppSpec spec;
    public final String explanation;

    public Generated(AppSpec spec, String explanation) {
      this.spec = spec;
      this.explanation = explanation;
    }
  }

  public static Generated generated(String text) {
    JSONObject envelope = J.parse(J.cleanJson(text));
    if (envelope.has("error")) throw new IllegalArgumentException("该需求超出当前记录、表单与统计运行时的能力，请缩小范围");
    JSONObject spec = envelope.optJSONObject("app");
    if (spec == null) throw new IllegalArgumentException("模型没有返回 app 描述");
    return new Generated(
        AppSpec.parse(spec.toString()), envelope.optString("explanation", "已生成修改，请预览后采用。"));
  }
}

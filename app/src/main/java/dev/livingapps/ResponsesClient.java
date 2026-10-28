package dev.livingapps;

import dev.livingapps.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class ResponsesClient {
  private final String base, key;
  private volatile HttpURLConnection active;

  public ResponsesClient(String base, String key) {
    this.base = ResponsesProtocol.validateBase(base);
    this.key = key;
    if (key.trim().isEmpty()) throw new IllegalArgumentException("请先在设置中填写 API Key");
  }

  public void cancel() {
    HttpURLConnection c = active;
    if (c != null) c.disconnect();
  }

  private String request(String path, JSONObject body) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
    active = connection;
    try {
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(150000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("Authorization", "Bearer " + key);
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Accept", "application/json");
      if (body != null) {
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
          output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
      }
      int status = connection.getResponseCode();
      if (status < 200 || status >= 300)
        throw new IOException("API 返回 HTTP " + status + "。请检查地址、模型及凭据；应用未改变。");
      try (InputStream input = connection.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
          if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
          if (out.size() + n > 2_000_000) throw new IOException("API 响应过大");
          out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
      }
    } finally {
      connection.disconnect();
      active = null;
    }
  }

  public List<String> models() throws Exception {
    JSONObject response = J.parse(request("/models", null));
    JSONArray data = response.optJSONArray("data");
    if (data == null) throw new IOException("模型发现响应格式不正确");
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < data.length(); i++) {
      JSONObject item = data.optJSONObject(i);
      if (item != null && !item.optString("id").isEmpty()) ids.add(item.optString("id"));
    }
    return ids;
  }

  public ResponsesProtocol.Generated generate(String model, String instructions, String input)
      throws Exception {
    return ResponsesProtocol.generated(
        ResponsesProtocol.outputText(
            request("/responses", ResponsesProtocol.request(model, instructions, input))));
  }
}

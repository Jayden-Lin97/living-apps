package dev.livingapps.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONObject;

/** Explicitly authored system copy only. Never pass business values or whole Views to this API. */
public final class Messages {
  private static final JSONObject ENGLISH = load();

  private Messages() {}

  private static JSONObject load() {
    try (InputStream in = Messages.class.getResourceAsStream("messages.en.json");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      if (in == null) throw new IllegalStateException("English catalog missing");
      byte[] buffer = new byte[4096];
      int count;
      while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
      return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
    } catch (Exception error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  public static boolean isChinese() {
    return Locale.getDefault().getLanguage().equals("zh");
  }

  public static String tr(String authoredChinese) {
    return isChinese() ? authoredChinese : ENGLISH.optString(authoredChinese, authoredChinese);
  }

  public static boolean hasTranslation(String authoredChinese) {
    return ENGLISH.has(authoredChinese);
  }

  public static boolean isWaitingStatus(String status) {
    // Legacy draft messages can have been written in either UI language.
    String key = "正在等待模型；可取消，或继续本地调整";
    return status.startsWith("正在") || status.equals(ENGLISH.optString(key));
  }
}

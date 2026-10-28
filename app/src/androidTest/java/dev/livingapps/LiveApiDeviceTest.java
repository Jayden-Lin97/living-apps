package dev.livingapps;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.livingapps.core.*;
import java.io.*;
import java.time.LocalDate;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Explicit opt-in: paid API calls only when a key is passed to instrumentation. */
@RunWith(AndroidJUnit4.class)
public class LiveApiDeviceTest {
  @Test
  public void realAndroidNetworkGenerationEditAndRestore() throws Exception {
    String key = InstrumentationRegistry.getArguments().getString("liveApiKey", "");
    String base = InstrumentationRegistry.getArguments().getString("liveApiBase", "");
    Assume.assumeFalse("未提供地址或凭据时跳过付费 API 测试", key.isEmpty() || base.isEmpty());
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String instructions;
    try (InputStream input = context.getAssets().open("generation-instructions.txt");
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
      instructions = out.toString("UTF-8");
    }
    ResponsesClient client = new ResponsesClient(base, key);
    assertTrue(client.models().contains("gpt-6.1-sol"));
    ResponsesProtocol.Generated generated =
        client.generate(
            "gpt-6.1-sol",
            instructions,
            "Create a simple personal reading log with book title, page count and date. Use Chinese"
                + " labels.");
    Store store = ((LivingApplication) context.getApplicationContext()).store;
    String id = store.create(generated.spec, "device-test");
    try {
      Store.App original = store.app(id);
      JSONObject record = J.obj();
      for (AppSpec.Field f : original.spec.fields()) {
        Object value;
        switch (f.type) {
          case "number":
            value = 5;
            break;
          case "boolean":
            value = false;
            break;
          case "date":
            value = LocalDate.now().toString();
            break;
          case "choice":
            value = f.options.get(0);
            break;
          default:
            value = "Device proof";
        }
        J.put(record, f.id, value);
      }
      store.saveRecord(id, original.versionId, null, record);
      ResponsesProtocol.Generated edited =
          client.generate(
              "gpt-6.1-sol",
              instructions,
              "修改这个应用：开启快速记录入口，增加一个可选的阅读感想文本字段。保留全部已有字段ID和类型。当前应用：" + original.spec);
      assertTrue(
          VersionPolicy.errors(original.spec, edited.spec).toString(),
          VersionPolicy.errors(original.spec, edited.spec).isEmpty());
      assertTrue(edited.spec.quickAdd());
      String next = store.apply(id, original.versionId, edited.spec, "真实设备 API 修改", "llm", false);
      assertEquals(1, store.records(id).size());
      store.apply(id, next, original.spec, "恢复", "restore", true);
      assertEquals(1, store.records(id).size());
      assertEquals(3, store.versions(id).size());
    } finally {
      store.deleteApp(id);
    }
  }
}

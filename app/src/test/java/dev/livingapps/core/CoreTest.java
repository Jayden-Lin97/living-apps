package dev.livingapps.core;

import static org.junit.Assert.*;

import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class CoreTest {
  private final java.util.Locale previousTestLocale = java.util.Locale.getDefault();

  @org.junit.Before
  public void selectChineseCopy() {
    java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE);
  }

  @org.junit.After
  public void restoreCopyLocale() {
    java.util.Locale.setDefault(previousTestLocale);
  }

  private AppSpec base() {
    return Templates.all().get(0);
  }

  private void rejected(Runnable task) {
    assertThrows(IllegalArgumentException.class, task::run);
  }

  @Test
  public void templatesAreValidAndIsolated() {
    for (AppSpec spec : Templates.all()) {
      assertEquals(4, spec.fields().size() + (spec.name().equals("喝水日记") ? 1 : 0));
      assertEquals(spec.toString(), AppSpec.parse(spec.toString()).toString());
    }
    AppSpec changed = base().with("name", "个人副本");
    assertNotEquals(changed.name(), base().name());
  }

  @Test
  public void arbitraryCodeIsRejected() {
    JSONObject raw = base().json();
    J.put(raw, "onLoad", "fetch('https://example.com')");
    rejected(() -> AppSpec.parse(raw.toString()));
  }

  @Test
  public void invalidSchemaVersionIsRejected() {
    rejected(() -> base().with("schemaVersion", 3));
  }

  @Test
  public void schemaVersionStringsAreRejected() {
    rejected(() -> base().with("schemaVersion", "1"));
  }

  @Test
  public void requiredDefaultsCannotBeEmpty() {
    JSONObject raw = base().json();
    J.put(raw.optJSONArray("fields").optJSONObject(0), "defaultValue", "");
    rejected(() -> AppSpec.parse(raw.toString()));
  }

  @Test
  public void duplicateStableIdsAreRejected() {
    JSONObject raw = base().json();
    JSONArray fields = raw.optJSONArray("fields");
    J.put(fields.optJSONObject(1), "id", "item");
    rejected(() -> AppSpec.parse(raw.toString()));
  }

  @Test
  public void invalidDefaultValuesAreRejected() {
    JSONObject raw = base().json();
    J.put(raw.optJSONArray("fields").optJSONObject(1), "defaultValue", "not a number");
    rejected(() -> AppSpec.parse(raw.toString()));
  }

  @Test
  public void unknownMetricIsRejected() {
    rejected(() -> base().with("metricField", "missing"));
  }

  @Test
  public void booleanStringsAreNotBooleans() {
    rejected(() -> base().with("quickAdd", "false"));
  }

  @Test
  public void stableIdsAndTypesCannotBeBroken() {
    JSONObject raw = base().json();
    J.put(raw.optJSONArray("fields").optJSONObject(0), "id", "renamed");
    J.put(raw, "titleField", "renamed");
    assertFalse(VersionPolicy.errors(base(), AppSpec.parse(raw.toString())).isEmpty());
    raw = base().json();
    J.put(raw.optJSONArray("fields").optJSONObject(0), "type", "number");
    assertFalse(VersionPolicy.errors(base(), AppSpec.parse(raw.toString())).isEmpty());
  }

  @Test
  public void removingChoiceOptionsIsIncompatible() {
    JSONObject raw = base().json();
    J.put(raw.optJSONArray("fields").optJSONObject(2), "options", J.arr("其他"));
    assertFalse(VersionPolicy.errors(base(), AppSpec.parse(raw.toString())).isEmpty());
  }

  @Test
  public void requiredAdditionsNeedDefaultForExistingRecords() {
    JSONObject raw = base().json();
    JSONObject added = J.obj("id", "project", "label", "项目", "type", "text", "required", true);
    raw.optJSONArray("fields").put(added);
    assertFalse(VersionPolicy.errors(base(), AppSpec.parse(raw.toString())).isEmpty());
    J.put(added, "defaultValue", "未分类");
    assertTrue(VersionPolicy.errors(base(), AppSpec.parse(raw.toString())).isEmpty());
  }

  @Test
  public void cosmeticAndOptionalAdditionsAreCompatible() {
    JSONObject raw = base().with("quickAdd", true).json();
    raw.optJSONArray("fields")
        .put(J.obj("id", "note", "label", "备注", "type", "text", "required", false));
    assertTrue(VersionPolicy.errors(base(), AppSpec.parse(raw.toString())).isEmpty());
  }

  @Test
  public void autoApplyCannotHideAdditionalModelChanges() {
    assertTrue(VersionPolicy.autoAllowed(base(), base().with("quickAdd", true)));
    assertFalse(
        VersionPolicy.autoAllowed(base(), base().with("quickAdd", true).with("name", "偷偷改名")));
    assertFalse(VersionPolicy.autoAllowed(base(), base().with("compact", true)));
  }

  @Test
  public void recordsRejectWrongTypesAndCalendarDates() {
    JSONObject record = J.obj("item", "午饭", "amount", 12.5, "category", "餐饮", "date", "2026-10-27");
    assertTrue(base().validateRecord(record).isEmpty());
    J.put(record, "amount", "12.5");
    assertFalse(base().validateRecord(record).isEmpty());
    J.put(record, "amount", 12.5);
    J.put(record, "date", "2026-02-30");
    assertFalse(base().validateRecord(record).isEmpty());
  }

  @Test
  public void oneDayOfUseCannotTriggerOptimization() {
    List<ReflectionEngine.Event> events = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      events.add(new ReflectionEngine.Event("open", 0, 1000));
      events.add(new ReflectionEngine.Event("save", 1000, 1000));
    }
    assertTrue(ReflectionEngine.suggest(base(), ReflectionEngine.summarize(events)).isEmpty());
  }

  @Test
  public void repeatedRealEventsProduceQuickAddSuggestion() {
    List<ReflectionEngine.Event> events = events();
    List<ReflectionEngine.Suggestion> suggestions =
        ReflectionEngine.suggest(base(), ReflectionEngine.summarize(events));
    assertEquals(1, suggestions.size());
    assertTrue(suggestions.get(0).canAutoApply);
    assertTrue(suggestions.get(0).candidate.quickAdd());
    assertTrue(
        ReflectionEngine.suggest(suggestions.get(0).candidate, ReflectionEngine.summarize(events))
            .isEmpty());
  }

  @Test
  public void noDataNeverClaimsImprovement() {
    ReflectionEngine.Summary empty = ReflectionEngine.summarize(new ArrayList<>());
    assertTrue(ReflectionEngine.evaluation(empty, empty).contains("不足"));
  }

  @Test
  public void medianIgnoresSingleSlowOutlier() {
    List<ReflectionEngine.Event> events = new ArrayList<>();
    for (long n : new long[] {1000, 1100, 1200, 1300, 3500000})
      events.add(new ReflectionEngine.Event("save", n, 1));
    assertEquals(1200, ReflectionEngine.summarize(events).medianMillis());
  }

  @Test
  public void requestExplicitlyDisablesResponseStorage() {
    JSONObject request = ResponsesProtocol.request("gpt-6.1-sol", "system", "hello");
    assertFalse(request.optBoolean("store", true));
    assertFalse(request.optBoolean("stream", true));
    assertFalse(request.toString().contains("api_key"));
  }

  @Test
  public void outputParserSkipsReasoningAndJoinsMessageBlocks() {
    JSONObject response =
        J.obj(
            "status",
            "completed",
            "output",
            J.arr(
                J.obj("type", "reasoning", "summary", J.arr()),
                J.obj(
                    "type",
                    "message",
                    "content",
                    J.arr(
                        J.obj("type", "output_text", "text", "first"),
                        J.obj("type", "output_text", "text", "second")))));
    assertEquals("firstsecond", ResponsesProtocol.outputText(response.toString()));
  }

  @Test
  public void incompleteOutputCannotBeUsed() {
    rejected(
        () ->
            ResponsesProtocol.outputText(
                J.obj("status", "incomplete", "output_text", "{} ").toString()));
  }

  @Test
  public void credentialsInUrlAndUnexpectedHttpAreRejected() {
    assertEquals(
        "http://127.0.0.1:8080/v1", ResponsesProtocol.validateBase("http://127.0.0.1:8080/v1/"));
    assertEquals(
        "https://api.example.com/v1", ResponsesProtocol.validateBase("https://api.example.com/v1"));
    rejected(() -> ResponsesProtocol.validateBase("https://secret@example.com/v1"));
    rejected(() -> ResponsesProtocol.validateBase("http://untrusted.example/v1"));
    rejected(() -> ResponsesProtocol.validateBase(""));
    rejected(() -> ResponsesProtocol.validateBase("file:///etc/passwd"));
  }

  @Test
  public void fencedResponsesAreSupported() {
    String json = J.obj("app", base().json(), "explanation", "完成").toString();
    assertEquals(
        base().name(), ResponsesProtocol.generated("```json\n" + json + "\n```").spec.name());
  }

  @Test
  public void realGatewayGenerationFixtureIsValid() throws Exception {
    try (java.io.InputStream in = getClass().getResourceAsStream("/live-generated.json")) {
      assertNotNull("真实网关生成样本必须存在", in);
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      ResponsesProtocol.Generated generated = ResponsesProtocol.generated(out.toString("UTF-8"));
      assertFalse(generated.spec.fields().isEmpty());
      assertEquals("records", generated.spec.startScreen());
    }
  }

  @Test
  public void realGatewayEditPreservesCompatibility() throws Exception {
    AppSpec before = fixture("/live-generated.json"), after = fixture("/live-edited.json");
    assertTrue(VersionPolicy.errors(before, after).isEmpty());
    assertTrue(after.quickAdd());
    assertNotNull(after.field("weather"));
    assertEquals(before.fields().size() + 1, after.fields().size());
  }

  private AppSpec fixture(String name) throws Exception {
    try (java.io.InputStream in = getClass().getResourceAsStream(name)) {
      assertNotNull(in);
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      return ResponsesProtocol.generated(out.toString("UTF-8")).spec;
    }
  }

  private List<ReflectionEngine.Event> events() {
    List<ReflectionEngine.Event> events = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      long time = i < 3 ? 1000 : ReflectionEngine.DAY + 1000;
      events.add(new ReflectionEngine.Event("open", 0, time));
      events.add(new ReflectionEngine.Event("save", 2000, time));
    }
    return events;
  }
}

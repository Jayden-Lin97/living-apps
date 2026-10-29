package dev.livingapps.core;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class ExtendedEditingTest {
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

  private AppSpec fixture(String name) throws Exception {
    try (java.io.InputStream input = getClass().getResourceAsStream(name);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      assertNotNull(input);
      byte[] bytes = new byte[4096];
      int count;
      while ((count = input.read(bytes)) != -1) out.write(bytes, 0, count);
      return ResponsesProtocol.generated(out.toString("UTF-8")).spec;
    }
  }

  @Test
  public void realScopedEditorResponseUpgradesFormatAndStaysInsideSelection() throws Exception {
    AppSpec before = fixture("/live-generated.json"), after = fixture("/live-editor-session.json");
    assertEquals(2, after.json().optInt("schemaVersion"));
    assertEquals("跑了多远", after.field("distance").label);
    assertEquals("运动数据", after.field("distance").group);
    assertTrue(EditChanges.withinTarget(before, after, "field:distance"));
    assertTrue(VersionPolicy.errors(before, after).isEmpty());
  }

  private AppSpec preset() {
    JSONObject root = base().json();
    J.put(root, "schemaVersion", 2);
    J.put(
        root,
        "actions",
        J.arr(
            J.obj(
                "id",
                "lunch",
                "label",
                "记午饭",
                "kind",
                "add_record",
                "values",
                J.obj("item", "午饭", "amount", 20, "category", "餐饮", "date", "$today"))));
    return AppSpec.parse(root.toString());
  }

  private void reject(Runnable task) {
    try {
      task.run();
      fail("应拒绝");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void legacyFormatRejectsNewCapabilitiesUntilExplicitUpgrade() {
    JSONObject raw = preset().json();
    J.put(raw, "schemaVersion", 1);
    reject(() -> AppSpec.parse(raw.toString()));
  }

  @Test
  public void groupedAndHiddenFieldsKeepStableIdsAndValues() {
    EditSession session = new EditSession("app", "v", base());
    AppSpec next = session.field("amount", "group", "基本信息");
    session.apply(next, "分组");
    session.apply(session.field("category", "defaultValue", "其他"), "默认值");
    next = session.field("category", "hidden", true);
    assertTrue(next.field("category").hidden);
    assertEquals("基本信息", next.field("amount").group);
    assertTrue(VersionPolicy.errors(base(), next).isEmpty());
  }

  @Test
  public void requiredHiddenOrTitleFieldsCannotBecomeImpossibleToFill() {
    EditSession session = new EditSession("app", "v", base());
    reject(() -> session.field("item", "hidden", true));
    reject(() -> session.field("amount", "hidden", true));
  }

  @Test
  public void actionsResolveOnlyTypedLocalValuesAndToday() {
    ActionSpec action = preset().actions().get(0);
    JSONObject row = action.resolved(preset());
    assertEquals(20, row.optInt("amount"));
    assertEquals(java.time.LocalDate.now().toString(), row.optString("date"));
    assertTrue(preset().validateRecord(row).isEmpty());
  }

  @Test
  public void actionsRejectUnknownCapabilitiesAndIncompleteRecords() {
    JSONObject raw = preset().json();
    JSONObject action = raw.optJSONArray("actions").optJSONObject(0);
    J.put(action, "script", "run()");
    reject(() -> AppSpec.parse(raw.toString()));
    action.remove("script");
    J.put(action, "kind", "http");
    reject(() -> AppSpec.parse(raw.toString()));
    J.put(action, "kind", "add_record");
    J.put(action, "values", J.obj());
    reject(() -> AppSpec.parse(raw.toString()));
  }

  @Test
  public void lockedActionCannotChangeOtherAppProperties() {
    AppSpec before = preset();
    JSONObject raw = before.json();
    J.put(raw.optJSONArray("actions").optJSONObject(0), "label", "快捷午饭");
    AppSpec after = AppSpec.parse(raw.toString());
    assertTrue(EditChanges.withinTarget(before, after, "action:lunch"));
    assertFalse(EditChanges.withinTarget(before, after.with("compact", true), "action:lunch"));
  }

  @Test
  public void unitConversionTransformsRecordDefaultsAndPresetTogether() {
    AppSpec before = preset();
    EditSession session = new EditSession("a", "v", before);
    before = session.field("amount", "defaultValue", 3);
    MigrationPlan plan = MigrationPlan.scale(before, "amount", 100, "分");
    assertEquals(300, ((Number) plan.after.field("amount").defaultValue).intValue());
    assertEquals(2000, plan.after.actions().get(0).resolved(plan.after).optInt("amount"));
    MigrationPlan.Result result =
        plan.evaluate(
            Arrays.asList(
                J.obj("amount", 2.5, "older_hidden_value", "keep"),
                J.obj("amount", JSONObject.NULL),
                J.obj()));
    assertTrue(result.allowed());
    assertEquals(250, result.records.get(0).optInt("amount"));
    assertEquals("keep", result.records.get(0).optString("older_hidden_value"));
    assertEquals(1, result.empty);
    assertEquals(1, result.missing);
  }

  @Test
  public void invalidTextConversionsAreCountedAndNeverPretendSuccess() {
    MigrationPlan plan = MigrationPlan.type(base(), "item", "number");
    MigrationPlan.Result result =
        plan.evaluate(
            Arrays.asList(J.obj("item", "12.5"), J.obj("item", "午饭"), J.obj("item", "NaN")));
    assertEquals(1, result.converted);
    assertEquals(2, result.failed);
    assertFalse(result.allowed());
  }

  @Test
  public void numberToTextClearsOnlyItsObsoleteMetricReference() {
    MigrationPlan plan = MigrationPlan.type(base(), "amount", "text");
    assertEquals("", plan.after.metricField());
    assertEquals("", plan.after.unit());
    assertEquals(
        "12.5",
        plan.evaluate(Collections.singletonList(J.obj("amount", 12.5)))
            .records
            .get(0)
            .optString("amount"));
    assertFalse(VersionPolicy.errors(base(), plan.after).isEmpty());
  }

  @Test
  public void choiceMappingRequiresCompleteMappingAndPreservesNoUnmappedValue() {
    Map<String, String> map = new LinkedHashMap<>();
    for (String option : base().field("category").options)
      map.put(option, option.equals("餐饮") ? "饮食" : option);
    MigrationPlan plan = MigrationPlan.choices(base(), "category", map);
    MigrationPlan.Result result =
        plan.evaluate(Arrays.asList(J.obj("category", "餐饮"), J.obj("category", "丢失的分类")));
    assertEquals("饮食", result.records.get(0).optString("category"));
    assertEquals(1, result.failed);
    map.remove("餐饮");
    reject(() -> MigrationPlan.choices(base(), "category", map));
  }
}

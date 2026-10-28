package dev.livingapps.core;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class EditingTest {
  private AppSpec original() {
    return Templates.all().get(0);
  }

  @Test
  public void draftRoundTripKeepsUndoRedoAndPrivateTrialSeparateFromModelContext() {
    EditSession s = new EditSession("app", "v1", original());
    s.apply(s.field("item", "label", "消费内容"), "改名称");
    s.prompt = "增加备注";
    s.trial.put(J.obj("data", J.obj("item", "VERY_PRIVATE_RECORD")));
    s.message("user", "改名称");
    EditSession copy = EditSession.parse(s.json().toString());
    assertEquals("消费内容", copy.spec.field("item").label);
    assertTrue(copy.canUndo());
    assertFalse(copy.requestContext().toString().contains("VERY_PRIVATE_RECORD"));
    copy.undo();
    assertEquals("花在哪里", copy.spec.field("item").label);
    copy.redo();
    assertEquals("消费内容", copy.spec.field("item").label);
  }

  @Test
  public void noOpDoesNotCreateHistoryOrAdvanceRevision() {
    EditSession s = new EditSession("app", "v1", original());
    s.apply(original(), "无变化");
    assertEquals(0, s.revision);
    assertFalse(s.canUndo());
  }

  @Test
  public void movingSelectedFieldOnlyStaysInItsScope() {
    EditSession s = new EditSession("app", "v1", original());
    AppSpec next = s.move("amount", 1);
    assertTrue(EditChanges.withinField(s.spec, next, "amount"));
    assertFalse(EditChanges.withinField(s.spec, next, "item"));
  }

  @Test
  public void unrelatedChangesCannotHideInsideFieldRequest() {
    EditSession s = new EditSession("app", "v1", original());
    assertFalse(
        EditChanges.withinField(
            s.spec, s.field("item", "label", "内容").with("compact", true), "item"));
  }

  @Test
  public void disjointChangesMergeWithoutLosingLocalWork() {
    EditSession s = new EditSession("app", "v1", original());
    AppSpec local = s.field("item", "label", "本地名称");
    EditChanges.Merge result =
        EditChanges.merge(s.base, local, s.base.with("compact", true), Collections.emptyMap());
    assertTrue(result.conflicts.isEmpty());
    assertTrue(result.spec.compact());
    assertEquals("本地名称", result.spec.field("item").label);
  }

  @Test
  public void samePropertyConflictRequiresAnExplicitChoice() {
    AppSpec base = original();
    EditChanges.Merge result =
        EditChanges.merge(
            base, base.with("name", "我的版本"), base.with("name", "模型版本"), Collections.emptyMap());
    assertEquals(1, result.conflicts.size());
    assertEquals("我的版本", result.spec.name());
    Map<String, Boolean> choose = new HashMap<>();
    choose.put("app/name", true);
    result = EditChanges.merge(base, base.with("name", "我的版本"), base.with("name", "模型版本"), choose);
    assertTrue(result.conflicts.isEmpty());
    assertEquals("模型版本", result.spec.name());
  }

  @Test
  public void removedPublishedFieldAndTypeChangeAreRejected() {
    EditSession s = new EditSession("app", "v1", original());
    try {
      s.apply(s.field("amount", "type", "text"), "换类型");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(0, s.revision);
  }

  @Test
  public void explicitEmptyExistingValueCannotBeSilentlyReplacedByNewDefault() {
    EditSession s = new EditSession("app", "v1", original());
    JSONObject raw = s.spec.json();
    raw.optJSONArray("fields")
        .put(J.obj("id", "note", "label", "备注", "type", "text", "required", false));
    AppSpec before = AppSpec.parse(raw.toString());
    EditSession n = new EditSession("app", "v1", before);
    AppSpec next = n.field("note", "defaultValue", "默认内容");
    n.spec = next;
    next = n.field("note", "required", true);
    JSONObject explicit =
        J.obj(
            "item",
            "午饭",
            "amount",
            12,
            "category",
            "餐饮",
            "date",
            "2026-10-27",
            "note",
            JSONObject.NULL);
    EditChanges.Impact impact = EditChanges.impact(before, next, Arrays.asList(explicit));
    assertFalse(impact.allowed());
    assertEquals(1, impact.invalid);
    explicit.remove("note");
    assertTrue(EditChanges.impact(before, next, Arrays.asList(explicit)).allowed());
  }

  @Test
  public void additionsMergeWithoutDroppingAnUnrelatedLocalField() {
    AppSpec base = original();
    JSONObject local = base.json(), incoming = base.json();
    local
        .optJSONArray("fields")
        .put(J.obj("id", "local_note", "label", "本地备注", "type", "text", "required", false));
    incoming
        .optJSONArray("fields")
        .put(J.obj("id", "ai_note", "label", "模型备注", "type", "text", "required", false));
    EditChanges.Merge merged =
        EditChanges.merge(
            base,
            AppSpec.parse(local.toString()),
            AppSpec.parse(incoming.toString()),
            Collections.singletonMap("order", true));
    assertNotNull(merged.spec.field("local_note"));
    assertNotNull(merged.spec.field("ai_note"));
  }

  @Test
  public void undoingUnpublishedFieldAdditionIsAllowed() {
    EditSession s = new EditSession("app", "v1", original());
    JSONObject raw = s.spec.json();
    raw.optJSONArray("fields")
        .put(J.obj("id", "note", "label", "备注", "type", "text", "required", false));
    s.apply(AppSpec.parse(raw.toString()), "添加备注");
    s.undo();
    assertNull(s.spec.field("note"));
    s.redo();
    assertNotNull(s.spec.field("note"));
  }
}

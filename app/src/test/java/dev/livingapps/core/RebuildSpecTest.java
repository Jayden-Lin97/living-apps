package dev.livingapps.core;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class RebuildSpecTest {
  private static final String CERT = "a".repeat(64);

  private JSONObject selector() {
    return J.obj(
        "viewId", "music:id/title", "text", "", "description", "", "className", "TextView");
  }

  private RebuildSpec base() {
    return RebuildSpec.create("My music", "example.music", 7, CERT);
  }

  private JSONObject read() {
    return J.obj(
        "id",
        "title",
        "label",
        "Now playing",
        "kind",
        "read",
        "selector",
        selector(),
        "page",
        "b".repeat(64));
  }

  private void rejects(Runnable action) {
    try {
      action.run();
      fail("Must reject");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void generatedBindingsCannotAddScriptsOrUnknownActions() {
    JSONObject json = base().json();
    J.put(json, "script", "anything");
    rejects(() -> new RebuildSpec(json));
    JSONObject action =
        J.obj(
            "id",
            "play",
            "label",
            "Play",
            "kind",
            "action",
            "steps",
            J.arr(J.obj("op", "launch_url", "selector", selector(), "page", "b".repeat(64))));
    rejects(() -> base().with("widgets", J.arr(action)));
  }

  @Test
  public void targetIdentityMustMatchExactly() {
    base().assertTarget("example.music", 7, CERT);
    rejects(() -> base().assertTarget("example.music", 8, CERT));
    rejects(() -> base().assertTarget("example.other", 7, CERT));
    rejects(() -> base().assertTarget("example.music", 7, "c".repeat(64)));
  }

  @Test
  public void selectorRequiresUniqueSemanticMatch() {
    JSONObject node =
        J.obj("viewId", "music:id/title", "className", "TextView", "text", "New song");
    assertEquals(0, RebuildSpec.resolve(selector(), Arrays.asList(node)));
    rejects(() -> RebuildSpec.resolve(selector(), Arrays.asList(node, node)));
    rejects(() -> RebuildSpec.resolve(selector(), Collections.emptyList()));
  }

  @Test
  public void readBindingDoesNotPinOldDisplayedValue() {
    RebuildSpec spec = base().with("widgets", J.arr(read()));
    assertTrue(
        RebuildSpec.matches(
            spec.widget("title").optJSONObject("selector"),
            J.obj("viewId", "music:id/title", "className", "TextView", "text", "Different song")));
  }

  @Test
  public void workflowsAreBoundedAndInputsCannotPersistValues() {
    JSONArray steps = J.arr();
    for (int i = 0; i < 9; i++)
      steps.put(J.obj("op", "click", "selector", selector(), "page", "b".repeat(64)));
    rejects(
        () ->
            base()
                .with(
                    "widgets",
                    J.arr(J.obj("id", "play", "label", "Play", "kind", "action", "steps", steps))));
    JSONObject step =
        J.obj("op", "input", "selector", selector(), "page", "b".repeat(64), "value", "secret");
    rejects(
        () ->
            base()
                .with(
                    "widgets",
                    J.arr(
                        J.obj(
                            "id",
                            "search",
                            "label",
                            "Search",
                            "kind",
                            "input",
                            "steps",
                            J.arr(step)))));
  }

  @Test
  public void duplicateWidgetIdsAndNonBooleanSettingsAreRejected() {
    rejects(() -> base().with("widgets", J.arr(read(), read())));
    rejects(() -> base().with("dark", "true"));
    JSONObject r = read();
    J.put(r, "page", "");
    rejects(() -> base().with("widgets", J.arr(r)));
  }

  @Test
  public void modelContextExcludesSourceAppAndCapturedText() {
    JSONObject r = read();
    J.put(r, "label", "PRIVATE SONG");
    String input = RebuildLayout.context(base().with("widgets", J.arr(r)), "Use dark mode");
    assertFalse(input.contains("PRIVATE SONG"));
    assertFalse(input.contains("example.music"));
    assertFalse(input.contains("music:id"));
    assertFalse(input.contains(CERT));
    assertTrue(input.contains("component0"));
  }

  @Test
  public void modelCanOnlyReorderRenameAndRestyle() {
    RebuildSpec original = base().with("widgets", J.arr(read()));
    RebuildSpec result =
        RebuildLayout.apply(
            original,
            J.obj(
                    "name",
                    "Music",
                    "dark",
                    true,
                    "compact",
                    false,
                    "widgets",
                    J.arr(J.obj("id", "component0", "label", "Playing")))
                .toString());
    assertEquals(
        original.widget("title").optJSONObject("selector").toString(),
        result.widget("title").optJSONObject("selector").toString());
    assertEquals(CERT, result.certificate());
    assertTrue(result.dark());
    rejects(
        () ->
            RebuildLayout.apply(
                original,
                J.obj("name", "Music", "dark", true, "compact", false, "widgets", J.arr())
                    .toString()));
    rejects(
        () ->
            RebuildLayout.apply(
                original,
                J.obj(
                        "name",
                        "Music",
                        "dark",
                        true,
                        "compact",
                        false,
                        "widgets",
                        J.arr(
                            J.obj("id", "component0", "label", "Playing", "selector", selector())))
                    .toString()));
  }

  @Test
  public void pageSignatureToleratesListLengthButDetectsDifferentControls() {
    JSONObject row = J.obj("viewId", "id/title", "className", "TextView", "click", false);
    assertEquals(
        RebuildSpec.pageSignature(Arrays.asList(row)),
        RebuildSpec.pageSignature(Arrays.asList(row, row)));
    assertNotEquals(
        RebuildSpec.pageSignature(Arrays.asList(row)),
        RebuildSpec.pageSignature(
            Arrays.asList(
                row, J.obj("viewId", "id/payment", "className", "Button", "click", true))));
  }

  @Test
  public void sensitiveOperationsAreNotOrdinaryDeviceActions() {
    assertTrue(RebuildSpec.sensitive("确认支付"));
    assertTrue(RebuildSpec.sensitive("Delete account"));
    assertTrue(RebuildSpec.sensitive("输入验证码"));
    assertFalse(RebuildSpec.sensitive("下一首"));
  }

  @Test
  public void schemaAndPackageVersionCannotUseCoercedTypes() {
    rejects(() -> base().with("schema", "1"));
    rejects(() -> base().with("version", 7.5));
  }
}

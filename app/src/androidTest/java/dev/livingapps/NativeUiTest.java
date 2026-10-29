package dev.livingapps;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercise the actual native UI through Android accessibility, including dialogs. */
@RunWith(AndroidJUnit4.class)
public class NativeUiTest {
  @org.junit.Rule public final ChineseUiRule language = new ChineseUiRule();
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final UiAutomation automation = instrumentation.getUiAutomation();

  private void pause(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void click(String text) throws Exception {
    long end = System.currentTimeMillis() + 20000;
    while (System.currentTimeMillis() < end) {
      AccessibilityNodeInfo root = automation.getRootInActiveWindow();
      if (root != null)
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
          AccessibilityNodeInfo target = node;
          while (target != null && !target.isClickable()) target = target.getParent();
          if (target != null) {
            target.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            pause(150);
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
              pause(600);
              return;
            }
          }
        }
      pause(200);
    }
    throw new AssertionError("没有找到可点击控件：" + text);
  }

  private List<AccessibilityNodeInfo> inputs(AccessibilityNodeInfo node) {
    List<AccessibilityNodeInfo> out = new ArrayList<>();
    if (node == null) return out;
    if ("android.widget.EditText".contentEquals(node.getClassName())) out.add(node);
    for (int i = 0; i < node.getChildCount(); i++) out.addAll(inputs(node.getChild(i)));
    return out;
  }

  private void fill(String hint, String value) {
    for (AccessibilityNodeInfo input : inputs(automation.getRootInActiveWindow()))
      if (hint.contentEquals(input.getHintText())) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
        return;
      }
    throw new AssertionError("没有找到输入框：" + hint);
  }

  private boolean clickClass(AccessibilityNodeInfo node, String className) {
    if (node == null) return false;
    if (className.contentEquals(node.getClassName()) && node.isClickable()) {
      node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
      return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    for (int i = 0; i < node.getChildCount(); i++)
      if (clickClass(node.getChild(i), className)) return true;
    return false;
  }

  private void screenshot(String name) throws Exception {
    pause(500);
    Bitmap image = automation.takeScreenshot();
    assertNotNull(image);
    File output =
        new File(
            instrumentation.getTargetContext().getExternalFilesDir(null), "qa-" + name + ".png");
    try (FileOutputStream stream = new FileOutputStream(output)) {
      image.compress(Bitmap.CompressFormat.PNG, 100, stream);
    }
    image.recycle();
  }

  @Test
  public void createRecordThroughNativeUiAndVisitLocalPages() throws Exception {
    Context context = instrumentation.getTargetContext();
    Intent launch =
        new Intent(context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    instrumentation.startActivitySync(launch);
    pause(1000);
    screenshot("home");
    Store store = ((LivingApplication) context.getApplicationContext()).store;
    Store.App app = null;
    for (Store.App a : store.apps())
      if (a.spec.name().equals("随手记账")) {
        app = a;
        break;
      }
    assertNotNull(app);
    int before = store.records(app.id).size();
    click("随手记账");
    click("新增记录");
    fill("花在哪里", "UI-Smoke");
    fill("金额", "12.5");
    assertTrue(clickClass(automation.getRootInActiveWindow(), "android.widget.Spinner"));
    pause(400);
    click("餐饮");
    click("保存");
    assertEquals(before + 1, store.records(app.id).size());
    assertEquals("UI-Smoke", store.records(app.id).get(0).data.optString("item"));
    screenshot("app");
    click("统计");
    screenshot("stats");
    click("返回");
    click("生长");
    screenshot("insights");
    click("设置");
    screenshot("settings");
    // Only remove this test's own record; do not reset another user's app data.
    for (Store.Entry entry : store.records(app.id))
      if (entry.data.optString("item").equals("UI-Smoke")) store.deleteRecord(app.id, entry.id);
  }
}

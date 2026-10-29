package dev.livingapps;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;

/** Device entry/disclosure smoke test. Never grants accessibility permission or calls APIs. */
@RunWith(AndroidJUnit4.class)
public class RebuildEntryDeviceTest {
  @org.junit.Rule public final ChineseUiRule language = new ChineseUiRule();

  @Test
  public void rebuiltAppEntryExplainsScopeBeforeEnablingService() {
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    Activity activity =
        instrumentation.startActivitySync(
            new Intent(instrumentation.getTargetContext(), RebuildActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      instrumentation.waitForIdleSync();
      AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
      assertNotNull(root);
      assertFalse(root.findAccessibilityNodeInfosByText("选择手机上的应用").isEmpty());
      assertFalse(root.findAccessibilityNodeInfosByText("操作授权").isEmpty());
      assertFalse(root.findAccessibilityNodeInfosByText("按功能检测兼容").isEmpty());
      root.recycle();
    } finally {
      instrumentation.runOnMainSync(activity::finish);
    }
  }
}

package dev.livingapps;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAccessibilityService;
import org.robolectric.util.ReflectionHelpers;

/** Framework/SQLite/shadowed cross-app windows; does not claim a real QQ Music device test. */
@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = 35,
    qualifiers = "zh-rCN-w360dp-h760dp-port-xxhdpi",
    shadows = RebuildRuntimeTest.FreshWindows.class)
public class RebuildRuntimeTest {
  @Implements(android.accessibilityservice.AccessibilityService.class)
  public static class FreshWindows extends ShadowAccessibilityService {
    @Implementation
    @Override
    protected List<AccessibilityWindowInfo> getWindows() {
      List<AccessibilityWindowInfo> out = new ArrayList<>();
      for (AccessibilityWindowInfo w : super.getWindows())
        out.add(AccessibilityWindowInfo.obtain(w));
      return out;
    }
  }

  private Context context;
  private RebuildStore store;
  private RebuildTarget target;
  private RebuildAccessibilityService service;
  private AccessibilityNodeInfo root, song, play;
  private String shell;
  private final List<Integer> performed = new ArrayList<>();

  @Before
  public void setup() {
    context = RuntimeEnvironment.getApplication();
    context.deleteDatabase("living-rebuild.db");
    store = new RebuildStore(context);
    PackageInfo info = new PackageInfo();
    info.packageName = "example.music";
    info.versionCode = 7;
    info.applicationInfo = new ApplicationInfo();
    info.applicationInfo.packageName = info.packageName;
    info.applicationInfo.name = "Demo music";
    info.applicationInfo.nonLocalizedLabel = "Demo music";
    info.signingInfo = new SigningInfo();
    Shadows.shadowOf(info.signingInfo).setSignatures(new Signature[] {new Signature("12345678")});
    ActivityInfo activity = new ActivityInfo();
    activity.packageName = info.packageName;
    activity.name = "example.music.Player";
    activity.applicationInfo = info.applicationInfo;
    activity.exported = true;
    info.activities = new ActivityInfo[] {activity};
    Shadows.shadowOf(context.getPackageManager()).installPackage(info);
    ResolveInfo resolve = new ResolveInfo();
    resolve.activityInfo = activity;
    Shadows.shadowOf(context.getPackageManager())
        .addResolveInfoForIntent(
            new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_INFO)
                .setPackage(info.packageName),
            resolve);
    Shadows.shadowOf(context.getPackageManager())
        .addResolveInfoForIntent(
            new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(info.packageName),
            resolve);
    Shadows.shadowOf(context.getPackageManager())
        .addResolveInfoForIntent(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), resolve);
    target = RebuildTarget.get(context, info.packageName);
    service = Robolectric.buildService(RebuildAccessibilityService.class).create().get();
    service.onServiceConnected();
    root = node("root", "", "android.widget.LinearLayout", false);
    song = node("title", "Old song", "android.widget.TextView", false);
    play = node("play", "Play", "android.widget.Button", true);
    Shadows.shadowOf(play)
        .setOnPerformActionListener(
            (action, args) -> {
              performed.add(action);
              return true;
            });
    Shadows.shadowOf(root).addChild(song);
    Shadows.shadowOf(root).addChild(play);
    window(root);
    context
        .getSharedPreferences("rebuild-consent", 0)
        .edit()
        .clear()
        .putBoolean(target.pkg, true)
        .commit();
    shell = store.create(spec());
  }

  @After
  public void cleanup() {
    service.onDestroy();
    store.close();
  }

  private AccessibilityNodeInfo node(String id, String text, String klass, boolean click) {
    AccessibilityNodeInfo n = AccessibilityNodeInfo.obtain();
    n.setPackageName("example.music");
    n.setClassName(klass);
    n.setViewIdResourceName("example.music:id/" + id);
    n.setText(text);
    n.setVisibleToUser(true);
    n.setEnabled(true);
    n.setClickable(click);
    return n;
  }

  private JSONObject selector(String id, String klass, String text) {
    return J.obj(
        "viewId", "example.music:id/" + id, "text", text, "description", "", "className", klass);
  }

  private void window(AccessibilityNodeInfo node) {
    AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
    Shadows.shadowOf(window).setType(AccessibilityWindowInfo.TYPE_APPLICATION);
    Shadows.shadowOf(window).setActive(true);
    Shadows.shadowOf(window).setRoot(node);
    Shadows.shadowOf(service).setWindows(Arrays.asList(window));
  }

  private RebuildSpec spec() {
    java.util.List<JSONObject> shape =
        Arrays.asList(
            J.obj("viewId", "example.music:id/root", "className", "android.widget.LinearLayout"),
            J.obj("viewId", "example.music:id/title", "className", "android.widget.TextView"),
            J.obj(
                "viewId",
                "example.music:id/play",
                "className",
                "android.widget.Button",
                "click",
                true));
    String page = RebuildSpec.pageSignature(shape);
    return RebuildSpec.create("My music", target.pkg, target.version, target.certificate)
        .with(
            "widgets",
            J.arr(
                J.obj(
                    "id",
                    "title",
                    "label",
                    "Now playing",
                    "kind",
                    "read",
                    "selector",
                    selector("title", "android.widget.TextView", ""),
                    "page",
                    page),
                J.obj(
                    "id",
                    "play",
                    "label",
                    "Play",
                    "kind",
                    "action",
                    "steps",
                    J.arr(
                        J.obj(
                            "op",
                            "click",
                            "selector",
                            selector("play", "android.widget.Button", "Play"),
                            "page",
                            page)))));
  }

  private View overlay() {
    return ReflectionHelpers.getField(service, "overlay");
  }

  private View find(View view, String text) {
    if (view == null) return null;
    if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        View found = find(((ViewGroup) view).getChildAt(i), text);
        if (found != null) return found;
      }
    return null;
  }

  private void idle(long millis) {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
  }

  private void begin() {
    service.begin(shell, false, "");
    idle(750);
    assertNotNull(overlay());
  }

  private void refresh() {
    AccessibilityEvent event =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    service.onAccessibilityEvent(event);
    event.recycle();
    idle(250);
  }

  @Test
  public void realSqliteRevisionsRestoreAndTrialEvidenceRemainIndependent() {
    RebuildStore.Entry e = store.get(shell);
    store.verify(shell, e.revision, "play");
    assertTrue(store.get(shell).verified.contains("play"));
    store.save(shell, e.revision, e.spec.with("dark", true));
    assertTrue(store.get(shell).verified.isEmpty());
    try {
      store.save(shell, e.revision, e.spec);
      fail();
    } catch (IllegalStateException expected) {
    }
    store.restore(shell, 2, 1);
    assertEquals(3, store.get(shell).revision);
    assertFalse(store.get(shell).spec.dark());
    assertEquals(3, store.history(shell).size());
    store.close();
    store = new RebuildStore(context);
    assertEquals(target.pkg, store.get(shell).spec.pkg());
  }

  @Test
  public void optionsKeepSourceUntouchedAndBackClosesThePanelBeforeExiting() {
    begin();
    View panel = overlay().findViewWithTag("rebuild-options-panel");
    assertEquals(View.GONE, panel.getVisibility());
    overlay().findViewWithTag("rebuild-options").performClick();
    assertTrue(panel.isShown());
    assertTrue(find(overlay(), "查看原应用 / 手动操作").isShown());
    assertTrue(performed.isEmpty());
    overlay().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
    assertNotNull(overlay());
    assertEquals(View.GONE, panel.getVisibility());
    assertTrue(performed.isEmpty());
    overlay().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
    assertNull(overlay());
  }

  @Test
  public void noSessionStartsWithoutPackageConsent() {
    context.getSharedPreferences("rebuild-consent", 0).edit().clear().commit();
    service.begin(shell, false, "");
    idle(1000);
    assertNull(overlay());
  }

  @Test
  public void liveTextUpdatesAndMissingPageNeverShowsOldValue() {
    begin();
    assertNotNull(find(overlay(), "Old song"));
    song.setText("New song");
    root = node("root", "", "android.widget.LinearLayout", false);
    Shadows.shadowOf(root).addChild(song);
    Shadows.shadowOf(root).addChild(play);
    window(root);
    refresh();
    assertNull(find(overlay(), "Old song"));
    assertNotNull(find(overlay(), "New song"));
    AccessibilityNodeInfo different =
        node("other", "Other page", "android.widget.FrameLayout", false);
    window(different);
    refresh();
    assertNull(find(overlay(), "New song"));
    assertNotNull(find(overlay(), "当前页面未匹配"));
  }

  @Test
  public void userTriggeredClickRequiresExplicitTrialConfirmation() {
    begin();
    find(overlay(), "Play").performClick();
    idle(600);
    assertEquals(
        ((TextView) ReflectionHelpers.getField(service, "state")).getText().toString(),
        Arrays.asList(AccessibilityNodeInfo.ACTION_CLICK),
        performed);
    assertTrue(store.get(shell).verified.isEmpty());
    overlay().findViewWithTag("rebuild-options").performClick();
    find(overlay(), "查看原应用 / 手动操作").performClick();
    find(overlay(), "回到我的界面").performClick();
    overlay().findViewWithTag("rebuild-options").performClick();
    find(overlay(), "结果正确，标记本机已试用").performClick();
    assertTrue(store.get(shell).verified.contains("play"));
  }

  @Test
  public void duplicateTargetAndChangedDefinitionCannotClick() {
    AccessibilityNodeInfo duplicate = node("play", "Play", "android.widget.Button", true);
    Shadows.shadowOf(root).addChild(duplicate);
    begin();
    find(overlay(), "Play").performClick();
    idle(2500);
    assertTrue(performed.isEmpty());
    assertTrue(store.get(shell).verified.isEmpty());
    RebuildStore.Entry e = store.get(shell);
    store.save(shell, e.revision, e.spec.with("name", "Changed"));
    refresh();
    assertFalse(find(overlay(), "Play").isEnabled());
  }

  @Test
  public void protectedPageDisablesExecutionAndHidesItsText() {
    song.setPassword(true);
    song.setText("private password");
    begin();
    assertNull(find(overlay(), "private password"));
    assertFalse(find(overlay(), "Play").isEnabled());
  }

  @Test
  public void sourceVersionChangeInvalidatesRunningInterface() {
    begin();
    Shadows.shadowOf(context.getPackageManager())
            .getInternalMutablePackageInfo(target.pkg)
            .versionCode =
        8;
    refresh();
    assertFalse(find(overlay(), "Play").isEnabled());
    assertNotNull(find(overlay(), "适配已失效"));
  }

  @Test
  public void leavingSourceAppStopsOverlayAndCancelsPendingWork() {
    begin();
    find(overlay(), "Play").performClick();
    service.stop();
    idle(3000);
    assertNull(overlay());
    assertTrue(store.get(shell).verified.isEmpty());
    begin();
    AccessibilityNodeInfo foreign = node("foreign", "Home", "android.widget.FrameLayout", false);
    foreign.setPackageName("example.home");
    window(foreign);
    idle(1600); // Source-scoped events may not report the launcher: watchdog must also stop it.
    assertNull(overlay());
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void managerAndOverlayRenderAtPhoneSize() throws Exception {
    try (org.robolectric.android.controller.ActivityController<RebuildActivity> controller =
        Robolectric.buildActivity(
                RebuildActivity.class,
                new Intent(context, RebuildActivity.class).putExtra("shell", shell))
            .setup()
            .visible()) {
      idle(100);
      View decor = controller.get().getWindow().getDecorView();
      assertNotNull(find(decor, "启动我的界面"));
      capture(decor, "rebuild-editor");
    }
    begin();
    capture(overlay(), "rebuild-runtime");
    RebuildStore.Entry e = store.get(shell);
    store.save(shell, e.revision, e.spec.with("dark", true));
    service.begin(shell, false, "");
    idle(750);
    capture(overlay(), "rebuild-runtime-dark");
    RuntimeEnvironment.setQualifiers("zh-rCN-w320dp-h640dp-port-xxhdpi");
    android.content.res.Configuration larger =
        new android.content.res.Configuration(context.getResources().getConfiguration());
    larger.fontScale = 1.3f;
    context.getResources().updateConfiguration(larger, context.getResources().getDisplayMetrics());
    service.begin(shell, false, "");
    idle(750);
    capture(overlay(), "rebuild-runtime-compact-large-text");
    assertNotNull(find(overlay(), "停止并返回 Living"));
  }

  @Test
  public void dynamicListUpdatesAndClicksOnlyTheChosenUniqueItem() {
    root = node("root", "", "android.widget.LinearLayout", false);
    AccessibilityNodeInfo a = node("result", "Song A", "android.widget.Button", true),
        b = node("result", "Song B", "android.widget.Button", true);
    List<String> chosen = new ArrayList<>();
    Shadows.shadowOf(a)
        .setOnPerformActionListener(
            (op, args) -> {
              chosen.add("A");
              return true;
            });
    Shadows.shadowOf(b)
        .setOnPerformActionListener(
            (op, args) -> {
              chosen.add("B");
              return true;
            });
    Shadows.shadowOf(root).addChild(a);
    Shadows.shadowOf(root).addChild(b);
    window(root);
    String page =
        RebuildSpec.pageSignature(
            Arrays.asList(
                J.obj(
                    "viewId", "example.music:id/root", "className", "android.widget.LinearLayout"),
                J.obj(
                    "viewId",
                    "example.music:id/result",
                    "className",
                    "android.widget.Button",
                    "click",
                    true)));
    RebuildStore.Entry entry = store.get(shell);
    store.save(
        shell,
        entry.revision,
        entry.spec.with(
            "widgets",
            J.arr(
                J.obj(
                    "id",
                    "results",
                    "label",
                    "Results",
                    "kind",
                    "list",
                    "selector",
                    selector("result", "android.widget.Button", ""),
                    "page",
                    page,
                    "interactive",
                    true))));
    begin();
    assertNotNull(find(overlay(), "Song A"));
    find(overlay(), "Song B").performClick();
    assertEquals(Arrays.asList("B"), chosen);
    AccessibilityNodeInfo c = node("result", "Song C", "android.widget.Button", true);
    Shadows.shadowOf(root).addChild(c);
    window(root);
    refresh();
    assertNotNull(find(overlay(), "Song C"));
    RebuildStore.Entry next = store.get(shell);
    store.save(shell, next.revision, next.spec.with("name", "New interface"));
    refresh();
    assertNull(find(overlay(), "Song A"));
    assertNotNull(find(overlay(), "适配已失效"));
  }

  @Test
  public void pickerCreatesARealBindingWithoutPersistingDisplayedValueAsSelector() {
    service.begin(shell, true, "");
    idle(750);
    find(overlay(), "选取当前页面").performClick();
    find(overlay(), "显示这条信息").performClick();
    RebuildStore.Entry entry = store.get(shell);
    assertEquals(3, entry.spec.widgets().length());
    JSONObject captured = entry.spec.widgets().optJSONObject(2);
    assertEquals("read", captured.optString("kind"));
    assertEquals("", captured.optJSONObject("selector").optString("text"));
    assertEquals(2, entry.revision);
    assertNull(overlay());
  }

  @Test
  public void stopCancelsLaterStepsWithoutReplayingAcceptedClicks() {
    RebuildStore.Entry entry = store.get(shell);
    JSONObject w = entry.spec.widget("play");
    JSONArray steps = w.optJSONArray("steps");
    steps.put(J.parse(steps.optJSONObject(0).toString()));
    store.save(shell, entry.revision, entry.spec.with("widgets", J.arr(w)));
    begin();
    find(overlay(), "Play").performClick();
    assertEquals(1, performed.size());
    service.stop();
    idle(2000);
    assertEquals(1, performed.size());
    assertTrue(store.get(shell).verified.isEmpty());
  }

  @Test
  public void oversizedPageNeverExecutesOnAPartialNodeTree() {
    for (int i = 0; i < 305; i++)
      Shadows.shadowOf(root)
          .addChild(node("row" + i, "Row " + i, "android.widget.TextView", false));
    window(root);
    begin();
    assertFalse(find(overlay(), "Play").isEnabled());
    assertNull(find(overlay(), "Old song"));
    assertTrue(performed.isEmpty());
  }

  @Test
  public void screenOffRemovesOverlayAndSession() {
    begin();
    context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
    idle(100);
    assertNull(overlay());
  }

  @Test
  public void trialEvidenceExpiresInsteadOfConfirmingAnOldAttempt() {
    begin();
    find(overlay(), "Play").performClick();
    idle(61000);
    overlay().findViewWithTag("rebuild-options").performClick();
    find(overlay(), "结果正确，标记本机已试用").performClick();
    assertTrue(store.get(shell).verified.isEmpty());
  }

  @Test
  public void languageSwitchStopsPendingStepsWithoutChangingTheDefinition() {
    RebuildStore.Entry before = store.get(shell);
    JSONObject action = before.spec.widget("play");
    action
        .optJSONArray("steps")
        .put(J.parse(action.optJSONArray("steps").optJSONObject(0).toString()));
    store.save(shell, before.revision, before.spec.with("widgets", J.arr(action)));
    String definition = store.get(shell).spec.json().toString();
    long revision = store.get(shell).revision;
    begin();
    find(overlay(), "Play").performClick();
    assertEquals(1, performed.size());
    try (org.robolectric.android.controller.ActivityController<MainActivity> main =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      Languages.choose(main.get(), Languages.ENGLISH);
      idle(2500);
      assertNull(overlay());
      assertEquals(1, performed.size());
      assertEquals(definition, store.get(shell).spec.json().toString());
      assertEquals(revision, store.get(shell).revision);
      service.begin(shell, false, "");
      idle(750);
      View stop = find(overlay(), "Stop and return to Living");
      assertNotNull(stop);
      assertEquals(
          "en",
          stop.getContext().getResources().getConfiguration().getLocales().get(0).getLanguage());
      assertEquals(1, performed.size());
    }
  }

  @Test
  @Config(qualifiers = "en-w320dp-h640dp-port-xxhdpi")
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void englishRebuildControlsRenderWithoutTranslatingSourceInformation() throws Exception {
    try (org.robolectric.android.controller.ActivityController<RebuildActivity> controller =
        Robolectric.buildActivity(
                RebuildActivity.class,
                new Intent(context, RebuildActivity.class).putExtra("shell", shell))
            .setup()
            .visible()) {
      assertNotNull(find(controller.get().getWindow().getDecorView(), "Open my interface"));
      capture(controller.get().getWindow().getDecorView(), "language-rebuild-editor-en");
    }
    begin();
    assertNotNull(find(overlay(), "View original app / act manually"));
    assertNotNull(find(overlay(), "Old song"));
    capture(overlay(), "language-rebuild-overlay-en");
  }

  private void capture(View view, String name) throws Exception {
    int width = context.getResources().getDisplayMetrics().widthPixels,
        height = context.getResources().getDisplayMetrics().heightPixels;
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(bitmap));
    File dir = new File(System.getProperty("livingapps.qaDir"));
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, "host-" + name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }
}

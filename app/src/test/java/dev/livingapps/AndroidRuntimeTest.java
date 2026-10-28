package dev.livingapps;

import static org.junit.Assert.*;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.io.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

/** Android framework and real native SQLite on the host; not a physical-device test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h720dp-port-xxhdpi")
public class AndroidRuntimeTest {
  private Context context;
  private Store store;

  @Before
  public void prepare() {
    context = RuntimeEnvironment.getApplication();
    context.deleteDatabase("living-apps.db");
    store = new Store(context);
  }

  @After
  public void finish() {
    store.close();
  }

  private JSONObject row() {
    return J.obj("item", "午饭", "amount", 23.5, "category", "餐饮", "date", "2026-10-27");
  }

  @Test
  public void realSqlitePreservesRecordsAcrossEditRestoreAndReopen() {
    AppSpec initial = Templates.all().get(0);
    String id = store.create(initial, "test");
    String first = store.app(id).versionId;
    JSONObject next = initial.json();
    next.optJSONArray("fields")
        .put(J.obj("id", "note", "label", "备注", "type", "text", "required", false));
    String second = store.apply(id, first, AppSpec.parse(next.toString()), "增加备注", "test", false);
    JSONObject data = row();
    J.put(data, "note", "重要内容");
    store.saveRecord(id, second, null, data);
    String restored = store.apply(id, second, initial, "恢复", "restore", true);
    Store.Entry entry = store.records(id).get(0);
    store.saveRecord(id, restored, entry.id, row());
    store.close();
    store = new Store(context);
    assertEquals("重要内容", store.records(id).get(0).data.optString("note"));
    assertEquals(3, store.versions(id).size());
    assertEquals(23.5, store.records(id).get(0).data.optDouble("amount"), 0.001);
  }

  @Test
  public void importIsAtomicAndBackupDoesNotContainEvents() {
    String id = store.create(Templates.all().get(0), "test");
    store.saveRecord(id, store.app(id).versionId, null, row());
    store.event(id, store.app(id).versionId, "open", 0);
    String exported = store.exportBackup();
    assertFalse(exported.contains("events"));
    assertEquals(1, store.importBackup(exported));
    int count = store.apps().size();
    JSONArray apps =
        J.arr(
            J.obj("spec", Templates.all().get(1).json(), "records", J.arr()),
            J.obj("spec", J.obj("broken", true), "records", J.arr()));
    try {
      store.importBackup(
          J.obj("format", "living-apps-backup", "version", 1, "apps", apps).toString());
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(count, store.apps().size());
  }

  @Test
  public void localReflectionDeduplicatesAndOnlyAppliesAllowedChange() {
    String id = store.create(Templates.all().get(0), "test");
    Store.App app = store.app(id);
    for (int i = 0; i < 6; i++) {
      store.event(id, app.versionId, "open", 0);
      store.event(id, app.versionId, "save", 2000);
    }
    store
        .getWritableDatabase()
        .execSQL(
            "UPDATE events SET created=created-86400000 WHERE app_id=? AND id IN (SELECT id FROM"
                + " events WHERE app_id=? ORDER BY id LIMIT 6)",
            new Object[] {id, id});
    store.reflect(false);
    store.reflect(false);
    assertEquals(1, store.proposals(id).size());
    store.reflect(true);
    assertTrue(store.app(id).spec.quickAdd());
    assertEquals(2, store.versions(id).size());
    assertEquals(0, store.proposals(id).size());
  }

  @Test
  public void frozenAndStaleChangesCannotCommit() {
    String id = store.create(Templates.all().get(0), "test");
    Store.App app = store.app(id);
    store.freeze(id, true);
    try {
      store.apply(id, app.versionId, app.spec.with("quickAdd", true), "自动", "auto", false);
      fail();
    } catch (IllegalStateException expected) {
    }
    store.apply(id, app.versionId, app.spec.with("compact", true), "手动", "local", false);
    try {
      store.saveRecord(id, app.versionId, null, row());
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(0, store.records(id).size());
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void nativeViewsSaveRecordAndRenderPreview() throws Exception {
    try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity = controller.get();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      capture(activity, "home");
      View card = text(activity.getWindow().getDecorView(), "随手记账");
      assertNotNull(card);
      while (card != null && !card.isClickable()) card = (View) card.getParent();
      assertNotNull(card);
      card.performClick();
      View add = contains(activity.getWindow().getDecorView(), "新增记录");
      assertNotNull(add);
      add.performClick();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
      assertTrue(dialog.isShowing());
      View form = dialog.getWindow().getDecorView();
      ((EditText) tag(form, "item")).setText("UI proof");
      ((EditText) tag(form, "amount")).setText("12.5");
      ((Spinner) tag(form, "category")).setSelection(1);
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertFalse(dialog.isShowing());
      Store target = ((LivingApplication) context).store;
      Store.App app = null;
      for (Store.App a : target.apps()) if (a.spec.name().equals("随手记账")) app = a;
      assertNotNull(app);
      assertEquals(1, target.records(app.id).size());
      capture(activity, "app");
      View stats = text(activity.getWindow().getDecorView(), "统计");
      stats.performClick();
      capture(activity, "stats");
      description(activity.getWindow().getDecorView(), "返回").performClick();
      tag(activity.getWindow().getDecorView(), "nav-生长").performClick();
      capture(activity, "insights");
      tag(activity.getWindow().getDecorView(), "nav-设置").performClick();
      capture(activity, "settings");
      tag(activity.getWindow().getDecorView(), "nav-应用").performClick();
      contains(activity.getWindow().getDecorView(), "创造一个应用").performClick();
      capture(activity, "editor");
      View generate = contains(activity.getWindow().getDecorView(), "生成并预览");
      android.graphics.Rect actionBounds = new android.graphics.Rect();
      assertTrue("生成按钮始终可见", generate.getGlobalVisibleRect(actionBounds));
      description(activity.getWindow().getDecorView(), "返回").performClick();
      View open = text(activity.getWindow().getDecorView(), "随手记账");
      while (!open.isClickable()) open = (View) open.getParent();
      open.performClick();
      description(activity.getWindow().getDecorView(), "修改应用").performClick();
      android.content.Intent editorIntent =
          org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
      assertEquals(EditorActivity.class.getName(), editorIntent.getComponent().getClassName());
      assertEquals(app.id, editorIntent.getStringExtra("appId"));
      assertEquals("调整界面不改变记录", 1, target.records(app.id).size());
      assertEquals("仅打开编辑器尚不发布", 1, target.versions(app.id).size());
    }
    RuntimeEnvironment.setQualifiers("w320dp-h640dp-port-xxhdpi");
    android.content.res.Resources resources = context.getResources();
    android.content.res.Configuration large =
        new android.content.res.Configuration(resources.getConfiguration());
    large.fontScale = 1.3f;
    resources.updateConfiguration(large, resources.getDisplayMetrics());
    try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity = controller.get();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      capture(activity, "home-compact-large-text");
      contains(activity.getWindow().getDecorView(), "创造一个应用").performClick();
      capture(activity, "editor-compact-large-text");
    }
  }

  private View text(View v, String text) {
    if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View found = text(((ViewGroup) v).getChildAt(i), text);
        if (found != null) return found;
      }
    return null;
  }

  private View description(View v, String label) {
    if (label.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
      return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View found = description(((ViewGroup) v).getChildAt(i), label);
        if (found != null) return found;
      }
    return null;
  }

  private View contains(View v, String text) {
    if (v instanceof Button && ((Button) v).getText().toString().contains(text)) return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View found = contains(((ViewGroup) v).getChildAt(i), text);
        if (found != null) return found;
      }
    return null;
  }

  private View tag(View v, String id) {
    if (id.equals(v.getTag())) return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View found = tag(((ViewGroup) v).getChildAt(i), id);
        if (found != null) return found;
      }
    return null;
  }

  private void capture(Activity activity, String name) throws Exception {
    View view = activity.getWindow().getDecorView();
    int width =
        Math.round(
            activity.getResources().getConfiguration().screenWidthDp
                * activity.getResources().getDisplayMetrics().density);
    int height =
        Math.round(
            activity.getResources().getConfiguration().screenHeightDp
                * activity.getResources().getDisplayMetrics().density);
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(image));
    File dir = new File(System.getProperty("livingapps.qaDir"));
    assertTrue(dir.exists() || dir.mkdirs());
    try (FileOutputStream out = new FileOutputStream(new File(dir, "host-" + name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
  }
}

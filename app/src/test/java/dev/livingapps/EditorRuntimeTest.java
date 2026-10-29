package dev.livingapps;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.io.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "zh-rCN-w360dp-h720dp-port-xxhdpi")
public class EditorRuntimeTest {
  private Store store;
  private String id;

  @Before
  public void prepare() {
    Context context = RuntimeEnvironment.getApplication();
    store = ((LivingApplication) context).store;
    for (Store.App app : store.apps()) store.deleteApp(app.id);
    id = store.create(Templates.all().get(0), "test");
    store.saveRecord(id, store.app(id).versionId, null, row("正式午饭"));
  }

  @After
  public void close() {
    store.close();
  }

  private JSONObject row(String title) {
    return J.obj("item", title, "amount", 20.0, "category", "餐饮", "date", "2026-10-27");
  }

  private ActivityController<EditorActivity> open() {
    return Robolectric.buildActivity(
            EditorActivity.class,
            new Intent(RuntimeEnvironment.getApplication(), EditorActivity.class)
                .putExtra("appId", id))
        .setup()
        .visible();
  }

  private void idle() {
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  }

  @Test
  public void sqliteUpgradeKeepsExistingAppsVersionsAndRecords() {
    String first = store.app(id).versionId;
    store.getWritableDatabase().execSQL("DROP TABLE drafts");
    store.getWritableDatabase().execSQL("DROP TABLE draft_recovery");
    store.getWritableDatabase().execSQL("DROP TABLE migration_backups");
    store.getWritableDatabase().execSQL("ALTER TABLE apps DROP COLUMN data_epoch");
    store.getWritableDatabase().execSQL("ALTER TABLE versions DROP COLUMN data_epoch");
    store.getWritableDatabase().setVersion(1);
    store.close();
    store = new Store(RuntimeEnvironment.getApplication());
    ((LivingApplication) RuntimeEnvironment.getApplication()).store = store;
    assertEquals(first, store.app(id).versionId);
    assertEquals("正式午饭", store.records(id).get(0).data.optString("item"));
    assertNotNull(store.beginDraft(id));
    assertEquals(3, store.getReadableDatabase().getVersion());
  }

  @Test
  public void draftUndoSurvivesDatabaseReopenAndPublishPreservesBusinessRecords() {
    EditSession session = store.beginDraft(id);
    String baseline = session.baseVersion;
    session.apply(session.field("item", "label", "消费内容"), "修改名称");
    store.saveDraft(session);
    TrialRecordStore trial = new TrialRecordStore(session);
    trial.save(null, row("临时数据"));
    store.saveDraft(session);
    assertEquals(1, store.records(id).size());
    assertEquals(baseline, store.app(id).versionId);
    store.close();
    store = new Store(RuntimeEnvironment.getApplication());
    ((LivingApplication) RuntimeEnvironment.getApplication()).store = store;
    EditSession restored = store.draft(id);
    assertEquals(1, new TrialRecordStore(restored).records().size());
    restored.undo();
    assertEquals("花在哪里", restored.spec.field("item").label);
    restored.redo();
    store.saveDraft(restored);
    store.publishDraft(restored);
    assertEquals(2, store.versions(id).size());
    assertFalse(store.hasDraft(id));
    assertEquals("正式午饭", store.records(id).get(0).data.optString("item"));
    assertFalse(store.exportBackup().contains("临时数据"));
  }

  @Test
  public void staleDraftCannotPublishOverANewerFormalVersion() {
    EditSession session = store.beginDraft(id);
    session.apply(session.spec.with("name", "草稿名称"), "名称");
    store.saveDraft(session);
    store.apply(
        id, session.baseVersion, session.base.with("compact", true), "正式更新", "local", false);
    try {
      store.publishDraft(session);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertTrue(store.hasDraft(id));
    assertEquals(2, store.versions(id).size());
    assertEquals("草稿名称", store.draft(id).spec.name());
  }

  @Test
  public void twoEditorsAtTheSameSemanticRevisionCannotOverwriteEachOther() {
    EditSession first = store.beginDraft(id), second = store.draft(id);
    first.apply(first.spec.with("name", "第一位编辑"), "名称");
    second.apply(second.spec.with("compact", true), "布局");
    assertEquals(first.revision, second.revision);
    store.saveDraft(first);
    try {
      store.saveDraft(second);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals("第一位编辑", store.draft(id).spec.name());
    assertFalse(store.draft(id).spec.compact());
  }

  @Test
  public void draftBlocksAutoAdoptionButNotObservation() {
    Store.App app = store.app(id);
    for (int i = 0; i < 6; i++) {
      store.event(id, app.versionId, "open", 0);
      store.event(id, app.versionId, "save", 1000);
    }
    store
        .getWritableDatabase()
        .execSQL(
            "UPDATE events SET created=created-86400000 WHERE id IN (SELECT id FROM events ORDER BY"
                + " id LIMIT 6)");
    EditSession session = store.beginDraft(id);
    store.reflect(true);
    assertFalse(store.app(id).spec.quickAdd());
    assertEquals(1, store.proposals(id).size());
    store.discardDraft(id, session.id);
    store.reflect(true);
    assertTrue(store.app(id).spec.quickAdd());
  }

  @Test
  public void trialEditAndDeleteNeverTouchFormalRecord() {
    EditSession s = store.beginDraft(id);
    TrialRecordStore trial = new TrialRecordStore(s);
    trial.seed(store.records(id));
    assertEquals(1, trial.records().size());
    String tempId = trial.records().get(0).id;
    trial.save(tempId, row("只改试用"));
    trial.delete(tempId);
    assertTrue(trial.records().isEmpty());
    assertEquals("正式午饭", store.records(id).get(0).data.optString("item"));
  }

  @Test
  public void lateModelResultBecomesPendingWithoutOverwritingLocalWork() {
    try (ActivityController<EditorActivity> controller = open()) {
      EditorActivity a = controller.get();
      a.session.prompt = "让布局紧凑";
      EditorActivity.Ticket ticket = a.ticket();
      a.session.apply(a.session.field("item", "label", "本地名称"), "本地改名");
      store.saveDraft(a.session);
      a.receive(ticket, new ResponsesProtocol.Generated(ticket.spec.with("compact", true), "布局调整"));
      assertNotNull(a.session.pending);
      assertFalse(a.session.spec.compact());
      assertEquals("本地名称", a.session.spec.field("item").label);
      assertEquals(1, store.versions(id).size());
    }
  }

  @Test
  public void modelCannotSilentlyExpandLockedFieldScope() {
    try (ActivityController<EditorActivity> controller = open()) {
      EditorActivity a = controller.get();
      a.session.selected = "field:item";
      a.session.prompt = "改名";
      EditorActivity.Ticket ticket = a.ticket();
      a.receive(ticket, new ResponsesProtocol.Generated(ticket.spec.with("compact", true), "只是改名"));
      assertNotNull(a.session.pending);
      assertFalse(a.session.spec.compact());
      assertEquals("field:item", a.session.pending.optString("scope"));
    }
  }

  @Test
  public void destroyedEditorIgnoresItsDelayedResult() {
    ActivityController<EditorActivity> controller = open();
    EditorActivity a = controller.get();
    EditorActivity.Ticket ticket = a.ticket();
    controller.pause().stop().destroy();
    a.receive(ticket, new ResponsesProtocol.Generated(ticket.spec.with("compact", true), "迟到"));
    assertFalse(store.draft(id).spec.compact());
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void nativeSelectionRenameRecreationTrialAndPublish() throws Exception {
    try (ActivityController<EditorActivity> controller = open()) {
      EditorActivity a = controller.get();
      idle();
      capture(a, "draft-editor");
      tag(a.getWindow().getDecorView(), "select-field:item").performClick();
      idle();
      ((EditText) tag(a.getWindow().getDecorView(), "rename:item")).setText("消费内容");
      click(a, "应用");
      idle();
      assertEquals("消费内容", store.draft(id).spec.field("item").label);
      capture(a, "draft-selected");
      click(a, "AI 修改");
      idle();
      ((EditText) tag(a.getWindow().getDecorView(), "editor-prompt")).setText("保留记录，增加一个可选备注");
      controller.recreate();
      a = controller.get();
      idle();
      assertEquals(
          "保留记录，增加一个可选备注",
          ((EditText) tag(a.getWindow().getDecorView(), "editor-prompt")).getText().toString());
      click(a, "试用");
      idle();
      capture(a, "draft-trial");
      click(a, "填写试用表单");
      idle();
      AlertDialog form = ShadowAlertDialog.getLatestAlertDialog();
      View fields = form.getWindow().getDecorView();
      ((EditText) tag(fields, "item")).setText("临时输入");
      ((EditText) tag(fields, "amount")).setText("35");
      ((Spinner) tag(fields, "category")).setSelection(1);
      form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      idle();
      assertEquals(1, new TrialRecordStore(a.session).records().size());
      assertEquals("正式午饭", store.records(id).get(0).data.optString("item"));
      description(a.getWindow().getDecorView(), "返回编辑").performClick();
      idle();
      click(a, "完成");
      idle();
      captureDialog(ShadowAlertDialog.getLatestAlertDialog(), "draft-review");
      ShadowAlertDialog.getLatestAlertDialog()
          .getButton(AlertDialog.BUTTON_POSITIVE)
          .performClick();
      idle();
      assertEquals("消费内容", store.app(id).spec.field("item").label);
      assertEquals(1, store.records(id).size());
      assertFalse(store.hasDraft(id));
    }
  }

  @Test
  public void selectingActionNeverExecutesButTrialClickWritesOnlyTemporaryRecords() {
    Store.App app = store.app(id);
    JSONObject raw = app.spec.json();
    J.put(raw, "schemaVersion", 2);
    J.put(
        raw,
        "actions",
        J.arr(J.obj("id", "lunch", "label", "记午饭", "kind", "add_record", "values", row("快捷午饭"))));
    store.apply(id, app.versionId, AppSpec.parse(raw.toString()), "按钮", "local", false);
    try (ActivityController<EditorActivity> controller = open()) {
      EditorActivity activity = controller.get();
      tag(activity.getWindow().getDecorView(), "draft-action:lunch").performClick();
      idle();
      assertEquals("action:lunch", activity.session.selected);
      assertEquals(1, store.records(id).size());
      click(activity, "试用");
      idle();
      tag(activity.getWindow().getDecorView(), "draft-action:lunch").performClick();
      idle();
      assertEquals(1, new TrialRecordStore(activity.session).records().size());
      assertEquals(1, store.records(id).size());
    }
  }

  @Test
  public void unsubmittedTrialInputSurvivesRecreationWithoutEnteringModelContext() {
    try (ActivityController<EditorActivity> controller = open()) {
      EditorActivity activity = controller.get();
      click(activity, "试用");
      idle();
      click(activity, "填写试用表单");
      idle();
      AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
      ((EditText) tag(dialog.getWindow().getDecorView(), "item"))
          .setText("PRIVATE_UNSUBMITTED_TRIAL");
      ((EditText) tag(dialog.getWindow().getDecorView(), "amount")).setText("12.5");
      idle();
      assertFalse(
          activity.session.requestContext().toString().contains("PRIVATE_UNSUBMITTED_TRIAL"));
      controller.recreate();
      activity = controller.get();
      idle();
      dialog = ShadowAlertDialog.getLatestAlertDialog();
      assertEquals(
          "PRIVATE_UNSUBMITTED_TRIAL",
          ((EditText) tag(dialog.getWindow().getDecorView(), "item")).getText().toString());
      assertEquals(
          "12.5",
          ((EditText) tag(dialog.getWindow().getDecorView(), "amount")).getText().toString());
      assertEquals(1, store.records(id).size());
      assertTrue(new TrialRecordStore(activity.session).records().isEmpty());
    }
  }

  @Test
  @Config(qualifiers = "zh-rCN-w320dp-h640dp-port-xxhdpi")
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void smallScreenLargeTextKeepsEditorActionsAndScrollablePanel() throws Exception {
    android.content.res.Resources resources = RuntimeEnvironment.getApplication().getResources();
    android.content.res.Configuration config =
        new android.content.res.Configuration(resources.getConfiguration());
    config.fontScale = 1.3f;
    resources.updateConfiguration(config, resources.getDisplayMetrics());
    try (ActivityController<EditorActivity> controller = open()) {
      EditorActivity activity = controller.get();
      idle();
      View decor = activity.getWindow().getDecorView();
      decor.measure(
          View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
      decor.layout(0, 0, 960, 1920);
      idle();
      for (String label : new String[] {"试用", "完成", "更多"}) {
        View button = text(decor, label);
        assertNotNull(label, button);
        assertTrue(label, button.getWidth() > 0 && button.getHeight() > 0);
        android.graphics.Rect bounds = new android.graphics.Rect();
        assertTrue(label, button.getGlobalVisibleRect(bounds));
        assertTrue(label, bounds.right <= 960);
      }
      Bitmap bitmap = Bitmap.createBitmap(960, 1920, Bitmap.Config.ARGB_8888);
      decor.draw(new Canvas(bitmap));
      File dir = new File(System.getProperty("livingapps.qaDir"));
      dir.mkdirs();
      try (FileOutputStream output =
          new FileOutputStream(new File(dir, "host-draft-compact-large-text.png"))) {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
      }
      bitmap.recycle();
    }
  }

  @Test
  public void nativeMigrationDryRunAndCommitUseSeparateCheckedFlow() throws Exception {
    try (ActivityController<EditorActivity> controller = open()) {
      EditorActivity activity = controller.get();
      click(activity, "更多");
      idle();
      AlertDialog menu = ShadowAlertDialog.getLatestAlertDialog();
      menu.getListView().performItemClick(null, 4, 4);
      idle();
      AlertDialog fields = ShadowAlertDialog.getLatestAlertDialog();
      fields.getListView().performItemClick(null, 1, 1);
      idle();
      AlertDialog modes = ShadowAlertDialog.getLatestAlertDialog();
      modes.getListView().performItemClick(null, 1, 1);
      idle();
      AlertDialog scale = ShadowAlertDialog.getLatestAlertDialog();
      ((EditText) tag(scale.getWindow().getDecorView(), "migration-factor")).setText("100");
      ((EditText) tag(scale.getWindow().getDecorView(), "migration-unit")).setText("分");
      scale.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      AlertDialog review = null;
      for (int i = 0; i < 100; i++) {
        Thread.sleep(20);
        idle();
        AlertDialog latest = ShadowAlertDialog.getLatestAlertDialog();
        if (text(latest.getWindow().getDecorView(), "检查数据转换") != null) {
          review = latest;
          break;
        }
      }
      assertNotNull("应呈现转换演算结果", review);
      assertEquals(20, store.records(id).get(0).data.optInt("amount"));
      review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      for (int i = 0; i < 100 && store.app(id).spec.unit().equals("元"); i++) {
        Thread.sleep(20);
        idle();
      }
      idle();
      assertEquals("分", store.app(id).spec.unit());
      assertEquals(2000, store.records(id).get(0).data.optInt("amount"));
      assertEquals(1, store.migrationBackups(id).size());
    }
  }

  private void click(Activity a, String label) {
    View v = text(a.getWindow().getDecorView(), label);
    assertNotNull(label, v);
    while (v != null && !v.isClickable()) v = (View) v.getParent();
    assertNotNull(label, v);
    v.performClick();
  }

  private View text(View v, String label) {
    if (v instanceof TextView && label.contentEquals(((TextView) v).getText())) return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View f = text(((ViewGroup) v).getChildAt(i), label);
        if (f != null) return f;
      }
    return null;
  }

  private View tag(View v, String label) {
    if (label.equals(v.getTag())) return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View f = tag(((ViewGroup) v).getChildAt(i), label);
        if (f != null) return f;
      }
    return null;
  }

  private View description(View v, String label) {
    if (label.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
      return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View f = description(((ViewGroup) v).getChildAt(i), label);
        if (f != null) return f;
      }
    return null;
  }

  private void capture(Activity a, String name) throws Exception {
    captureView(a.getWindow().getDecorView(), name);
  }

  private void captureDialog(AlertDialog d, String name) throws Exception {
    captureView(d.getWindow().getDecorView(), name);
  }

  private void captureView(View view, String name) throws Exception {
    int width = 1080, height = 2160;
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(bitmap));
    File dir = new File(System.getProperty("livingapps.qaDir"));
    dir.mkdirs();
    try (FileOutputStream output = new FileOutputStream(new File(dir, "host-" + name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
    }
    bitmap.recycle();
  }
}

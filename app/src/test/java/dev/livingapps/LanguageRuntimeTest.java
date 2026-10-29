package dev.livingapps;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;

/** Real SQLite/framework lifecycle and native rendering; no model/network or device claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-w360dp-h760dp-port-xxhdpi")
public class LanguageRuntimeTest {
  private Locale previous;
  private LivingApplication app;

  @Before
  public void setup() {
    previous = Locale.getDefault();
    app = (LivingApplication) RuntimeEnvironment.getApplication();
  }

  @After
  public void finish() {
    Locale.setDefault(previous);
  }

  private void idle() {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
  }

  private View find(View root, String text) {
    if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
    if (root instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
        View v = find(((ViewGroup) root).getChildAt(i), text);
        if (v != null) return v;
      }
    return null;
  }

  private void click(View root, String text) {
    View v = find(root, text);
    assertNotNull(text, v);
    while (!v.isClickable() && v.getParent() instanceof View) v = (View) v.getParent();
    assertTrue(v.isClickable());
    v.performClick();
    idle();
  }

  private void select(ActivityController<MainActivity> c, String language) {
    Languages.choose(c.get(), language);
    idle();
    // LocaleManager dispatch is a platform responsibility; recreate explicitly in its host shadow.
    if (!c.get()
        .getResources()
        .getConfiguration()
        .getLocales()
        .get(0)
        .getLanguage()
        .equals(Languages.effective(c.get()).getLanguage())) c.recreate();
    idle();
  }

  private View decor(Activity a) {
    return a.getWindow().getDecorView();
  }

  @Test
  public void englishFirstLaunchHasEnglishChromeAndFreshTemplates() {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      assertNotNull(find(decor(c.get()), "My apps"));
      assertNotNull(find(decor(c.get()), "Create an app"));
      assertTrue(app.store.apps().stream().anyMatch(a -> a.spec.name().equals("Daily spending")));
      assertEquals(
          "en", c.get().getResources().getConfiguration().getLocales().get(0).getLanguage());
      assertTrue(app.store.apps().stream().allMatch(a -> app.store.records(a.id).isEmpty()));
      assertNull(find(decor(c.get()), "随手记账"));
      click(decor(c.get()), "Create an app");
      assertNotNull(find(decor(c.get()), "Daily spending"));
      assertNull(find(decor(c.get()), "随手记账"));
      click(decor(c.get()), "Daily spending");
      assertEquals(4, app.store.apps().size());
      assertTrue(app.store.apps().stream().allMatch(a -> a.spec.name().matches("[\\p{ASCII}]+")));
    }
  }

  @Test
  @Config(sdk = 26, qualifiers = "en-w360dp-h760dp-port-xxhdpi")
  public void preAndroid13PickerSavesAndReturnsToSettingsAcrossRecreation() {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      click(decor(c.get()), "Settings");
      click(decor(c.get()), "Language / 语言");
      AlertDialog picker = ShadowAlertDialog.getLatestAlertDialog();
      picker.getListView().performItemClick(picker.getListView().getChildAt(1), 1, 1);
      idle();
      if (!c.get().getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh"))
        c.recreate();
      assertEquals("zh-CN", Languages.requested(c.get()));
      assertNotNull(find(decor(c.get()), "语言 / Language"));
      assertNotNull(find(decor(c.get()), "按你的偏好"));
      c.recreate();
      assertNotNull(find(decor(c.get()), "语言 / Language"));
      select(c, Languages.ENGLISH);
      assertNotNull(find(decor(c.get()), "Your preferences"));
      assertNotNull(find(decor(c.get()), "Language / 语言"));
    }
  }

  @Test
  @Config(sdk = 32, qualifiers = "zh-rCN-w360dp-h760dp-port-xxhdpi")
  public void systemDefaultCanBeRestoredAfterManualEnglish() {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      assertEquals("zh", Languages.effective(c.get()).getLanguage());
      select(c, Languages.ENGLISH);
      assertNotNull(find(decor(c.get()), "My apps"));
      select(c, Languages.SYSTEM);
      assertNotNull(find(decor(c.get()), "我的应用"));
      assertEquals("system", Languages.requested(c.get()));
    }
  }

  @Test
  @Config(sdk = 32, qualifiers = "fr-rFR-w360dp-h760dp-port-xxhdpi")
  public void unsupportedSystemLanguageFallsBackToEnglish() {
    assertEquals(Locale.ENGLISH, Languages.effective(app));
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      assertNotNull(find(decor(c.get()), "My apps"));
      assertEquals("Cancel", c.get().getString(android.R.string.cancel));
    }
  }

  @Test
  @Config(sdk = 33, qualifiers = "en-w360dp-h760dp-port-xxhdpi")
  public void android13UsesThePlatformChoiceAndRespectsExternalChanges() {
    LocaleManager manager = app.getSystemService(LocaleManager.class);
    manager.setApplicationLocales(LocaleList.forLanguageTags("zh-CN"));
    assertEquals("zh-CN", Languages.requested(app));
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      assertNotNull(find(decor(c.get()), "我的应用"));
      select(c, Languages.ENGLISH);
      assertEquals("en", manager.getApplicationLocales().toLanguageTags());
      assertNotNull(find(decor(c.get()), "My apps"));
      select(c, Languages.SYSTEM);
      assertTrue(manager.getApplicationLocales().isEmpty());
    }
  }

  @Test
  @Config(sdk = 33)
  public void legacyChoiceMigratesOnceWithoutOverridingTheSystemPickerLater() {
    SharedPreferences prefs = app.getSharedPreferences("language", 0);
    prefs.edit().putString("choice", "zh-CN").putBoolean("platformMigrated", false).commit();
    Languages.migrate(app);
    LocaleManager manager = app.getSystemService(LocaleManager.class);
    assertEquals("zh-CN", manager.getApplicationLocales().toLanguageTags());
    manager.setApplicationLocales(LocaleList.forLanguageTags("en"));
    Languages.migrate(app);
    assertEquals("en", manager.getApplicationLocales().toLanguageTags());
  }

  @Test
  public void switchingPreservesDefinitionsValuesVersionsAndDraftContents() {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      AppSpec spec = Templates.all().get(1).with("name", "保存");
      String id = app.store.create(spec, "test"), version = app.store.app(id).versionId;
      app.store.saveRecord(
          id, version, null, J.obj("date", "2026-10-29", "volume", 300, "note", "我的应用 / Settings"));
      EditSession draft = app.store.beginDraft(id);
      draft.prompt = "保留我的原话 / Keep my words";
      draft.apply(draft.spec.with("description", "English / 中文的用户说明"), "用户修改");
      app.store.saveDraft(draft);
      String definition = app.store.app(id).spec.toString(),
          record = app.store.records(id).get(0).data.toString();
      String draftDefinition = app.store.draft(id).spec.toString();
      int versions = app.store.versions(id).size();
      select(c, Languages.CHINESE);
      select(c, Languages.ENGLISH);
      assertEquals(definition, app.store.app(id).spec.toString());
      assertEquals(record, app.store.records(id).get(0).data.toString());
      assertEquals(version, app.store.app(id).versionId);
      assertEquals(versions, app.store.versions(id).size());
      assertEquals(draftDefinition, app.store.draft(id).spec.toString());
      assertEquals("保留我的原话 / Keep my words", app.store.draft(id).prompt);
      assertTrue(app.store.draft(id).canUndo());
      assertEquals("保存", new Ui(c.get()).text("保存", 14, Ui.INK, false).getText().toString());
      assertTrue(ResponsesClient.languageInstruction().contains("English (en)"));
      assertFalse(app.automatic());
    }
  }

  @Test
  public void anEnglishPendingDraftCanResumeSafelyInChinese() {
    String id = app.store.create(Templates.all().get(1), "test");
    EditSession draft = app.store.beginDraft(id);
    draft.prompt = "Keep this request";
    draft.status = "Waiting for the model; you can cancel or keep editing locally";
    app.store.saveDraft(draft);
    app.getSystemService(LocaleManager.class)
        .setApplicationLocales(LocaleList.forLanguageTags("zh-CN"));
    try (ActivityController<EditorActivity> c =
        Robolectric.buildActivity(
                EditorActivity.class, new Intent(app, EditorActivity.class).putExtra("appId", id))
            .setup()
            .visible()) {
      assertEquals("Keep this request", c.get().session.prompt);
      assertEquals("上次请求已结束，草稿和输入已保留，可重新发送", c.get().session.status);
      assertEquals(0, app.store.records(id).size());
    }
  }

  private void capture(Activity a, String name) throws Exception {
    View v = decor(a);
    int width = a.getResources().getDisplayMetrics().widthPixels,
        height = a.getResources().getDisplayMetrics().heightPixels;
    v.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    v.layout(0, 0, width, height);
    Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    v.draw(new Canvas(b));
    File folder = new File(System.getProperty("livingapps.qaDir"));
    folder.mkdirs();
    try (FileOutputStream out =
        new FileOutputStream(new File(folder, "host-language-" + name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    b.recycle();
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  @Config(qualifiers = "en-w320dp-h640dp-port-xxhdpi")
  public void nativeEnglishAndChineseScreensRenderAtCompactPhoneSize() throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      capture(c.get(), "home-en");
      click(decor(c.get()), "Settings");
      capture(c.get(), "settings-en");
      select(c, Languages.CHINESE);
      capture(c.get(), "settings-zh");
      select(c, Languages.ENGLISH);
      String id = app.store.create(Templates.localized().get(1), "test");
      try (ActivityController<EditorActivity> editor =
          Robolectric.buildActivity(
                  EditorActivity.class, new Intent(app, EditorActivity.class).putExtra("appId", id))
              .setup()
              .visible()) {
        assertNotNull(find(decor(editor.get()), "Edit app"));
        capture(editor.get(), "editor-en");
      }
    }
  }
}

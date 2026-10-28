package dev.livingapps;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.livingapps.core.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StorageIntegrationTest {
  private Store store;
  private Context context;

  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getContext();
    context.deleteDatabase("living-apps.db");
    store = new Store(context);
  }

  @After
  public void tearDown() {
    store.close();
    context.deleteDatabase("living-apps.db");
  }

  private JSONObject lunch() {
    return J.obj("item", "午饭", "amount", 23.5, "category", "餐饮", "date", "2026-10-27");
  }

  @Test
  public void restoreKeepsNewFieldValuesAndAllRecords() {
    AppSpec original = Templates.all().get(0);
    String id = store.create(original, "test");
    String first = store.app(id).versionId;
    JSONObject updated = original.json();
    updated
        .optJSONArray("fields")
        .put(J.obj("id", "note", "label", "备注", "type", "text", "required", false));
    AppSpec next = AppSpec.parse(updated.toString());
    String second = store.apply(id, first, next, "增加备注", "test", false);
    JSONObject data = lunch();
    J.put(data, "note", "保存这个值");
    store.saveRecord(id, second, null, data);
    String restored = store.apply(id, second, original, "恢复", "restore", true);
    Store.Entry entry = store.records(id).get(0);
    assertEquals("保存这个值", entry.data.optString("note"));
    JSONObject edited = lunch();
    J.put(edited, "amount", 25);
    store.saveRecord(id, restored, entry.id, edited);
    assertEquals("保存这个值", store.records(id).get(0).data.optString("note"));
    assertEquals(3, store.versions(id).size());
  }

  @Test
  public void staleEditsAndStaleFormSubmissionsAreRejected() {
    String id = store.create(Templates.all().get(0), "test");
    Store.App old = store.app(id);
    store.apply(id, old.versionId, old.spec.with("compact", true), "变更", "test", false);
    try {
      store.saveRecord(id, old.versionId, null, lunch());
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(0, store.records(id).size());
    try {
      store.apply(id, old.versionId, old.spec, "陈旧修改", "test", false);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(2, store.versions(id).size());
  }

  @Test
  public void invalidBackupRollsBackWholeImport() {
    String id = store.create(Templates.all().get(0), "test");
    JSONArray array = new JSONArray();
    array.put(J.obj("spec", Templates.all().get(1).json(), "records", J.arr()));
    array.put(J.obj("spec", J.obj("broken", true), "records", J.arr()));
    String backup = J.obj("format", "living-apps-backup", "version", 1, "apps", array).toString();
    try {
      store.importBackup(backup);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(1, store.apps().size());
    assertEquals(id, store.apps().get(0).id);
  }

  @Test
  public void exportImportCopiesRecordsWithoutCredentials() {
    String id = store.create(Templates.all().get(0), "test");
    store.saveRecord(id, store.app(id).versionId, null, lunch());
    store.event(id, store.app(id).versionId, "open", 0);
    String backup = store.exportBackup();
    assertFalse(backup.contains("api_key"));
    assertFalse(backup.contains("events"));
    assertEquals(1, store.importBackup(backup));
    assertEquals(2, store.apps().size());
    for (Store.App app : store.apps()) assertEquals(1, store.records(app.id).size());
  }

  @Test
  public void cascadeDeleteDoesNotTouchOtherApps() {
    String one = store.create(Templates.all().get(0), "test"),
        two = store.create(Templates.all().get(0), "test");
    store.saveRecord(one, store.app(one).versionId, null, lunch());
    store.saveRecord(two, store.app(two).versionId, null, lunch());
    store.deleteApp(one);
    assertEquals(1, store.apps().size());
    assertEquals(1, store.records(two).size());
  }

  @Test
  public void keystoreRoundTripDoesNotPersistPlaintext() throws Exception {
    SecretVault vault = new SecretVault(context);
    String sentinel = "testing-not-a-real-api-key";
    vault.save(sentinel);
    assertEquals(sentinel, vault.read());
    String encrypted =
        context.getSharedPreferences("credential-vault", 0).getString("encrypted", "");
    assertFalse(encrypted.contains(sentinel));
    vault.save("");
    assertEquals("", vault.read());
  }

  @Test
  public void frozenAppsRefuseAutomaticUpdates() {
    String id = store.create(Templates.all().get(0), "test");
    Store.App app = store.app(id);
    store.freeze(id, true);
    try {
      store.apply(id, app.versionId, app.spec.with("quickAdd", true), "自动", "auto", false);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertFalse(store.app(id).spec.quickAdd());
  }

  @Test
  public void automaticUpdatesCannotBypassAllowList() {
    String id = store.create(Templates.all().get(0), "test");
    Store.App app = store.app(id);
    try {
      store.apply(
          id,
          app.versionId,
          app.spec.with("quickAdd", true).with("name", "偷偷改名"),
          "额外修改",
          "auto",
          false);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(app.versionId, store.app(id).versionId);
  }

  @Test
  public void proposalsDeduplicateAndCanBeAppliedWhenAutomaticIsEnabled() {
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
    assertFalse(store.app(id).spec.quickAdd());
    store.reflect(true);
    assertTrue(store.app(id).spec.quickAdd());
    assertEquals(2, store.versions(id).size());
    assertEquals(0, store.proposals(id).size());
  }

  @Test
  public void explicitNullIsNotReplacedByNewDefault() {
    AppSpec original = Templates.all().get(2);
    JSONObject raw = original.json();
    J.put(raw.optJSONArray("fields").optJSONObject(2), "defaultValue", "默认想法");
    AppSpec spec = AppSpec.parse(raw.toString());
    assertEquals("默认想法", store.effective(spec.field("thought"), J.obj()));
    assertNull(store.effective(spec.field("thought"), J.obj("thought", JSONObject.NULL)));
  }
}

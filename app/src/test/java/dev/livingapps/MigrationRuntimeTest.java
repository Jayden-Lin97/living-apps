package dev.livingapps;

import static org.junit.Assert.*;

import android.content.*;
import dev.livingapps.core.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MigrationRuntimeTest {
  private Store store;
  private String id, version;

  @Before
  public void setup() {
    Context context = RuntimeEnvironment.getApplication();
    store = ((LivingApplication) context).store;
    for (Store.App app : store.apps()) store.deleteApp(app.id);
    id = store.create(Templates.all().get(0), "test");
    version = store.app(id).versionId;
    store.saveRecord(id, version, null, row("午饭", 12.5));
  }

  @After
  public void close() {
    store.close();
  }

  private JSONObject row(String title, double amount) {
    return J.obj("item", title, "amount", amount, "category", "餐饮", "date", "2026-10-27");
  }

  @Test
  public void migrationIsAtomicPreservesIdsAndKeepsVerifiedRecoverableOriginal() {
    String recordId = store.records(id).get(0).id;
    Store.PreparedMigration plan =
        store.prepareMigration(
            id, version, MigrationPlan.scale(store.app(id).spec, "amount", 100, "分"));
    assertEquals(12.5, store.records(id).get(0).data.optDouble("amount"), 0);
    store.commitMigration(plan);
    assertEquals(recordId, store.records(id).get(0).id);
    assertEquals(1250, store.records(id).get(0).data.optInt("amount"));
    assertEquals("分", store.app(id).spec.unit());
    assertEquals(1, store.migrationBackups(id).size());
    store.saveRecord(id, store.app(id).versionId, null, row("转换后新增", 800));
    String copy = store.recoverMigrationCopy(id, store.migrationBackups(id).get(0).id);
    assertEquals(2, store.records(id).size());
    assertEquals(1, store.records(copy).size());
    assertEquals(12.5, store.records(copy).get(0).data.optDouble("amount"), 0);
  }

  @Test
  public void changedDataAfterDryRunBlocksCommit() {
    Store.PreparedMigration prepared =
        store.prepareMigration(
            id, version, MigrationPlan.scale(store.app(id).spec, "amount", 100, "分"));
    store.saveRecord(id, version, null, row("新记录", 10));
    try {
      store.commitMigration(prepared);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(version, store.app(id).versionId);
    assertEquals(0, store.migrationBackups(id).size());
    assertEquals(2, store.records(id).size());
  }

  @Test
  public void anyFailedConversionBlocksAllWritesAndBackupCreation() {
    Store.PreparedMigration prepared =
        store.prepareMigration(
            id, version, MigrationPlan.type(store.app(id).spec, "item", "number"));
    assertEquals(1, prepared.result.failed);
    try {
      store.commitMigration(prepared);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals("午饭", store.records(id).get(0).data.optString("item"));
    assertEquals(1, store.versions(id).size());
    assertTrue(store.migrationBackups(id).isEmpty());
  }

  @Test
  public void oldUiRestoreCannotCrossDataConversionEpoch() {
    AppSpec old = store.app(id).spec;
    store.commitMigration(
        store.prepareMigration(id, version, MigrationPlan.scale(old, "amount", 100, "分")));
    try {
      store.apply(id, store.app(id).versionId, old, "旧版恢复", "restore", true);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(1250, store.records(id).get(0).data.optInt("amount"));
  }

  @Test
  public void dirtyDraftBlocksBothPreparationAndCommit() {
    Store.PreparedMigration prepared =
        store.prepareMigration(
            id, version, MigrationPlan.scale(store.app(id).spec, "amount", 100, "分"));
    EditSession draft = store.beginDraft(id);
    draft.apply(draft.spec.with("compact", true), "布局");
    store.saveDraft(draft);
    try {
      store.commitMigration(prepared);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertTrue(store.hasDraft(id));
    assertEquals(version, store.app(id).versionId);
  }

  @Test
  public void corruptBackupFailsVerificationWithoutCreatingACopy() {
    store.commitMigration(
        store.prepareMigration(
            id, version, MigrationPlan.scale(store.app(id).spec, "amount", 100, "分")));
    String backup = store.migrationBackups(id).get(0).id;
    store
        .getWritableDatabase()
        .execSQL("UPDATE migration_backups SET payload='{}' WHERE id=?", new Object[] {backup});
    try {
      store.recoverMigrationCopy(id, backup);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(1, store.apps().size());
    assertEquals(1250, store.records(id).get(0).data.optInt("amount"));
  }

  @Test
  public void fixedActionExecutesOnlyOnExplicitCallAndUndoDetectsLaterEdits() {
    JSONObject raw = store.app(id).spec.json();
    J.put(raw, "schemaVersion", 2);
    J.put(
        raw,
        "actions",
        J.arr(
            J.obj("id", "lunch", "label", "午饭", "kind", "add_record", "values", row("快捷午饭", 20))));
    store.apply(id, version, AppSpec.parse(raw.toString()), "固定动作", "local", false);
    assertEquals(1, store.records(id).size());
    String current = store.app(id).versionId;
    String saved = store.runAction(id, current, "lunch");
    assertEquals(2, store.records(id).size());
    JSONObject original = J.parse(store.records(id).get(0).data.toString());
    store.saveRecord(id, current, saved, row("修改后的午饭", 30));
    try {
      store.undoAction(id, saved, original);
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(2, store.records(id).size());
    String second = store.runAction(id, current, "lunch");
    assertTrue(store.undoAction(id, second, row("快捷午饭", 20)));
    assertEquals(2, store.records(id).size());
  }

  @Test
  public void staleActionCannotWriteThroughAnOldAppVersion() {
    try {
      store.runAction(id, "old-version", "lunch");
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(1, store.records(id).size());
  }
}

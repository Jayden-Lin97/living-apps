package dev.livingapps;

import static dev.livingapps.core.Messages.tr;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import dev.livingapps.core.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

public final class Store extends SQLiteOpenHelper {
  public static final class App {
    public String id, versionId;
    public AppSpec spec;
    public boolean frozen;
    public long created;
  }

  public static final class Entry {
    public String id;
    public JSONObject data;
    public long created;
  }

  public static final class Version {
    public String id, reason, source;
    public AppSpec spec;
    public long created;
    public int dataEpoch;
  }

  public static final class Proposal {
    public String id, appId, baseVersion, rule, reason, status;
    public AppSpec candidate;
    public long created;
  }

  public Store(Context context) {
    super(context, "living-apps.db", null, 3);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE apps(id TEXT PRIMARY KEY,current_version TEXT NOT NULL,frozen INTEGER NOT"
            + " NULL DEFAULT 0,created INTEGER NOT NULL,last_auto INTEGER NOT NULL DEFAULT 0)");
    db.execSQL(
        "CREATE TABLE versions(id TEXT PRIMARY KEY,app_id TEXT NOT NULL REFERENCES apps(id) ON"
            + " DELETE CASCADE,spec TEXT NOT NULL,reason TEXT NOT NULL,source TEXT NOT NULL,created"
            + " INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE records(id TEXT PRIMARY KEY,app_id TEXT NOT NULL REFERENCES apps(id) ON"
            + " DELETE CASCADE,data TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT"
            + " NULL)");
    db.execSQL(
        "CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT,app_id TEXT NOT NULL REFERENCES"
            + " apps(id) ON DELETE CASCADE,version_id TEXT NOT NULL,kind TEXT NOT NULL,elapsed"
            + " INTEGER NOT NULL,created INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX events_lookup ON events(app_id,version_id,created)");
    db.execSQL("CREATE INDEX records_lookup ON records(app_id,created)");
    db.execSQL(
        "CREATE TABLE proposals(id TEXT PRIMARY KEY,app_id TEXT NOT NULL REFERENCES apps(id) ON"
            + " DELETE CASCADE,base_version TEXT NOT NULL,rule TEXT NOT NULL,candidate TEXT NOT"
            + " NULL,reason TEXT NOT NULL,status TEXT NOT NULL,created INTEGER NOT"
            + " NULL,UNIQUE(app_id,base_version,rule))");
    createDraftTables(db);
    createMigrationTables(db);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 2) createDraftTables(db);
    if (oldVersion < 3) createMigrationTables(db);
  }

  private boolean columnExists(SQLiteDatabase db, String table, String column) {
    try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
      while (cursor.moveToNext()) if (column.equals(cursor.getString(1))) return true;
    }
    return false;
  }

  private void createMigrationTables(SQLiteDatabase db) {
    if (!columnExists(db, "drafts", "storage_revision"))
      db.execSQL("ALTER TABLE drafts ADD COLUMN storage_revision INTEGER NOT NULL DEFAULT 0");
    if (!columnExists(db, "apps", "data_epoch"))
      db.execSQL("ALTER TABLE apps ADD COLUMN data_epoch INTEGER NOT NULL DEFAULT 0");
    if (!columnExists(db, "versions", "data_epoch"))
      db.execSQL("ALTER TABLE versions ADD COLUMN data_epoch INTEGER NOT NULL DEFAULT 0");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS migration_backups(id TEXT PRIMARY KEY,app_id TEXT NOT NULL"
            + " REFERENCES apps(id) ON DELETE CASCADE,payload TEXT NOT NULL,digest TEXT NOT"
            + " NULL,reason TEXT NOT NULL,created INTEGER NOT NULL)");
  }

  private int dataEpoch(String appId) {
    try (Cursor cursor =
        getReadableDatabase()
            .rawQuery("SELECT data_epoch FROM apps WHERE id=?", new String[] {appId})) {
      if (cursor.moveToFirst()) return cursor.getInt(0);
    }
    throw new IllegalArgumentException(tr("应用不存在"));
  }

  private void createDraftTables(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS drafts(app_id TEXT PRIMARY KEY REFERENCES apps(id) ON DELETE"
            + " CASCADE,id TEXT NOT NULL,revision INTEGER NOT NULL,payload TEXT NOT NULL,updated"
            + " INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS draft_recovery(id INTEGER PRIMARY KEY AUTOINCREMENT,app_id TEXT"
            + " NOT NULL REFERENCES apps(id) ON DELETE CASCADE,payload TEXT NOT NULL,created"
            + " INTEGER NOT NULL)");
  }

  public boolean hasDraft(String appId) {
    try (Cursor cursor =
        getReadableDatabase()
            .rawQuery("SELECT 1 FROM drafts WHERE app_id=?", new String[] {appId})) {
      return cursor.moveToFirst();
    }
  }

  public EditSession draft(String appId) {
    try (Cursor cursor =
        getReadableDatabase()
            .rawQuery("SELECT payload FROM drafts WHERE app_id=?", new String[] {appId})) {
      if (!cursor.moveToFirst()) return null;
      EditSession session = EditSession.parse(cursor.getString(0));
      if (!session.appId.equals(appId)) throw new IllegalStateException(tr("草稿所属应用不匹配"));
      return session;
    }
  }

  public EditSession beginDraft(String appId) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      EditSession existing = draft(appId);
      if (existing != null) {
        db.setTransactionSuccessful();
        return existing;
      }
      App app = app(appId);
      EditSession session = new EditSession(appId, app.versionId, app.spec);
      ContentValues values = new ContentValues();
      values.put("app_id", appId);
      values.put("id", session.id);
      values.put("revision", session.revision);
      values.put("payload", session.json().toString());
      values.put("updated", System.currentTimeMillis());
      db.insertOrThrow("drafts", null, values);
      db.setTransactionSuccessful();
      return session;
    } finally {
      db.endTransaction();
    }
  }

  public void saveDraft(EditSession session) {
    JSONObject encoded = session.json();
    J.put(encoded, "storageRevision", session.storageRevision + 1);
    String payload = encoded.toString();
    if (payload.length() > 4_000_000) throw new IllegalArgumentException(tr("草稿过大，请保存版本或导出后重新编辑"));
    ContentValues values = new ContentValues();
    values.put("payload", payload);
    values.put("revision", session.revision);
    values.put("storage_revision", session.storageRevision + 1);
    values.put("updated", System.currentTimeMillis());
    int count =
        getWritableDatabase()
            .update(
                "drafts",
                values,
                "app_id=? AND id=? AND storage_revision=?",
                new String[] {session.appId, session.id, String.valueOf(session.storageRevision)});
    if (count != 1) throw new IllegalStateException(tr("草稿已被其他编辑会话更新或关闭，请重新打开"));
    session.storageRevision++;
  }

  public void discardDraft(String appId, String draftId) {
    getWritableDatabase().delete("drafts", "app_id=? AND id=?", new String[] {appId, draftId});
  }

  public List<JSONObject> recordValues(String appId) {
    List<JSONObject> out = new ArrayList<>();
    for (Entry entry : records(appId)) out.add(entry.data);
    return out;
  }

  public EditChanges.Impact draftImpact(EditSession session) {
    return EditChanges.impact(app(session.appId).spec, session.spec, recordValues(session.appId));
  }

  public String publishDraft(EditSession session) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      EditSession stored = draft(session.appId);
      if (stored == null
          || !stored.id.equals(session.id)
          || stored.revision != session.revision
          || stored.storageRevision != session.storageRevision
          || !EditChanges.same(stored.spec.json(), session.spec.json()))
        throw new IllegalStateException(tr("草稿已在其他会话变化，请重新打开"));
      App app = app(session.appId);
      if (!app.versionId.equals(session.baseVersion))
        throw new IllegalStateException(tr("正式应用已有新版本，请先合并到最新版；你的草稿已保留"));
      EditChanges.Impact impact = draftImpact(session);
      if (!impact.allowed()) throw new IllegalArgumentException(String.join("\n", impact.problems));
      String version = app.versionId;
      if (session.changed()) {
        List<String> reasons = new ArrayList<>();
        for (EditChanges.Change change : EditChanges.between(app.spec, session.spec))
          reasons.add(change.description());
        version =
            apply(
                session.appId,
                session.baseVersion,
                session.spec,
                String.join("；", reasons),
                "editor",
                false);
      }
      discardDraft(session.appId, session.id);
      db.setTransactionSuccessful();
      return version;
    } finally {
      db.endTransaction();
    }
  }

  public void archiveDraft(EditSession session) {
    ContentValues values = new ContentValues();
    values.put("app_id", session.appId);
    values.put("payload", session.json().toString());
    values.put("created", System.currentTimeMillis());
    getWritableDatabase().insertOrThrow("draft_recovery", null, values);
    getWritableDatabase()
        .execSQL(
            "DELETE FROM draft_recovery WHERE app_id=? AND id NOT IN (SELECT id FROM draft_recovery"
                + " WHERE app_id=? ORDER BY id DESC LIMIT 5)",
            new Object[] {session.appId, session.appId});
  }

  public List<String> draftRecoveries(String appId) {
    List<String> out = new ArrayList<>();
    try (Cursor cursor =
        getReadableDatabase()
            .rawQuery(
                "SELECT payload FROM draft_recovery WHERE app_id=? ORDER BY id DESC LIMIT 5",
                new String[] {appId})) {
      while (cursor.moveToNext()) out.add(cursor.getString(0));
    }
    return out;
  }

  private static String id() {
    return UUID.randomUUID().toString();
  }

  public List<App> apps() {
    List<App> result = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT a.id,a.current_version,a.frozen,a.created,v.spec FROM apps a JOIN versions"
                    + " v ON v.id=a.current_version ORDER BY a.created DESC,a.rowid DESC",
                null)) {
      while (c.moveToNext()) {
        App a = new App();
        a.id = c.getString(0);
        a.versionId = c.getString(1);
        a.frozen = c.getInt(2) != 0;
        a.created = c.getLong(3);
        a.spec = AppSpec.parse(c.getString(4));
        result.add(a);
      }
    }
    return result;
  }

  public App app(String appId) {
    for (App app : apps()) if (app.id.equals(appId)) return app;
    throw new IllegalArgumentException(tr("应用不存在"));
  }

  public String create(AppSpec spec, String source) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      String appId = id(), versionId = id();
      long now = System.currentTimeMillis();
      ContentValues a = new ContentValues();
      a.put("id", appId);
      a.put("current_version", versionId);
      a.put("created", now);
      db.insertOrThrow("apps", null, a);
      insertVersion(db, versionId, appId, spec, tr("创建应用"), source, now);
      db.setTransactionSuccessful();
      return appId;
    } finally {
      db.endTransaction();
    }
  }

  private void insertVersion(
      SQLiteDatabase db,
      String version,
      String app,
      AppSpec spec,
      String reason,
      String source,
      long now) {
    ContentValues v = new ContentValues();
    v.put("id", version);
    v.put("app_id", app);
    v.put("spec", spec.toString());
    v.put("reason", reason);
    v.put("source", source);
    v.put("created", now);
    v.put("data_epoch", dataEpoch(app));
    db.insertOrThrow("versions", null, v);
  }

  public String apply(
      String appId,
      String expectedVersion,
      AppSpec next,
      String reason,
      String source,
      boolean restoring) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      App app = app(appId);
      if (!app.versionId.equals(expectedVersion))
        throw new IllegalStateException(tr("应用已在别处更新，请重新预览本次修改"));
      if (source.equals("auto") && app.frozen) throw new IllegalStateException(tr("应用已固定，不再自动修改"));
      if (source.equals("auto") && hasDraft(appId))
        throw new IllegalStateException(tr("应用有未完成草稿，暂缓自动采用"));
      if (source.equals("auto") && !VersionPolicy.autoAllowed(app.spec, next))
        throw new IllegalArgumentException(tr("这项修改不在自动优化允许范围内"));
      if (!restoring) {
        List<String> errors = VersionPolicy.errors(app.spec, next);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
      } else {
        boolean compatible = false;
        for (Version old : versions(appId))
          if (old.dataEpoch == dataEpoch(appId) && EditChanges.same(old.spec.json(), next.json())) {
            compatible = true;
            break;
          }
        if (!compatible)
          throw new IllegalStateException(tr("该版本早于数据转换；请从“转换前备份”恢复为副本，避免用旧类型解释新记录"));
      }
      String version = id();
      long now = System.currentTimeMillis();
      insertVersion(db, version, appId, next, reason, source, now);
      ContentValues values = new ContentValues();
      values.put("current_version", version);
      if (source.equals("auto")) values.put("last_auto", now);
      db.update("apps", values, "id=?", new String[] {appId});
      db.execSQL(
          "UPDATE proposals SET status='stale' WHERE app_id=? AND status='pending'",
          new Object[] {appId});
      db.setTransactionSuccessful();
      return version;
    } finally {
      db.endTransaction();
    }
  }

  public void freeze(String appId, boolean frozen) {
    ContentValues v = new ContentValues();
    v.put("frozen", frozen ? 1 : 0);
    getWritableDatabase().update("apps", v, "id=?", new String[] {appId});
  }

  public void deleteApp(String appId) {
    getWritableDatabase().delete("apps", "id=?", new String[] {appId});
  }

  public List<Version> versions(String appId) {
    List<Version> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,spec,reason,source,created,data_epoch FROM versions WHERE app_id=? ORDER"
                    + " BY created DESC,rowid DESC",
                new String[] {appId})) {
      while (c.moveToNext()) {
        Version v = new Version();
        v.id = c.getString(0);
        v.spec = AppSpec.parse(c.getString(1));
        v.reason = c.getString(2);
        v.source = c.getString(3);
        v.created = c.getLong(4);
        v.dataEpoch = c.getInt(5);
        out.add(v);
      }
    }
    return out;
  }

  public List<Entry> records(String appId) {
    List<Entry> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,data,created FROM records WHERE app_id=? ORDER BY created DESC,rowid"
                    + " DESC",
                new String[] {appId})) {
      while (c.moveToNext()) {
        Entry r = new Entry();
        r.id = c.getString(0);
        r.data = J.parse(c.getString(1));
        r.created = c.getLong(2);
        out.add(r);
      }
    }
    return out;
  }

  public String saveRecord(
      String appId, String expectedVersion, String recordId, JSONObject fields) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      App app = app(appId);
      if (!app.versionId.equals(expectedVersion))
        throw new IllegalStateException(tr("应用已更新，请重新打开表单后保存"));
      List<String> errors = app.spec.validateRecord(fields);
      if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
      long now = System.currentTimeMillis();
      JSONObject data = J.obj();
      if (recordId != null) {
        boolean found = false;
        for (Entry e : records(appId))
          if (e.id.equals(recordId)) {
            data = e.data;
            found = true;
            break;
          }
        if (!found) throw new IllegalArgumentException(tr("记录已删除"));
      }
      // Keep fields hidden by an older restored UI; never discard historical values.
      Iterator<String> keys = fields.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        J.put(data, key, fields.opt(key));
      }
      ContentValues values = new ContentValues();
      values.put("data", data.toString());
      values.put("updated", now);
      if (recordId == null) {
        recordId = id();
        values.put("id", recordId);
        values.put("app_id", appId);
        values.put("created", now);
        db.insertOrThrow("records", null, values);
      } else db.update("records", values, "id=? AND app_id=?", new String[] {recordId, appId});
      db.setTransactionSuccessful();
      return recordId;
    } finally {
      db.endTransaction();
    }
  }

  public String runAction(String appId, String expectedVersion, String actionId) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      App app = app(appId);
      if (!app.versionId.equals(expectedVersion))
        throw new IllegalStateException(tr("应用已更新，请重新打开后点击动作"));
      for (ActionSpec action : app.spec.actions())
        if (action.id.equals(actionId)) {
          String result = saveRecord(appId, expectedVersion, null, action.resolved(app.spec));
          db.setTransactionSuccessful();
          return result;
        }
      throw new IllegalArgumentException(tr("动作已不存在"));
    } finally {
      db.endTransaction();
    }
  }

  public void deleteRecord(String appId, String recordId) {
    getWritableDatabase().delete("records", "id=? AND app_id=?", new String[] {recordId, appId});
  }

  public boolean undoAction(String appId, String recordId, JSONObject original) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      for (Entry entry : records(appId))
        if (entry.id.equals(recordId)) {
          if (!EditChanges.same(entry.data, original))
            throw new IllegalStateException(tr("这条记录已经编辑过，请到记录列表检查后再决定是否删除"));
          deleteRecord(appId, recordId);
          db.setTransactionSuccessful();
          return true;
        }
      db.setTransactionSuccessful();
      return false;
    } finally {
      db.endTransaction();
    }
  }

  public Object effective(AppSpec.Field field, JSONObject data) {
    if (!data.has(field.id)) return field.defaultValue;
    Object value = data.opt(field.id);
    return value == JSONObject.NULL ? null : value;
  }

  public void event(String appId, String version, String kind, long elapsed) {
    if (!Arrays.asList("open", "save", "stats", "validation_error", "cancel").contains(kind))
      throw new IllegalArgumentException(tr("未知使用事件"));
    ContentValues e = new ContentValues();
    e.put("app_id", appId);
    e.put("version_id", version);
    e.put("kind", kind);
    e.put("elapsed", Math.max(0, elapsed));
    e.put("created", System.currentTimeMillis());
    getWritableDatabase().insertOrThrow("events", null, e);
  }

  public ReflectionEngine.Summary summary(String appId, String version) {
    List<ReflectionEngine.Event> events = new ArrayList<>();
    String where = "app_id=? AND created>=?";
    List<String> args =
        new ArrayList<>(
            Arrays.asList(
                appId, String.valueOf(System.currentTimeMillis() - 7 * ReflectionEngine.DAY)));
    if (version != null) {
      where += " AND version_id=?";
      args.add(version);
    }
    try (Cursor c =
        getReadableDatabase()
            .query(
                "events",
                new String[] {"kind", "elapsed", "created"},
                where,
                args.toArray(new String[0]),
                null,
                null,
                "created ASC")) {
      while (c.moveToNext())
        events.add(new ReflectionEngine.Event(c.getString(0), c.getLong(1), c.getLong(2)));
    }
    return ReflectionEngine.summarize(events);
  }

  public List<Proposal> proposals(String appId) {
    List<Proposal> out = new ArrayList<>();
    String where = appId == null ? "status='pending'" : "status='pending' AND app_id=?";
    try (Cursor c =
        getReadableDatabase()
            .query(
                "proposals",
                new String[] {
                  "id", "app_id", "base_version", "rule", "candidate", "reason", "status", "created"
                },
                where,
                appId == null ? null : new String[] {appId},
                null,
                null,
                "created DESC")) {
      while (c.moveToNext()) {
        Proposal p = new Proposal();
        p.id = c.getString(0);
        p.appId = c.getString(1);
        p.baseVersion = c.getString(2);
        p.rule = c.getString(3);
        p.candidate = AppSpec.parse(c.getString(4));
        p.reason = c.getString(5);
        p.status = c.getString(6);
        p.created = c.getLong(7);
        out.add(p);
      }
    }
    return out;
  }

  public void dismiss(String proposalId) {
    ContentValues v = new ContentValues();
    v.put("status", "dismissed");
    getWritableDatabase().update("proposals", v, "id=?", new String[] {proposalId});
  }

  public void reflect(boolean automatic) {
    for (App app : apps()) {
      if (app.frozen) continue;
      for (ReflectionEngine.Suggestion suggestion :
          ReflectionEngine.suggest(app.spec, summary(app.id, app.versionId))) {
        ContentValues p = new ContentValues();
        p.put("id", id());
        p.put("app_id", app.id);
        p.put("base_version", app.versionId);
        p.put("rule", suggestion.rule);
        p.put("candidate", suggestion.candidate.toString());
        p.put("reason", suggestion.reason);
        p.put("status", "pending");
        p.put("created", System.currentTimeMillis());
        long inserted =
            getWritableDatabase()
                .insertWithOnConflict("proposals", null, p, SQLiteDatabase.CONFLICT_IGNORE);
        boolean pending = inserted != -1;
        if (!pending)
          try (Cursor status =
              getReadableDatabase()
                  .rawQuery(
                      "SELECT status FROM proposals WHERE app_id=? AND base_version=? AND rule=?",
                      new String[] {app.id, app.versionId, suggestion.rule})) {
            pending = status.moveToFirst() && status.getString(0).equals("pending");
          }
        if (pending
            && automatic
            && !hasDraft(app.id)
            && suggestion.canAutoApply
            && VersionPolicy.autoAllowed(app.spec, suggestion.candidate)) {
          try (Cursor c =
              getReadableDatabase()
                  .rawQuery("SELECT last_auto FROM apps WHERE id=?", new String[] {app.id})) {
            if (c.moveToFirst()
                && System.currentTimeMillis() - c.getLong(0) >= ReflectionEngine.DAY) {
              apply(app.id, app.versionId, suggestion.candidate, suggestion.reason, "auto", false);
              break;
            }
          }
        }
      }
    }
    getWritableDatabase()
        .delete(
            "events",
            "created<?",
            new String[] {String.valueOf(System.currentTimeMillis() - 30 * ReflectionEngine.DAY)});
  }

  public void clearEvents() {
    getWritableDatabase().delete("events", null, null);
    getWritableDatabase().delete("proposals", "status='pending'", null);
  }

  public static final class PreparedMigration {
    public final String appId, baseVersion, fingerprint;
    public final MigrationPlan plan;
    public final MigrationPlan.Result result;

    private PreparedMigration(
        String appId,
        String version,
        String hash,
        MigrationPlan plan,
        MigrationPlan.Result result) {
      this.appId = appId;
      baseVersion = version;
      fingerprint = hash;
      this.plan = plan;
      this.result = result;
    }
  }

  public static final class MigrationBackup {
    public String id, reason;
    public long created;
  }

  private static String digest(String data) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
      return out.toString();
    } catch (Exception e) {
      throw new IllegalStateException(tr("无法校验本机备份"), e);
    }
  }

  private String migrationSnapshot(String appId) {
    App app = app(appId);
    List<Entry> rows = records(appId);
    if (rows.size() > 5000) throw new IllegalArgumentException(tr("转换暂限每应用 5000 条记录，请先导出并整理数据"));
    JSONArray data = new JSONArray();
    for (Entry entry : rows)
      data.put(J.obj("id", entry.id, "data", entry.data, "created", entry.created));
    String payload =
        J.obj(
                "format",
                "living-apps-backup",
                "version",
                1,
                "apps",
                J.arr(J.obj("spec", app.spec.json(), "frozen", app.frozen, "records", data)))
            .toString();
    if (payload.length() > 5_000_000)
      throw new IllegalArgumentException(tr("转换前备份超过 5 MB，请先导出并整理数据"));
    return payload;
  }

  public PreparedMigration prepareMigration(
      String appId, String expectedVersion, MigrationPlan plan) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      App app = app(appId);
      if (!app.versionId.equals(expectedVersion)
          || !EditChanges.same(app.spec.json(), plan.before.json()))
        throw new IllegalStateException(tr("应用已改变，请重新演算转换"));
      EditSession draft = draft(appId);
      if (draft != null && (draft.changed() || !draft.baseVersion.equals(app.versionId)))
        throw new IllegalStateException(tr("请先保存或放弃普通编辑草稿，再进入数据转换"));
      PreparedMigration prepared =
          new PreparedMigration(
              appId,
              app.versionId,
              digest(migrationSnapshot(appId)),
              plan,
              plan.evaluate(recordValues(appId)));
      db.setTransactionSuccessful();
      return prepared;
    } finally {
      db.endTransaction();
    }
  }

  public String commitMigration(PreparedMigration prepared) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      PreparedMigration checked =
          prepareMigration(prepared.appId, prepared.baseVersion, prepared.plan);
      if (!checked.fingerprint.equals(prepared.fingerprint))
        throw new IllegalStateException(tr("记录在演算后已经变化，请重新检查数量与转换结果"));
      if (!checked.result.allowed()) throw new IllegalArgumentException(tr("仍有转换失败的记录，未改写任何数据"));
      String payload = migrationSnapshot(prepared.appId), backupId = id();
      ContentValues backup = new ContentValues();
      backup.put("id", backupId);
      backup.put("app_id", prepared.appId);
      backup.put("payload", payload);
      backup.put("digest", digest(payload));
      backup.put("reason", prepared.plan.description);
      backup.put("created", System.currentTimeMillis());
      db.insertOrThrow("migration_backups", null, backup);
      verifiedMigrationBackup(prepared.appId, backupId);
      List<Entry> rows = records(prepared.appId);
      for (int i = 0; i < rows.size(); i++) {
        ContentValues values = new ContentValues();
        values.put("data", checked.result.records.get(i).toString());
        values.put("updated", System.currentTimeMillis());
        if (db.update(
                "records",
                values,
                "app_id=? AND id=?",
                new String[] {prepared.appId, rows.get(i).id})
            != 1) throw new IllegalStateException(tr("转换时记录变化，已回滚"));
      }
      EditSession draft = draft(prepared.appId);
      if (draft != null) {
        archiveDraft(draft);
        discardDraft(draft.appId, draft.id);
      }
      db.execSQL(
          "UPDATE apps SET data_epoch=data_epoch+1 WHERE id=?", new Object[] {prepared.appId});
      String version = id();
      insertVersion(
          db,
          version,
          prepared.appId,
          prepared.plan.after,
          prepared.plan.description,
          "migration",
          System.currentTimeMillis());
      ContentValues current = new ContentValues();
      current.put("current_version", version);
      db.update("apps", current, "id=?", new String[] {prepared.appId});
      db.execSQL(
          "UPDATE proposals SET status='stale' WHERE app_id=? AND status='pending'",
          new Object[] {prepared.appId});
      db.setTransactionSuccessful();
      return version;
    } finally {
      db.endTransaction();
    }
  }

  private String verifiedMigrationBackup(String appId, String backupId) {
    try (Cursor cursor =
        getReadableDatabase()
            .rawQuery(
                "SELECT payload,digest FROM migration_backups WHERE app_id=? AND id=?",
                new String[] {appId, backupId})) {
      if (!cursor.moveToFirst()) throw new IllegalArgumentException(tr("找不到转换备份"));
      String payload = cursor.getString(0);
      if (!digest(payload).equals(cursor.getString(1)))
        throw new IllegalStateException(tr("转换备份校验失败，无法恢复副本"));
      return payload;
    }
  }

  public List<MigrationBackup> migrationBackups(String appId) {
    List<MigrationBackup> out = new ArrayList<>();
    try (Cursor cursor =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,reason,created FROM migration_backups WHERE app_id=? ORDER BY created"
                    + " DESC,rowid DESC",
                new String[] {appId})) {
      while (cursor.moveToNext()) {
        MigrationBackup backup = new MigrationBackup();
        backup.id = cursor.getString(0);
        backup.reason = cursor.getString(1);
        backup.created = cursor.getLong(2);
        out.add(backup);
      }
    }
    return out;
  }

  public String recoverMigrationCopy(String appId, String backupId) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject backup = J.parse(verifiedMigrationBackup(appId, backupId));
      JSONObject app = backup.optJSONArray("apps").optJSONObject(0);
      JSONObject raw = app.optJSONObject("spec");
      String name = raw.optString("name");
      J.put(raw, "name", name.substring(0, Math.min(name.length(), 34)) + tr("·转换前副本"));
      Set<String> oldIds = new HashSet<>();
      for (App existing : apps()) oldIds.add(existing.id);
      importBackup(backup.toString());
      for (App restored : apps())
        if (!oldIds.contains(restored.id)) {
          db.setTransactionSuccessful();
          return restored.id;
        }
      throw new IllegalStateException(tr("未能创建恢复副本"));
    } finally {
      db.endTransaction();
    }
  }

  public String exportBackup() {
    JSONArray apps = new JSONArray();
    for (App app : apps()) {
      JSONArray records = new JSONArray();
      for (Entry entry : records(app.id))
        records.put(J.obj("data", entry.data, "created", entry.created));
      apps.put(J.obj("spec", app.spec.json(), "frozen", app.frozen, "records", records));
    }
    return J.obj("format", "living-apps-backup", "version", 1, "apps", apps).toString();
  }

  public int importBackup(String text) {
    if (text.length() > 5_000_000) throw new IllegalArgumentException(tr("备份不能超过 5 MB"));
    JSONObject root = J.parse(text);
    JSONArray array = root.optJSONArray("apps");
    if (!root.optString("format").equals("living-apps-backup")
        || root.optInt("version") != 1
        || array == null
        || array.length() > 50) throw new IllegalArgumentException(tr("不支持的备份格式"));
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      int total = 0;
      for (int i = 0; i < array.length(); i++) {
        JSONObject raw = array.optJSONObject(i);
        if (raw == null || raw.optJSONObject("spec") == null)
          throw new IllegalArgumentException(tr("备份应用损坏"));
        AppSpec spec = AppSpec.parse(raw.optJSONObject("spec").toString());
        String appId = create(spec, "import");
        freeze(appId, raw.optBoolean("frozen"));
        JSONArray records = raw.optJSONArray("records");
        if (records == null || records.length() > 5000)
          throw new IllegalArgumentException(tr("记录数量超出限制"));
        for (int k = 0; k < records.length(); k++) {
          JSONObject row = records.optJSONObject(k),
              data = row == null ? null : row.optJSONObject("data");
          if (data == null
              || data.length() > 100
              || data.toString().length() > 100000
              || ++total > 20000) throw new IllegalArgumentException(tr("备份记录无效"));
          Iterator<String> keys = data.keys();
          while (keys.hasNext()) {
            Object value = data.opt(keys.next());
            if (value instanceof JSONObject
                || value instanceof JSONArray
                || value instanceof String && ((String) value).length() > 4000)
              throw new IllegalArgumentException(tr("备份记录包含不支持的数据"));
          }
          ContentValues values = new ContentValues();
          values.put("id", id());
          values.put("app_id", appId);
          values.put("data", data.toString());
          long created = row.optLong("created", System.currentTimeMillis());
          values.put("created", created);
          values.put("updated", created);
          db.insertOrThrow("records", null, values);
        }
      }
      db.setTransactionSuccessful();
      return array.length();
    } finally {
      db.endTransaction();
    }
  }
}

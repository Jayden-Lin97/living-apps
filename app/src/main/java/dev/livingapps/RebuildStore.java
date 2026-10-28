package dev.livingapps;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import dev.livingapps.core.*;
import java.util.*;

/** Separate local definitions/history. Never copies or changes the source app's database. */
final class RebuildStore extends SQLiteOpenHelper {
  static final class Entry {
    String id;
    long revision;
    RebuildSpec spec;
    Set<String> verified = new HashSet<>();
  }

  RebuildStore(Context c) {
    super(c, "living-rebuild.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE shells(id TEXT PRIMARY KEY,revision INTEGER NOT NULL,spec TEXT NOT"
            + " NULL,verified TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE history(shell TEXT NOT NULL,revision INTEGER NOT NULL,spec TEXT NOT"
            + " NULL,PRIMARY KEY(shell,revision))");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int next) {}

  List<Entry> all() {
    List<Entry> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT id FROM shells ORDER BY rowid DESC", null)) {
      while (c.moveToNext()) out.add(get(c.getString(0)));
    }
    return out;
  }

  Entry get(String id) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT revision,spec,verified FROM shells WHERE id=?", new String[] {id})) {
      if (!c.moveToFirst()) throw new IllegalArgumentException("重构应用不存在");
      Entry e = new Entry();
      e.id = id;
      e.revision = c.getLong(0);
      e.spec = new RebuildSpec(J.parse(c.getString(1)));
      org.json.JSONArray v = J.parse(c.getString(2)).optJSONArray("ids");
      for (int i = 0; v != null && i < v.length(); i++) e.verified.add(v.optString(i));
      return e;
    }
  }

  String create(RebuildSpec spec) {
    String id = UUID.randomUUID().toString();
    ContentValues v = new ContentValues();
    v.put("id", id);
    v.put("revision", 1);
    v.put("spec", spec.json().toString());
    v.put("verified", "{\"ids\":[]}");
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.insertOrThrow("shells", null, v);
      history(db, id, 1, spec);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    return id;
  }

  void save(String id, long baseline, RebuildSpec spec) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      Entry e = get(id);
      if (e.revision != baseline) throw new IllegalStateException("界面已有新版本，请重新打开");
      if (!e.spec.pkg().equals(spec.pkg()) || !e.spec.certificate().equals(spec.certificate()))
        throw new IllegalArgumentException("不能更换来源或签名");
      ContentValues v = new ContentValues();
      v.put("revision", baseline + 1);
      v.put("spec", spec.json().toString());
      v.put("verified", "{\"ids\":[]}");
      if (db.update("shells", v, "id=? AND revision=?", new String[] {id, "" + baseline}) != 1)
        throw new IllegalStateException("版本已变化");
      history(db, id, baseline + 1, spec);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private void history(SQLiteDatabase db, String id, long revision, RebuildSpec spec) {
    ContentValues v = new ContentValues();
    v.put("shell", id);
    v.put("revision", revision);
    v.put("spec", spec.json().toString());
    db.insertOrThrow("history", null, v);
  }

  List<Long> history(String id) {
    List<Long> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT revision FROM history WHERE shell=? ORDER BY revision DESC",
                new String[] {id})) {
      while (c.moveToNext()) out.add(c.getLong(0));
    }
    return out;
  }

  void restore(String id, long baseline, long version) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT spec FROM history WHERE shell=? AND revision=?",
                new String[] {id, "" + version})) {
      if (!c.moveToFirst()) throw new IllegalArgumentException("历史不存在");
      save(id, baseline, new RebuildSpec(J.parse(c.getString(0))));
    }
  }

  void verify(String id, long baseline, String widget) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      Entry e = get(id);
      if (e.revision != baseline) throw new IllegalStateException("版本已变化，需要重新试用");
      e.spec.widget(widget);
      e.verified.add(widget);
      org.json.JSONArray ids = J.arr();
      for (String x : e.verified) ids.put(x);
      ContentValues v = new ContentValues();
      v.put("verified", J.obj("ids", ids).toString());
      db.update("shells", v, "id=? AND revision=?", new String[] {id, "" + baseline});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  void delete(String id) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("history", "shell=?", new String[] {id});
      db.delete("shells", "id=?", new String[] {id});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }
}

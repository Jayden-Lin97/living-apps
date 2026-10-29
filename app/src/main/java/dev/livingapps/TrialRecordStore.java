package dev.livingapps;

import static dev.livingapps.core.Messages.tr;

import dev.livingapps.core.*;
import java.util.*;
import org.json.*;

/** Isolated records live inside the draft payload, never the business records table. */
public final class TrialRecordStore {
  private final EditSession session;

  public TrialRecordStore(EditSession session) {
    this.session = session;
  }

  public List<Store.Entry> records() {
    List<Store.Entry> out = new ArrayList<>();
    for (int i = 0; i < session.trial.length(); i++) {
      JSONObject row = session.trial.optJSONObject(i);
      Store.Entry e = new Store.Entry();
      e.id = row.optString("id");
      e.created = row.optLong("created");
      e.data = J.parse(row.optJSONObject("data").toString());
      out.add(e);
    }
    return out;
  }

  public void save(String id, JSONObject data) {
    List<String> errors = session.spec.validateRecord(data);
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
    List<Store.Entry> rows = records();
    boolean found = false;
    for (Store.Entry row : rows)
      if (row.id.equals(id)) {
        JSONObject merged = J.parse(row.data.toString());
        data.keys().forEachRemaining(k -> J.put(merged, k, data.opt(k)));
        row.data = merged;
        found = true;
      }
    if (id != null && !found) throw new IllegalArgumentException(tr("临时记录已不存在"));
    if (id == null) {
      if (rows.size() >= 100) throw new IllegalArgumentException(tr("试用记录最多 100 条，可清空后继续"));
      Store.Entry row = new Store.Entry();
      row.id = UUID.randomUUID().toString();
      row.created = System.currentTimeMillis();
      row.data = J.parse(data.toString());
      rows.add(0, row);
    }
    write(rows);
  }

  public void delete(String id) {
    List<Store.Entry> rows = records();
    rows.removeIf(row -> row.id.equals(id));
    write(rows);
  }

  public void clear() {
    session.trial = new JSONArray();
  }

  public void seed(List<Store.Entry> source) {
    List<Store.Entry> rows = new ArrayList<>();
    for (Store.Entry e : source.subList(0, Math.min(20, source.size()))) {
      Store.Entry row = new Store.Entry();
      row.id = UUID.randomUUID().toString();
      row.created = e.created;
      row.data = J.parse(e.data.toString());
      rows.add(row);
    }
    write(rows);
  }

  private void write(List<Store.Entry> rows) {
    JSONArray array = new JSONArray();
    for (Store.Entry e : rows) array.put(J.obj("id", e.id, "created", e.created, "data", e.data));
    if (array.toString().length() > 1_000_000)
      throw new IllegalArgumentException(tr("试用副本过大，请减少临时记录后继续"));
    session.trial = array;
  }
}

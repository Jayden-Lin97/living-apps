package dev.livingapps.core;

import static dev.livingapps.core.Messages.tr;

import java.util.*;
import org.json.*;

/** Persistable editing session. Business records and trial records never enter model context. */
public final class EditSession {
  public final String id, appId;
  public String baseVersion,
      selected = "app",
      screen = "form",
      tab = "quick",
      prompt = "",
      status = tr("草稿已保存在本机");
  public int panel = 1;
  public long revision, storageRevision;
  public AppSpec base, spec;
  private final List<JSONObject> undo = new ArrayList<>(), redo = new ArrayList<>();
  private JSONArray messages = new JSONArray();
  public JSONArray trial = new JSONArray();
  public JSONObject pending;
  public JSONObject controls = J.obj();
  public boolean scopeLocked = true;

  public EditSession(String appId, String version, AppSpec spec) {
    this.id = UUID.randomUUID().toString();
    this.appId = appId;
    this.baseVersion = version;
    this.base = spec;
    this.spec = spec;
  }

  private EditSession(JSONObject json) {
    id = json.optString("id");
    appId = json.optString("appId");
    baseVersion = json.optString("baseVersion");
    if (id.isEmpty() || appId.isEmpty() || baseVersion.isEmpty())
      throw new IllegalArgumentException(tr("草稿身份不完整"));
    base = AppSpec.parse(json.optJSONObject("base").toString());
    spec = AppSpec.parse(json.optJSONObject("spec").toString());
    revision = json.optLong("revision");
    storageRevision = json.optLong("storageRevision");
    selected = json.optString("selected", "app");
    screen = json.optString("screen", "form");
    tab = json.optString("tab", "quick");
    prompt = json.optString("prompt");
    status = json.optString("status");
    panel = Math.max(0, Math.min(2, json.optInt("panel", 1)));
    readHistory(json.optJSONArray("undo"), undo);
    readHistory(json.optJSONArray("redo"), redo);
    messages =
        json.optJSONArray("messages") == null ? new JSONArray() : json.optJSONArray("messages");
    trial = json.optJSONArray("trial") == null ? new JSONArray() : json.optJSONArray("trial");
    pending = json.optJSONObject("pending");
    controls = json.optJSONObject("controls") == null ? J.obj() : json.optJSONObject("controls");
    scopeLocked = json.optBoolean("scopeLocked", true);
    checkSelection();
  }

  public static EditSession parse(String value) {
    if (value.length() > 4_000_000) throw new IllegalArgumentException(tr("草稿过大"));
    return new EditSession(J.parse(value));
  }

  private static void readHistory(JSONArray rows, List<JSONObject> out) {
    for (int i = Math.max(0, rows == null ? 0 : rows.length() - 30);
        rows != null && i < rows.length();
        i++) out.add(J.parse(rows.optJSONObject(i).toString()));
  }

  public JSONObject json() {
    return J.obj(
        "id",
        id,
        "appId",
        appId,
        "baseVersion",
        baseVersion,
        "base",
        base.json(),
        "spec",
        spec.json(),
        "revision",
        revision,
        "storageRevision",
        storageRevision,
        "selected",
        selected,
        "screen",
        screen,
        "tab",
        tab,
        "panel",
        panel,
        "prompt",
        prompt,
        "status",
        status,
        "undo",
        new JSONArray(undo),
        "redo",
        new JSONArray(redo),
        "messages",
        messages,
        "trial",
        trial,
        "pending",
        pending == null ? JSONObject.NULL : pending,
        "controls",
        controls,
        "scopeLocked",
        scopeLocked);
  }

  public boolean changed() {
    return !EditChanges.between(base, spec).isEmpty();
  }

  public boolean canUndo() {
    return !undo.isEmpty();
  }

  public boolean canRedo() {
    return !redo.isEmpty();
  }

  private void checkSelection() {
    if (selected.startsWith("field:") && spec.field(selected.substring(6)) == null)
      selected = "app";
    if (selected.startsWith("action:")
        && spec.actions().stream().noneMatch(a -> selected.equals("action:" + a.id)))
      selected = "app";
  }

  public void apply(AppSpec next, String reason) {
    List<String> errors = VersionPolicy.errors(base, next);
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
    if (EditChanges.between(spec, next).isEmpty()) {
      status = tr("没有新的修改");
      return;
    }
    undo.add(J.obj("spec", spec.json(), "reason", reason));
    if (undo.size() > 30) undo.remove(0);
    redo.clear();
    spec = next;
    revision++;
    checkSelection();
    status = reason + tr(" · 已存草稿");
  }

  public void undo() {
    if (!canUndo()) return;
    JSONObject row = undo.remove(undo.size() - 1);
    redo.add(J.obj("spec", spec.json(), "reason", row.optString("reason")));
    spec = AppSpec.parse(row.optJSONObject("spec").toString());
    revision++;
    checkSelection();
    status = tr("已撤销：") + row.optString("reason");
  }

  public void redo() {
    if (!canRedo()) return;
    JSONObject row = redo.remove(redo.size() - 1);
    undo.add(J.obj("spec", spec.json(), "reason", row.optString("reason")));
    spec = AppSpec.parse(row.optJSONObject("spec").toString());
    revision++;
    checkSelection();
    status = tr("已重做：") + row.optString("reason");
  }

  public void rebase(String version, AppSpec latest, AppSpec merged) {
    List<String> errors = VersionPolicy.errors(latest, merged);
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
    baseVersion = version;
    base = latest;
    spec = merged;
    undo.clear();
    redo.clear();
    revision++;
    pending = null;
    checkSelection();
    status = tr("已基于最新版本合并，尚未发布");
  }

  public void message(String role, String text) {
    messages.put(
        J.obj("role", role, "text", text.length() > 2400 ? text.substring(0, 2400) : text));
    if (messages.length() > 12) {
      JSONArray next = new JSONArray();
      for (int i = messages.length() - 12; i < messages.length(); i++) next.put(messages.opt(i));
      messages = next;
    }
  }

  public JSONArray messages() {
    return J.parse(J.obj("rows", messages).toString()).optJSONArray("rows");
  }

  public JSONObject requestContext() {
    JSONArray context = new JSONArray();
    for (int i = Math.max(0, messages.length() - 6); i < messages.length(); i++)
      context.put(messages.opt(i));
    return J.obj(
        "app",
        spec.json(),
        "selected",
        selected,
        "draftRevision",
        revision,
        "appId",
        appId,
        "baseVersion",
        baseVersion,
        "scopeLocked",
        scopeLocked,
        "conversation",
        context,
        "capabilities",
        J.arr(
            "app_properties",
            "field_label",
            "field_order",
            "field_options_append",
            "field_default",
            "optional_field_add",
            "field_group",
            "field_hide",
            "tap_add_record_preset"));
  }

  public AppSpec field(String fieldId, String key, Object value) {
    JSONObject raw = spec.json();
    JSONObject field = EditChanges.fields(raw).get(fieldId);
    if (field == null) throw new IllegalArgumentException(tr("该记录项已不存在"));
    if (key.equals("group") || key.equals("hidden")) J.put(raw, "schemaVersion", 2);
    if (value == null) field.remove(key);
    else J.put(field, key, value);
    JSONArray fields = new JSONArray();
    for (JSONObject f : EditChanges.fields(raw).values()) fields.put(f);
    J.put(raw, "fields", fields);
    return AppSpec.parse(raw.toString());
  }

  public AppSpec move(String fieldId, int offset) {
    JSONObject raw = spec.json();
    List<JSONObject> fields = new ArrayList<>(EditChanges.fields(raw).values());
    int old = -1;
    for (int i = 0; i < fields.size(); i++)
      if (fields.get(i).optString("id").equals(fieldId)) old = i;
    if (old < 0 || old + offset < 0 || old + offset >= fields.size()) return spec;
    JSONObject f = fields.remove(old);
    fields.add(old + offset, f);
    J.put(raw, "fields", new JSONArray(fields));
    return AppSpec.parse(raw.toString());
  }
}

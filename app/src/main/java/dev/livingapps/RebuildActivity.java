package dev.livingapps;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Standard creation/editing flow for interfaces backed by an installed Android app. */
public final class RebuildActivity extends Activity {
  private Ui ui;
  private RebuildStore store;
  private LinearLayout body;
  private String selected;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private ResponsesClient client;
  private Future<?> request;
  private int requestToken;
  private AlertDialog generationDialog;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    ui = new Ui(this);
    store = new RebuildStore(this);
    selected = state == null ? getIntent().getStringExtra("shell") : state.getString("shell");
  }

  @Override
  protected void onResume() {
    super.onResume();
    showSelected();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    selected = intent.getStringExtra("shell");
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("shell", selected);
  }

  @Override
  protected void onDestroy() {
    requestToken++;
    if (client != null) client.cancel();
    if (request != null) request.cancel(true);
    if (generationDialog != null) generationDialog.dismiss();
    executor.shutdownNow();
    store.close();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (selected != null) {
      selected = null;
      library();
    } else super.onBackPressed();
  }

  private void showSelected() {
    if (selected == null) library();
    else
      try {
        detail();
      } catch (IllegalArgumentException e) {
        selected = null;
        library();
      }
  }

  private void guard(Runnable action) {
    try {
      action.run();
    } catch (Exception e) {
      new AlertDialog.Builder(this)
          .setTitle("暂时没有完成")
          .setMessage(e.getMessage() == null ? "请重试" : e.getMessage())
          .setPositiveButton("知道了", null)
          .show();
    }
  }

  private void page(String title, String subtitle, Runnable back) {
    LinearLayout root = ui.col();
    root.setBackgroundColor(Ui.BG);
    root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              i.getSystemWindowInsetLeft(),
              i.getSystemWindowInsetTop(),
              i.getSystemWindowInsetRight(),
              i.getSystemWindowInsetBottom());
          return i;
        });
    LinearLayout header = ui.row();
    header.setPadding(ui.dp(10), ui.dp(12), ui.dp(18), ui.dp(8));
    header.addView(ui.iconButton("arrow-left", "返回", back));
    header.addView(ui.text(title, 20, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(ui.tag("本机运行"));
    root.addView(header);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = ui.col();
    body.setPadding(ui.dp(22), ui.dp(12), ui.dp(22), ui.dp(28));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (subtitle != null) {
      body.addView(ui.body(subtitle));
      ui.space(body, 18);
    }
    setContentView(root);
    root.requestApplyInsets();
  }

  private void library() {
    page("重构已有应用", "原应用完成业务，你决定怎样使用。按功能检测兼容，支持随时回到原界面。", this::finish);
    LinearLayout hero = ui.card();
    hero.setBackground(ui.shape(Ui.GREEN, 24, false));
    hero.addView(ui.eyebrow("A NEW WAY IN", Ui.LIME));
    ui.space(hero, 12);
    hero.addView(ui.text("熟悉的应用，\n自己的界面。", 27, Color.WHITE, true));
    ui.space(hero, 14);
    hero.addView(ui.text("选择应用 → 选取功能 → 设计界面 → 本机试用", 12, Ui.LIME, false));
    ui.space(hero, 18);
    hero.addView(ui.action("选择手机上的应用", "plus", false, this::chooseTarget));
    body.addView(hero);
    LinearLayout permission = ui.card();
    permission.addView(
        ui.actionRow(
            "shield-check",
            "操作授权",
            RebuildAccessibilityService.connected == null
                ? "尚未开启 · 只在你启动会话时读取所选应用"
                : "已开启 · 可随时在系统设置关闭",
            this::permission));
    body.addView(permission);
    body.addView(ui.text("我的重构应用", 18, Ui.INK, true));
    ui.space(body, 12);
    List<RebuildStore.Entry> entries = store.all();
    if (entries.isEmpty())
      body.addView(ui.empty("layout-grid", "先选一个熟悉的应用", "例如音乐、阅读或待办。首版通过选取页面内容建立适配，不保证所有应用或功能兼容。"));
    for (RebuildStore.Entry e : entries) {
      LinearLayout card = ui.card();
      card.addView(
          ui.actionRow(
              "layout-grid",
              e.spec.name(),
              e.spec.widgets().length() + " 个组件 · " + e.verified.size() + " 个动作已本机确认",
              () -> {
                selected = e.id;
                detail();
              }));
      body.addView(card);
    }
  }

  private void permission() {
    new AlertDialog.Builder(this)
        .setTitle("允许 Living 操作所选应用")
        .setMessage(
            "开启无障碍服务后，在你主动启动的重构会话中，Living 可读取所选应用已加载页面的文字和控件，并按你点击的固定流程执行点击、输入或滚动。\n\n"
                + "页面、账号内容和本次输入留在本机，不默认传给 AI。登录、支付及受保护页面请使用原应用。可随时停止会话，或在系统设置关闭服务。")
        .setNegativeButton("暂不开启", null)
        .setPositiveButton(
            "前往系统设置",
            (d, w) ->
                guard(() -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))))
        .show();
  }

  private void chooseTarget() {
    page("选择原应用", "仅展示系统允许发现且带启动入口的应用。列出不代表所有功能兼容。", this::library);
    EditText search = ui.input("搜索应用名称", false);
    body.addView(search);
    ui.space(body, 16);
    LinearLayout list = ui.col();
    body.addView(list);
    List<RebuildTarget> targets = RebuildTarget.installed(this);
    Runnable refresh =
        () -> {
          list.removeAllViews();
          String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
          for (RebuildTarget target : targets)
            if (target.label.toLowerCase(Locale.ROOT).contains(query)) {
              LinearLayout card = ui.card();
              card.addView(
                  ui.actionRow(
                      "layout-grid",
                      target.label,
                      "待检测 · 版本 " + target.version,
                      () ->
                          guard(
                              () -> {
                                selected =
                                    store.create(
                                        RebuildSpec.create(
                                            "我的" + target.label,
                                            target.pkg,
                                            target.version,
                                            target.certificate));
                                detail();
                              })));
              list.addView(card);
            }
          if (list.getChildCount() == 0) list.addView(ui.body("没有找到可选择的应用。请检查原应用是否已安装并有启动入口。"));
        };
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            refresh.run();
          }

          public void afterTextChanged(Editable e) {}
        });
    refresh.run();
  }

  private void detail() {
    RebuildStore.Entry e = store.get(selected);
    page(
        e.spec.name(),
        "重构已有应用 · 界面 v" + e.revision,
        () -> {
          selected = null;
          library();
        });
    boolean ready = true;
    try {
      RebuildTarget t = RebuildTarget.get(this, e.spec.pkg());
      e.spec.assertTarget(t.pkg, t.version, t.certificate);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      ready = false;
      LinearLayout notice = ui.card();
      notice.addView(ui.text("适配需要重新检查", 17, Ui.INK, true));
      notice.addView(ui.body(ex.getMessage()));
      notice.addView(
          ui.button(
              "接受当前版本并清除试用标记",
              false,
              () ->
                  guard(
                      () -> {
                        RebuildTarget t = RebuildTarget.get(this, e.spec.pkg());
                        if (!t.certificate.equals(e.spec.certificate()))
                          throw new IllegalStateException("签名已变化，请重新创建关联");
                        store.save(e.id, e.revision, e.spec.with("version", t.version));
                        detail();
                      })));
      body.addView(notice);
    }
    LinearLayout source = ui.card();
    source.addView(ui.eyebrow("CONNECTED EXPERIENCE", Ui.GREEN));
    ui.space(source, 8);
    String sourceName;
    try {
      sourceName = RebuildTarget.get(this, e.spec.pkg()).label;
    } catch (Exception ignored) {
      sourceName = "原应用不可用";
    }
    source.addView(ui.text(sourceName + "  →  " + e.spec.name(), 19, Ui.INK, true));
    ui.space(source, 8);
    source.addView(ui.body("原应用保留账号和业务数据。这里只保存界面、控件定位和固定操作步骤。"));
    source.addView(ui.button("启动我的界面", true, () -> startSession(false, "")));
    body.addView(source);
    Button capture = ui.action("到原应用选取信息 / 动作", "plus", false, () -> startSession(true, ""));
    capture.setEnabled(ready);
    body.addView(capture);
    ui.space(body, 14);
    LinearLayout design = ui.card();
    design.addView(ui.actionRow("sliders-horizontal", "界面设计", "名称、深浅色、留白和组件顺序", this::appearance));
    design.addView(
        ui.actionRow("wand-sparkles", "用自然语言调整界面", "可选 API · 仅发送你的描述和匿名组件类型", this::aiLayout));
    design.addView(ui.actionRow("history", "版本历史", "恢复只改变界面，不改动原应用数据", this::versions));
    body.addView(design);
    body.addView(ui.text("页面组件", 18, Ui.INK, true));
    ui.space(body, 8);
    if (e.spec.widgets().length() == 0)
      body.addView(ui.empty("plus", "从原页面选取第一个组件", "信息会跟随当前页面更新；动作需要本机试用，不能自动推断为兼容。"));
    JSONArray widgets = e.spec.widgets();
    for (int i = 0; i < widgets.length(); i++) {
      JSONObject widget = widgets.optJSONObject(i);
      String id = widget.optString("id");
      final int at = i;
      LinearLayout card = ui.card();
      card.addView(ui.text(widget.optString("label"), 17, Ui.INK, true));
      ui.space(card, 6);
      String kind = widget.optString("kind");
      card.addView(
          ui.body(
              kind.equals("read")
                  ? "实时信息 · 仅当前页面匹配时显示"
                  : kind.equals("list")
                      ? (widget.optBoolean("interactive") ? "动态列表 · 可点击 · 按项目重新定位" : "动态列表 · 只读")
                      : (e.verified.contains(id) ? "本机试用已确认" : "待试用确认")
                          + " · "
                          + widget.optJSONArray("steps").length()
                          + " 步"));
      ui.space(card, 10);
      LinearLayout controls = ui.row();
      ui.weighted(controls, ui.chip("编辑", () -> widgetEditor(id)));
      ui.weighted(controls, ui.chip("上移", () -> move(e, at, -1)));
      ui.weighted(controls, ui.chip("下移", () -> move(e, at, 1)));
      card.addView(controls);
      body.addView(card);
    }
    body.addView(
        ui.button(
            "停止当前会话",
            false,
            () -> {
              if (RebuildAccessibilityService.connected != null)
                RebuildAccessibilityService.connected.stop();
              Toast.makeText(this, "会话已停止", Toast.LENGTH_SHORT).show();
            }));
    body.addView(
        ui.button(
            "撤销此应用的操作授权",
            false,
            () -> {
              if (RebuildAccessibilityService.connected != null)
                RebuildAccessibilityService.connected.stop();
              getSharedPreferences("rebuild-consent", MODE_PRIVATE)
                  .edit()
                  .remove(e.spec.pkg())
                  .apply();
              Toast.makeText(this, "已撤销，下次启动需重新同意", Toast.LENGTH_SHORT).show();
            }));
    body.addView(
        ui.button(
            "移除此重构界面",
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("移除自定义界面？")
                    .setMessage("仅移除 Living 保存的界面及其历史，不操作原应用或其数据。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton(
                        "移除",
                        (d, w) -> {
                          if (RebuildAccessibilityService.connected != null)
                            RebuildAccessibilityService.connected.stop();
                          store.delete(e.id);
                          selected = null;
                          library();
                        })
                    .show()));
  }

  private void startSession(boolean pick, String append) {
    guard(
        () -> {
          if (RebuildAccessibilityService.connected == null) {
            permission();
            return;
          }
          RebuildStore.Entry e = store.get(selected);
          SharedPreferences consent = getSharedPreferences("rebuild-consent", MODE_PRIVATE);
          if (!consent.getBoolean(e.spec.pkg(), false)) {
            new AlertDialog.Builder(this)
                .setTitle("授权这一个原应用")
                .setMessage(
                    "Living"
                        + " 将在你主动启动的会话中，读取此应用的页面文字与控件，并按你点击的固定流程操作。信息留在本机；无页面后台监视、截图上传或自动支付。可随时退出或撤销授权。")
                .setNegativeButton("取消", null)
                .setPositiveButton(
                    "同意并继续",
                    (d, w) -> {
                      consent.edit().putBoolean(e.spec.pkg(), true).apply();
                      startSession(pick, append);
                    })
                .show();
            return;
          }
          RebuildAccessibilityService.connected.begin(e.id, pick, append);
        });
  }

  private void appearance() {
    RebuildStore.Entry e = store.get(selected);
    LinearLayout box = ui.col();
    box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);
    EditText name = ui.input("界面名称", false);
    name.setText(e.spec.name());
    box.addView(name);
    Switch dark = ui.toggle("深色界面", e.spec.dark()), compact = ui.toggle("紧凑布局", e.spec.compact());
    box.addView(dark);
    box.addView(compact);
    new AlertDialog.Builder(this)
        .setTitle("界面设计")
        .setView(box)
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "保存新版本",
            (d, w) ->
                guard(
                    () -> {
                      store.save(
                          e.id,
                          e.revision,
                          e.spec
                              .with("name", name.getText().toString())
                              .with("dark", dark.isChecked())
                              .with("compact", compact.isChecked()));
                      detail();
                    }))
        .show();
  }

  private void move(RebuildStore.Entry e, int index, int offset) {
    guard(
        () -> {
          JSONArray list = e.spec.widgets();
          int dest = index + offset;
          if (dest < 0 || dest >= list.length()) return;
          JSONObject a = list.optJSONObject(index), b = list.optJSONObject(dest);
          try {
            list.put(index, b);
            list.put(dest, a);
          } catch (JSONException ex) {
            throw new IllegalArgumentException(ex);
          }
          store.save(e.id, e.revision, e.spec.with("widgets", list));
          detail();
        });
  }

  private void widgetEditor(String id) {
    RebuildStore.Entry e = store.get(selected);
    JSONObject widget = e.spec.widget(id);
    page(
        "编辑组件",
        widget.optString("kind").equals("read")
            ? "信息只来自匹配的原页面，未匹配时显示不可用。"
            : "固定步骤按顺序执行；每一步失败即停止。输入值仅用于当次，不保存。",
        this::detail);
    EditText label = ui.input("组件名称", false);
    label.setText(widget.optString("label"));
    body.addView(label);
    ui.space(body, 12);
    body.addView(
        ui.button(
            "保存名称",
            true,
            () ->
                guard(
                    () -> {
                      JSONArray ws = e.spec.widgets();
                      for (int i = 0; i < ws.length(); i++)
                        if (ws.optJSONObject(i).optString("id").equals(id))
                          J.put(ws.optJSONObject(i), "label", label.getText().toString());
                      store.save(e.id, e.revision, e.spec.with("widgets", ws));
                      detail();
                    })));
    if (widget.has("steps")) {
      JSONArray steps = widget.optJSONArray("steps");
      for (int i = 0; i < steps.length(); i++) {
        JSONObject step = steps.optJSONObject(i);
        LinearLayout card = ui.card();
        String op = step.optString("op");
        card.addView(
            ui.text(
                (i + 1)
                    + " · "
                    + (op.equals("input") ? "输入本次内容" : op.equals("click") ? "点击控件" : "向下滚动"),
                15,
                Ui.INK,
                true));
        JSONObject selector = step.optJSONObject("selector");
        card.addView(
            ui.body(
                selector.optString("text").isEmpty()
                    ? selector.optString("viewId")
                    : selector.optString("text")));
        body.addView(card);
      }
      body.addView(ui.button("示范：追加下一步", false, () -> startSession(true, id)));
      if (steps.length() > 1)
        body.addView(
            ui.button(
                "移除最后一步",
                false,
                () ->
                    guard(
                        () -> {
                          JSONArray ws = e.spec.widgets();
                          for (int i = 0; i < ws.length(); i++) {
                            JSONObject w = ws.optJSONObject(i);
                            if (!w.optString("id").equals(id)) continue;
                            JSONArray st = w.optJSONArray("steps");
                            st.remove(st.length() - 1);
                            boolean input = false;
                            for (int k = 0; k < st.length(); k++)
                              if (st.optJSONObject(k).optString("op").equals("input")) input = true;
                            J.put(w, "kind", input ? "input" : "action");
                          }
                          store.save(e.id, e.revision, e.spec.with("widgets", ws));
                          widgetEditor(id);
                        })));
    }
    body.addView(
        ui.button(
            "移除此组件",
            false,
            () ->
                guard(
                    () -> {
                      JSONArray ws = e.spec.widgets(), next = J.arr();
                      for (int i = 0; i < ws.length(); i++)
                        if (!ws.optJSONObject(i).optString("id").equals(id))
                          next.put(ws.optJSONObject(i));
                      store.save(e.id, e.revision, e.spec.with("widgets", next));
                      detail();
                    })));
  }

  private void versions() {
    RebuildStore.Entry e = store.get(selected);
    page("界面版本", "恢复会生成新版本并清除试用标记。原应用及业务数据始终独立。", this::detail);
    for (long revision : store.history(e.id))
      body.addView(
          ui.button(
              "界面 v" + revision + (revision == e.revision ? " · 当前" : " · 恢复为新版本"),
              false,
              () -> {
                if (revision != e.revision)
                  guard(
                      () -> {
                        store.restore(e.id, e.revision, revision);
                        detail();
                      });
              }));
  }

  private void aiLayout() {
    RebuildStore.Entry baseline = store.get(selected);
    if (baseline.spec.widgets().length() == 0) {
      Toast.makeText(this, "先从原应用选取组件，再让 AI 调整界面", Toast.LENGTH_LONG).show();
      return;
    }
    LinearLayout box = ui.col();
    box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);
    box.addView(ui.body("只发送你的描述与匿名组件编号 / 类型。不会发送原页面、控件文字、定位信息、包名或账号内容。你输入的描述会发送至设置中的模型 API。"));
    EditText prompt = ui.input("例如：深色、留白更多，把第 2 个组件放到最上面", true);
    box.addView(prompt);
    new AlertDialog.Builder(this)
        .setTitle("描述你想要的界面")
        .setView(box)
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "发送并生成预览", (d, w) -> guard(() -> generateLayout(baseline, prompt.getText().toString())))
        .show();
  }

  private void generateLayout(RebuildStore.Entry baseline, String requirement) {
    RebuildLayout.context(baseline.spec, requirement);
    final int token = ++requestToken;
    SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
    try {
      client = new ResponsesClient(prefs.getString("base", ""), new SecretVault(this).read());
    } catch (Exception e) {
      throw new IllegalArgumentException("请先在主页面设置中配置模型 API 和密钥");
    }
    if (request != null) request.cancel(true);
    final ResponsesClient active = client;
    AlertDialog progress =
        new AlertDialog.Builder(this)
            .setTitle("正在设计界面")
            .setMessage("原应用和当前界面保持可用。生成后需要你预览采用。")
            .setNegativeButton(
                "取消",
                (d, w) -> {
                  requestToken++;
                  active.cancel();
                  if (request != null) request.cancel(true);
                })
            .create();
    progress.setCancelable(false);
    generationDialog = progress;
    progress.show();
    request =
        executor.submit(
            () -> {
              try {
                RebuildSpec result =
                    active.rebuildLayout(
                        prefs.getString("model", "gpt-6.1-sol"), baseline.spec, requirement);
                runOnUiThread(
                    () -> {
                      if (isFinishing() || isDestroyed() || token != requestToken) return;
                      progress.dismiss();
                      StringBuilder summary =
                          new StringBuilder(
                              result.name()
                                  + "\n"
                                  + (result.dark() ? "深色" : "浅色")
                                  + " · "
                                  + (result.compact() ? "紧凑" : "舒展")
                                  + "\n\n");
                      JSONArray ws = result.widgets();
                      for (int i = 0; i < ws.length(); i++)
                        summary
                            .append(i + 1)
                            .append(". ")
                            .append(ws.optJSONObject(i).optString("label"))
                            .append('\n');
                      new AlertDialog.Builder(this)
                          .setTitle("预览新界面")
                          .setMessage(summary.toString())
                          .setNegativeButton("保留原样", null)
                          .setPositiveButton(
                              "采用为新版本",
                              (d, w) ->
                                  guard(
                                      () -> {
                                        store.save(baseline.id, baseline.revision, result);
                                        detail();
                                      }))
                          .show();
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () -> {
                      if (isFinishing() || isDestroyed() || token != requestToken) return;
                      progress.dismiss();
                      new AlertDialog.Builder(this)
                          .setTitle("没有改变当前界面")
                          .setMessage("生成失败或返回了超出布局边界的内容。请检查配置后重试。")
                          .setPositiveButton("知道了", null)
                          .show();
                    });
              }
            });
  }
}

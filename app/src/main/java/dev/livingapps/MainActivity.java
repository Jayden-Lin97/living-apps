package dev.livingapps;

import static dev.livingapps.core.Messages.tr;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.json.*;

public final class MainActivity extends LocalizedActivity {
  private Ui ui;
  private Store store;
  private SecretVault vault;
  private SharedPreferences prefs;
  private LinearLayout root, body, toolbarActions;
  private String currentApp, screen = "overview";
  private String destination = "home";
  private ScrollView pageScroll;
  private int editorScrollY;
  private final Map<String, String> searchQueries = new HashMap<>();
  private String actionUndoApp, actionUndoId;
  private JSONObject actionUndoData;
  private Runnable backAction;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private ResponsesClient client;
  private Future<?> request;
  private AlertDialog loading;
  private int requestToken;
  private static final int EXPORT = 71, IMPORT = 72, EDIT = 73, REBUILD = 74;
  private static final String DEFAULT_BASE = "";

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    ui = new Ui(this);
    store = ((LivingApplication) getApplication()).store;
    vault = new SecretVault(this);
    prefs = getSharedPreferences("settings", MODE_PRIVATE);
    if (!prefs.getBoolean("initialized", false)) {
      for (AppSpec spec : Templates.localized()) store.create(spec, "template");
      prefs.edit().putBoolean("initialized", true).apply();
    }
    if (state != null) {
      currentApp = state.getString("app");
      screen = state.getString("screen", "overview");
      destination = state.getString("destination", "home");
      editorScrollY = state.getInt("editorScroll");
      Bundle queries = state.getBundle("queries");
      if (queries != null)
        for (String key : queries.keySet()) searchQueries.put(key, queries.getString(key, ""));
    }
    if (currentApp != null) {
      try {
        showApp(currentApp, screen, true);
      } catch (Exception e) {
        home();
      }
    } else if (destination.equals("settings")) settings();
    else if (destination.equals("insights")) insights();
    else home();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("app", currentApp);
    out.putString("screen", screen);
    out.putString("destination", destination);
    out.putInt("editorScroll", editorScrollY);
    Bundle queries = new Bundle();
    for (String key : searchQueries.keySet()) queries.putString(key, searchQueries.get(key));
    out.putBundle("queries", queries);
  }

  @Override
  protected void onDestroy() {
    requestToken++;
    if (client != null) client.cancel();
    if (request != null) request.cancel(true);
    if (loading != null) loading.dismiss();
    executor.shutdownNow();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (backAction != null) backAction.run();
    else super.onBackPressed();
  }

  private void toast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private void fail(Exception e) {
    String message = e.getMessage();
    if (message == null) message = tr("操作没有完成，请重试");
    message = message.replaceAll("sk-[A-Za-z0-9_-]{8,}", tr("[已隐藏]"));
    new AlertDialog.Builder(this)
        .setTitle(tr("暂时没有完成"))
        .setMessage(message.length() > 800 ? message.substring(0, 800) : message)
        .setPositiveButton(tr("知道了"), null)
        .show();
  }

  private LinearLayout page(String title, String subtitle, Runnable back, int nav) {
    if (back == null) destination = nav == 2 ? "settings" : nav == 1 ? "insights" : "home";
    backAction = back;
    root = ui.col();
    root.setBackgroundColor(Ui.BG);
    root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    LinearLayout header = ui.row();
    header.setPadding(ui.dp(back == null ? 22 : 12), ui.dp(12), ui.dp(14), ui.dp(10));
    if (back != null) header.addView(ui.iconButton("arrow-left", tr("返回"), back));
    else {
      FrameLayout logo = ui.badge("sprout", Ui.GREEN, 36);
      logo.setBackground(ui.shape(Ui.GREEN, 12, false));
      ((ImageView) logo.getChildAt(0))
          .setImageTintList(android.content.res.ColorStateList.valueOf(Ui.LIME));
      header.addView(logo);
      ui.gap(header, 10);
    }
    LinearLayout labels = ui.col();
    TextView heading = ui.text(title, back == null ? 21 : 18, Ui.INK, true);
    heading.setMaxLines(1);
    heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
    labels.addView(heading);
    if (back == null) {
      ui.space(labels, 4);
      labels.addView(ui.eyebrow("LIVING APPS", Ui.MUTED));
    }
    header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    toolbarActions = ui.row();
    header.addView(toolbarActions, new LinearLayout.LayoutParams(-2, -2));
    if (nav >= 0) toolbarActions.addView(ui.tag(tr("本机空间")));
    root.addView(header);
    ScrollView scroll = new ScrollView(this);
    pageScroll = scroll;
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    body = ui.col();
    body.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(24));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (nav >= 0) {
      LinearLayout holder = ui.col();
      holder.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12));
      LinearLayout tabs = ui.row();
      tabs.setBackground(ui.shape(Color.WHITE, 24, true));
      tabs.setPadding(ui.dp(5), ui.dp(2), ui.dp(5), ui.dp(2));
      String[] names = {getString(R.string.nav_apps), getString(R.string.nav_insights), tr("设置")},
          icons = {"layout-grid", "sprout", "settings-2"};
      for (int i = 0; i < 3; i++) {
        final int index = i;
        ui.weighted(
            tabs,
            ui.navigationItem(
                icons[i],
                names[i],
                i == nav,
                () -> {
                  currentApp = null;
                  if (index == 0) home();
                  else if (index == 1) insights();
                  else settings();
                }));
      }
      holder.addView(tabs);
      root.addView(holder);
    }
    setContentView(root);
    root.requestApplyInsets();
    return body;
  }

  private void footer(View action) {
    LinearLayout holder = ui.col();
    holder.setBackgroundColor(Color.WHITE);
    ui.divider(holder);
    LinearLayout content = ui.col();
    content.setPadding(ui.dp(22), ui.dp(12), ui.dp(22), ui.dp(16));
    content.addView(action);
    holder.addView(content);
    root.addView(holder);
  }

  private void steps(LinearLayout parent, int selected) {
    LinearLayout steps = ui.row();
    String[] names = {tr("描述想法"), tr("预览应用"), tr("开始使用")};
    for (int i = 0; i < 3; i++) {
      LinearLayout step = ui.row();
      TextView dot =
          ui.text(String.valueOf(i + 1), 11, i == selected ? Color.WHITE : Ui.MUTED, true);
      dot.setGravity(Gravity.CENTER);
      dot.setBackground(ui.shape(i == selected ? Ui.GREEN : Ui.SOFT, 12, false));
      step.addView(dot, new LinearLayout.LayoutParams(ui.dp(23), ui.dp(23)));
      ui.gap(step, 6);
      step.addView(ui.text(names[i], 11, i == selected ? Ui.GREEN : Ui.MUTED, i == selected));
      steps.addView(step, new LinearLayout.LayoutParams(0, -2, 1));
    }
    parent.addView(steps);
    ui.space(parent, 26);
  }

  private void section(LinearLayout parent, String title, String hint) {
    ui.space(parent, 12);
    parent.addView(ui.text(title, 18, Ui.INK, true));
    if (hint != null) {
      ui.space(parent, 4);
      parent.addView(ui.body(hint));
    }
    ui.space(parent, 12);
  }

  private String number(double n) {
    return new DecimalFormat("#,##0.##").format(n);
  }

  private String when(long millis) {
    return DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
  }

  private String screenName(String value) {
    return value.equals("records") ? tr("记录") : value.equals("stats") ? tr("统计") : tr("概览");
  }

  private String versionName(String source) {
    return source.equals("auto")
        ? tr("自动小调整")
        : source.equals("restore")
            ? tr("版本恢复")
            : source.equals("template")
                ? tr("本地模板")
                : source.equals("local")
                    ? tr("离线编辑")
                    : source.equals("rule") ? tr("本地优化") : tr("AI / 导入");
  }

  private void home() {
    currentApp = null;
    List<Store.App> apps = store.apps();
    List<RebuildStore.Entry> rebuiltApps;
    try (RebuildStore rebuilds = new RebuildStore(this)) {
      rebuiltApps = rebuilds.all();
    }
    boolean compactDisplay =
        getResources().getConfiguration().screenWidthDp < 360
            || getResources().getConfiguration().fontScale > 1.2f;
    LinearLayout b = page(tr("生长"), null, null, 0);
    ui.space(b, 10);
    LinearLayout heading = ui.row();
    heading.addView(ui.text(tr("我的应用"), 29, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(
        ui.tag(String.format(Locale.ROOT, "%02d", apps.size() + rebuiltApps.size()) + tr(" 个应用")));
    b.addView(heading);
    ui.space(b, 8);
    b.addView(ui.body(tr("创建属于自己的应用，让它持续适应你的需求。")));
    ui.space(b, 22);
    LinearLayout hero = ui.card();
    hero.setBackground(ui.shape(Ui.GREEN, 25, false));
    hero.setPadding(ui.dp(20), ui.dp(18), ui.dp(16), ui.dp(16));
    LinearLayout heroTop = ui.row();
    LinearLayout words = ui.col();
    words.addView(ui.eyebrow("MAKE IT YOURS", Ui.LIME));
    ui.space(words, 10);
    words.addView(
        ui.text(
            compactDisplay ? tr("一个想法，\n一个应用。") : tr("一个想法，\n一个新应用。"),
            compactDisplay ? 22 : 24,
            Color.WHITE,
            true));
    heroTop.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    heroTop.addView(
        ui.artwork(false),
        new LinearLayout.LayoutParams(
            ui.dp(compactDisplay ? 76 : 108), ui.dp(compactDisplay ? 92 : 108)));
    hero.addView(heroTop);
    ui.space(hero, 16);
    Button create = ui.action(tr("创造一个应用"), "plus", false, () -> editor(null, null, false));
    create.setBackground(ui.shape(Ui.LIME, 14, false));
    hero.addView(create);
    ui.space(hero, 10);
    hero.addView(ui.action(tr("重构已有应用"), "layout-grid", false, () -> openRebuild(null)));
    b.addView(hero);
    LinearLayout status = ui.row();
    status.setPadding(ui.dp(2), ui.dp(2), 0, ui.dp(18));
    status.addView(ui.icon("shield-check", Ui.MUTED, 14));
    ui.gap(status, 6);
    status.addView(ui.text(tr("你的记录，只留在这台手机"), 11, Ui.MUTED, false));
    b.addView(status);
    for (RebuildStore.Entry rebuilt : rebuiltApps) {
      LinearLayout rebuiltCard = ui.card();
      rebuiltCard.addView(
          ui.actionRow(
              "layout-grid",
              rebuilt.spec.name(),
              tr("重构已有应用 · ") + rebuilt.spec.widgets().length() + tr(" 个组件 · 按功能验证"),
              () -> openRebuild(rebuilt.id)));
      b.addView(rebuiltCard);
    }
    if (apps.isEmpty())
      b.addView(ui.empty("layout-grid", tr("从第一个应用开始"), tr("从一个想法开始，或选择下方的离线模板。")));
    int columns =
        getResources().getConfiguration().screenWidthDp < 360
                || getResources().getConfiguration().fontScale > 1.2f
            ? 1
            : 2;
    for (int i = 0; i < apps.size() + 1; i += columns) {
      LinearLayout grid = ui.row();
      grid.setGravity(Gravity.TOP);
      for (int j = 0; j < columns; j++) {
        int at = i + j;
        LinearLayout card;
        if (at < apps.size()) card = appTile(apps.get(at));
        else if (at == apps.size()) {
          card = ui.card();
          card.setBackground(ui.shape(Ui.SOFT, 22, false));
          card.addView(ui.badge("plus", Ui.GREEN, 40));
          ui.space(card, 18);
          card.addView(ui.text(tr("下一个应用"), 15, Ui.GREEN, true));
          ui.space(card, 7);
          card.addView(ui.text(tr("让生活再顺手一点"), 11, Ui.MUTED, false));
          ui.touch(card, 22, () -> editor(null, null, false));
        } else {
          ui.weighted(grid, new Space(this));
          continue;
        }
        LinearLayout.LayoutParams tile =
            new LinearLayout.LayoutParams(0, ui.dp(columns == 1 ? 188 : 174), 1);
        if (columns > 1) {
          if (j == 0) tile.rightMargin = ui.dp(6);
          else tile.leftMargin = ui.dp(6);
        }
        grid.addView(card, tile);
      }
      b.addView(grid);
      ui.space(b, 12);
    }
    List<Store.Proposal> proposals = store.proposals(null);
    if (!proposals.isEmpty()) {
      LinearLayout nudge = ui.card();
      nudge.addView(
          ui.actionRow(
              "sprout", proposals.size() + tr(" 个应用改进建议"), tr("来自你的使用习惯，点开看看"), this::insights));
      b.addView(nudge);
    }
    section(b, tr("从灵感开始"), tr("这三个模板，断网也能直接用。"));
    LinearLayout templates = ui.card();
    List<AppSpec> all = Templates.all();
    for (int i = 0; i < all.size(); i++) {
      AppSpec template = all.get(i);
      templates.addView(
          ui.actionRow(
              ui.appIcon(template),
              template.name(),
              template.description(),
              () -> showApp(store.create(template, "template"), "overview", true)));
      if (i < all.size() - 1) ui.divider(templates);
    }
    b.addView(templates);
  }

  private LinearLayout appTile(Store.App app) {
    LinearLayout card = ui.card();
    card.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(15));
    int color = Color.parseColor(app.spec.accent());
    LinearLayout top = ui.row();
    top.addView(ui.badge(ui.appIcon(app.spec), color, 40));
    Space flex = new Space(this);
    top.addView(flex, new LinearLayout.LayoutParams(0, 1, 1));
    top.addView(ui.icon(app.frozen ? "lock" : "arrow-up-right", Ui.MUTED, 16));
    card.addView(top);
    ui.space(card, 15);
    TextView name = ui.text(app.spec.name(), 16, Ui.INK, true);
    name.setMaxLines(1);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    card.addView(name);
    ui.space(card, 6);
    TextView description = ui.text(app.spec.description(), 11, Ui.MUTED, false);
    description.setMaxLines(2);
    description.setEllipsize(android.text.TextUtils.TruncateAt.END);
    card.addView(description, new LinearLayout.LayoutParams(-1, 0, 1));
    card.addView(ui.text(store.records(app.id).size() + tr(" 条记录"), 11, color, true));
    ui.touch(card, 22, () -> showApp(app.id, app.spec.startScreen(), true));
    return card;
  }

  private void openRebuild(String id) {
    startActivityForResult(new Intent(this, RebuildActivity.class).putExtra("shell", id), REBUILD);
  }

  private void showApp(String id, String target, boolean logOpen) {
    Store.App app = store.app(id);
    currentApp = id;
    screen = target;
    if (logOpen) store.event(id, app.versionId, "open", 0);
    LinearLayout b = page(app.spec.name(), null, this::home, -1);
    toolbarActions.addView(ui.iconButton("pen-line", tr("修改应用"), () -> editor(id, null, false)));
    toolbarActions.addView(ui.iconButton("history", tr("版本"), () -> history(id)));
    toolbarActions.addView(ui.iconButton("ellipsis", tr("更多"), () -> appMenu(id)));
    LinearLayout identity = ui.row();
    identity.setPadding(0, ui.dp(8), 0, ui.dp(22));
    identity.addView(ui.badge(ui.appIcon(app.spec), Color.parseColor(app.spec.accent()), 48));
    ui.gap(identity, 12);
    LinearLayout description = ui.col();
    description.addView(ui.body(app.spec.description()));
    ui.space(description, 6);
    description.addView(ui.text(app.frozen ? tr("版本已固定") : tr("个人应用 · 本机保存"), 10, Ui.GREEN, true));
    identity.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
    b.addView(identity);
    if (store.hasDraft(id)) {
      b.addView(ui.action(tr("继续未完成的编辑草稿"), "pen-line", false, () -> editor(id, null, false)));
      ui.space(b, 12);
    }
    if (id.equals(actionUndoApp) && actionUndoId != null) {
      LinearLayout notice = ui.card();
      notice.addView(ui.body(tr("刚刚通过固定动作新增了 1 条记录")));
      notice.addView(
          ui.button(
              tr("撤销刚才这条记录"),
              false,
              () -> {
                try {
                  store.undoAction(id, actionUndoId, actionUndoData);
                  actionUndoId = null;
                  showApp(id, screen, false);
                } catch (Exception e) {
                  fail(e);
                }
              }));
      b.addView(notice);
    }
    LinearLayout tabs = ui.row();
    tabs.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
    tabs.setBackground(ui.shape(Ui.SOFT, 15, false));
    for (String name : new String[] {"overview", "records", "stats"}) {
      boolean selected = name.equals(target);
      Button tab =
          ui.button(
              screenName(name),
              false,
              () -> {
                if (name.equals("stats")) store.event(id, app.versionId, "stats", 0);
                showApp(id, name, false);
              });
      tab.setMinHeight(ui.dp(40));
      tab.setMinimumHeight(ui.dp(40));
      tab.setPadding(ui.dp(6), ui.dp(7), ui.dp(6), ui.dp(7));
      tab.setTextSize(13);
      tab.setTextColor(selected ? Ui.GREEN : Ui.MUTED);
      tab.setBackground(ui.shape(selected ? Color.WHITE : Color.TRANSPARENT, 11, false));
      tab.setSelected(selected);
      ui.weighted(tabs, tab);
    }
    b.addView(tabs);
    ui.space(b, 20);
    for (ActionSpec action : app.spec.actions()) {
      b.addView(
          ui.action(
              action.label,
              "plus",
              false,
              () -> {
                try {
                  actionUndoId = store.runAction(id, app.versionId, action.id);
                  actionUndoApp = id;
                  for (Store.Entry row : store.records(id))
                    if (row.id.equals(actionUndoId)) actionUndoData = J.parse(row.data.toString());
                  store.event(id, app.versionId, "save", 0);
                  showApp(id, screen, false);
                } catch (Exception e) {
                  fail(e);
                }
              }));
      ui.space(b, 10);
    }
    if (app.spec.quickAdd()) {
      b.addView(ui.action(tr("快速记一条"), "plus", true, () -> form(id, null)));
      ui.space(b, 14);
    }
    List<Store.Entry> entries = store.records(id);
    if (target.equals("records")) {
      recordsView(b, app, entries);
      return;
    }
    if (target.equals("stats")) {
      statsView(b, app, entries);
      return;
    }
    LinearLayout summary = ui.card();
    LinearLayout caption = ui.row();
    caption.addView(
        ui.text(
            app.spec.metricField().isEmpty()
                ? tr("一点一滴，都是积累")
                : app.spec.field(app.spec.metricField()).label + tr("累计"),
            12,
            Ui.MUTED,
            false),
        new LinearLayout.LayoutParams(0, -2, 1));
    caption.addView(ui.icon("chart-column", Ui.GREEN, 18));
    summary.addView(caption);
    ui.space(summary, 16);
    LinearLayout value = ui.row();
    value.setGravity(Gravity.BOTTOM);
    value.addView(
        ui.text(
            app.spec.metricField().isEmpty()
                ? String.valueOf(entries.size())
                : number(total(app, entries)),
            42,
            Ui.GREEN,
            true));
    ui.gap(value, 7);
    TextView unit =
        ui.text(app.spec.metricField().isEmpty() ? tr("条") : app.spec.unit(), 15, Ui.MUTED, false);
    unit.setPadding(0, 0, 0, ui.dp(7));
    value.addView(unit);
    summary.addView(value);
    ui.space(summary, 12);
    summary.addView(ui.body(tr("已留下 ") + entries.size() + tr(" 条记录")));
    ui.space(summary, 22);
    summary.addView(ui.action(tr("新增记录"), "plus", true, () -> form(id, null)));
    b.addView(summary);
    LinearLayout recent = ui.row();
    recent.setPadding(0, ui.dp(14), 0, ui.dp(14));
    recent.addView(ui.text(tr("最近记录"), 18, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    TextView all = ui.text(tr("查看全部"), 12, Ui.GREEN, true);
    all.setPadding(ui.dp(8), ui.dp(10), 0, ui.dp(10));
    all.setOnClickListener(v -> showApp(id, "records", false));
    recent.addView(all);
    b.addView(recent);
    if (entries.isEmpty())
      b.addView(ui.empty("notebook-pen", tr("第一条记录，从今天开始"), tr("不必很多，记下对你有用的就好。")));
    for (Store.Entry entry : entries.subList(0, Math.min(3, entries.size())))
      entryCard(b, app, entry);
    List<Store.Proposal> proposals = store.proposals(id);
    if (!proposals.isEmpty()) {
      LinearLayout nudge = ui.card();
      nudge.addView(
          ui.actionRow(
              "sprout",
              tr("有 ") + proposals.size() + tr(" 个小改进"),
              tr("来自这台手机上的使用观察"),
              this::insights));
      b.addView(nudge);
    }
  }

  private double total(Store.App app, List<Store.Entry> entries) {
    AppSpec.Field f = app.spec.field(app.spec.metricField());
    if (f == null) return 0;
    double total = 0;
    for (Store.Entry e : entries) {
      Object value = store.effective(f, e.data);
      if (value instanceof Number) total += ((Number) value).doubleValue();
    }
    return total;
  }

  private void entryCard(LinearLayout parent, Store.App app, Store.Entry entry) {
    parent.addView(
        new RuntimeRenderer(this, ui).recordCard(app.spec, entry, () -> form(app.id, entry)));
  }

  private void recordsView(LinearLayout b, Store.App app, List<Store.Entry> entries) {
    b.addView(ui.action(tr("新增记录"), "plus", true, () -> form(app.id, null)));
    ui.space(b, 12);
    EditText search = ui.input(tr("搜索记录…"), false);
    b.addView(search);
    ui.space(b, 12);
    LinearLayout list = ui.col();
    b.addView(list);
    Consumer<String> render =
        query -> {
          list.removeAllViews();
          List<Store.Entry> filtered = new ArrayList<>();
          for (Store.Entry e : entries)
            if (e.data.toString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
              filtered.add(e);
          list.addView(ui.body(tr("共 ") + filtered.size() + tr(" 条")));
          ui.space(list, 8);
          appendEntries(list, app, filtered, 0);
        };
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            searchQueries.put(app.id, s.toString());
            render.accept(s.toString());
          }

          public void afterTextChanged(Editable s) {}
        });
    search.setText(searchQueries.getOrDefault(app.id, ""));
    render.accept(search.getText().toString());
  }

  private void appendEntries(
      LinearLayout list, Store.App app, List<Store.Entry> entries, int offset) {
    for (int i = offset; i < Math.min(offset + 40, entries.size()); i++)
      entryCard(list, app, entries.get(i));
    if (offset + 40 < entries.size()) {
      Button more = ui.button(tr("再显示 40 条"), false, () -> {});
      more.setOnClickListener(
          v -> {
            list.removeView(more);
            appendEntries(list, app, entries, offset + 40);
          });
      list.addView(more);
    }
    if (entries.isEmpty()) list.addView(ui.empty("search", tr("这里还没有记录"), tr("新增一条记录，或换个关键词再试试。")));
  }

  private void statsView(LinearLayout b, Store.App app, List<Store.Entry> entries) {
    new RuntimeRenderer(this, ui).stats(b, app.spec, entries);
  }

  private void form(String id, Store.Entry existing) {
    Store.App app = store.app(id);
    long start = SystemClock.elapsedRealtime();
    LinearLayout fields = ui.col();
    fields.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12));
    Map<String, Supplier<Object>> readers = new LinkedHashMap<>();
    for (AppSpec.Field f : app.spec.fields())
      readers.put(
          f.id,
          renderField(
              fields, f, existing == null ? f.defaultValue : store.effective(f, existing.data)));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(fields);
    AlertDialog.Builder builder =
        new AlertDialog.Builder(this)
            .setTitle(existing == null ? tr("留下新记录") : tr("编辑记录"))
            .setView(scroll)
            .setPositiveButton(tr("保存"), null)
            .setNegativeButton(
                tr("取消"),
                (d, w) ->
                    store.event(
                        id, app.versionId, "cancel", SystemClock.elapsedRealtime() - start));
    if (existing != null)
      builder.setNeutralButton(
          tr("删除"),
          (d, w) ->
              new AlertDialog.Builder(this)
                  .setTitle(tr("删除这条记录？"))
                  .setMessage(tr("应用版本恢复不包含记录删除的撤销。"))
                  .setNegativeButton(tr("保留"), null)
                  .setPositiveButton(
                      tr("删除"),
                      (dialog, which) -> {
                        store.deleteRecord(id, existing.id);
                        showApp(id, screen, false);
                      })
                  .show());
    AlertDialog dialog = builder.create();
    dialog.setOnCancelListener(
        d -> store.event(id, app.versionId, "cancel", SystemClock.elapsedRealtime() - start));
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        JSONObject data = J.obj();
                        for (Map.Entry<String, Supplier<Object>> entry : readers.entrySet())
                          J.put(data, entry.getKey(), entry.getValue().get());
                        List<String> errors = app.spec.validateRecord(data);
                        if (!errors.isEmpty()) {
                          store.event(id, app.versionId, "validation_error", 0);
                          toast(String.join("\n", errors));
                          return;
                        }
                        store.saveRecord(
                            id, app.versionId, existing == null ? null : existing.id, data);
                        store.event(
                            id, app.versionId, "save", SystemClock.elapsedRealtime() - start);
                        dialog.dismiss();
                        showApp(id, screen, false);
                        executor.submit(
                            () ->
                                store.reflect(((LivingApplication) getApplication()).automatic()));
                      } catch (Exception e) {
                        fail(e);
                      }
                    }));
    dialog.show();
    ui.styleDialog(dialog);
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  }

  private Supplier<Object> renderField(LinearLayout parent, AppSpec.Field field, Object value) {
    return new RuntimeRenderer(this, ui).field(parent, field, value);
  }

  private void editor(String id, String fieldId, boolean optimize) {
    if (id != null) {
      editorScrollY = id.equals(currentApp) && pageScroll != null ? pageScroll.getScrollY() : 0;
      Intent intent =
          new Intent(this, EditorActivity.class)
              .putExtra("appId", id)
              .putExtra("optimize", optimize);
      if (fieldId != null) intent.putExtra("fieldId", fieldId);
      startActivityForResult(intent, EDIT);
      return;
    }
    Store.App app = id == null ? null : store.app(id);
    currentApp = id;
    LinearLayout b =
        page(
            id == null ? tr("创造一个应用") : optimize ? tr("改进应用") : tr("修改应用"),
            id == null ? tr("把一个小需求，说给它听。") : tr("已有记录始终独立保存在本机。"),
            id == null ? this::home : () -> showApp(id, screen, false),
            -1);
    steps(b, 0);
    b.addView(
        ui.text(
            id == null ? tr("你想创建怎样的应用？") : optimize ? tr("让每次使用更顺手") : tr("哪些细节，想换个方式？"),
            25,
            Ui.INK,
            true));
    ui.space(b, 10);
    b.addView(ui.body(id == null ? tr("说说要记录什么，以及想怎么看。") : tr("描述想调整的地方，已有记录会保留。")));
    ui.space(b, 24);
    if (fieldId != null) {
      AppSpec.Field field = app.spec.field(fieldId);
      LinearLayout selected = ui.card();
      selected.addView(
          ui.actionRow(
              "pen-line",
              tr("已选中：") + field.label,
              tr("直接改名称和位置 · 离线"),
              () -> localFieldEdit(id, fieldId)));
      b.addView(selected);
    }
    b.addView(ui.text(tr("你的想法"), 13, Ui.INK, true));
    ui.space(b, 10);
    EditText prompt =
        ui.input(
            id == null
                ? tr("例如：给我做一个跑步日记，记录距离、耗时和心情，每周看看自己跑了多远。")
                : optimize ? tr("例如：请依据使用摘要，给出一项能减少操作步骤的改进。") : tr("例如：增加一个可选的心情字段；保留我已有的内容。"),
            true);
    prompt.setFilters(new InputFilter[] {new InputFilter.LengthFilter(2000)});
    prompt.setMinLines(6);
    b.addView(prompt);
    ui.space(b, 16);
    if (id == null) {
      b.addView(ui.text(tr("试试这些灵感"), 11, Ui.MUTED, false));
      ui.space(b, 10);
      HorizontalScrollView suggestions = new HorizontalScrollView(this);
      suggestions.setHorizontalScrollBarEnabled(false);
      LinearLayout chips = ui.row();
      String[] labels = {tr("运动记录"), tr("读书笔记"), tr("存钱计划")};
      String[] ideas = {
        tr("做一个运动记录应用，记录日期、运动类型、时长和感受，可以看看累计运动时长。"),
        tr("做一个读书笔记，记录书名、日期、页数和想法，统计读过的页数。"),
        tr("做一个存钱应用，记录日期、存入金额、目标和备注，看看已经积累了多少。")
      };
      for (int i = 0; i < labels.length; i++) {
        final int at = i;
        chips.addView(
            ui.chip(
                labels[i],
                () -> {
                  prompt.setText(ideas[at]);
                  prompt.setSelection(prompt.length());
                }));
        ui.gap(chips, 8);
      }
      suggestions.addView(chips);
      b.addView(suggestions);
      ui.space(b, 20);
    }
    LinearLayout reassurance = ui.row();
    reassurance.addView(ui.icon("shield-check", Ui.GREEN, 16));
    ui.gap(reassurance, 7);
    reassurance.addView(ui.text(tr("先预览，满意后再采用"), 11, Ui.MUTED, false));
    b.addView(reassurance);
    ui.space(b, 12);
    footer(
        ui.action(
            optimize ? tr("分析并预览改进") : tr("生成并预览"),
            "wand-sparkles",
            true,
            () -> {
              String description = prompt.getText().toString().trim();
              if (description.isEmpty()) {
                toast(tr("先写下你想要的改变"));
                return;
              }
              try {
                String instructions = asset("generation-instructions.txt");
                String input = tr("用户需求：") + description;
                if (app != null) input += tr("\n当前应用定义（保留旧字段ID和类型）：\n") + app.spec;
                if (fieldId != null) input += tr("\n用户明确选中字段ID：") + fieldId + tr("，优先只修改这个对象。");
                if (optimize)
                  input +=
                      tr("\n最近7天本版本真实聚合使用摘要：")
                          + store.summary(id, app.versionId).json()
                          + tr("\n只提出有数据依据的小改进，不推断具体记录内容。");
                final String task = input;
                client = new ResponsesClient(prefs.getString("base", DEFAULT_BASE), vault.read());
                remote(
                    () ->
                        client.generate(
                            prefs.getString("model", "gpt-6.1-sol"), instructions, task),
                    result -> {
                      if (app != null) {
                        List<String> errors = VersionPolicy.errors(app.spec, result.spec);
                        if (!errors.isEmpty()) {
                          fail(new IllegalArgumentException(String.join("\n", errors)));
                          return;
                        }
                      }
                      preview(
                          id,
                          app == null ? null : app.versionId,
                          result.spec,
                          result.explanation,
                          "llm");
                    });
              } catch (Exception e) {
                fail(e);
              }
            }));
    if (app != null) {
      section(b, tr("点选一个细节"), tr("选择字段，让这次修改更准确。"));
      LinearLayout fields = ui.card();
      for (int i = 0; i < app.spec.fields().size(); i++) {
        AppSpec.Field field = app.spec.fields().get(i);
        fields.addView(
            ui.actionRow(
                "sliders-horizontal",
                field.label,
                fieldType(field.type),
                () -> editor(id, field.id, false)));
        if (i < app.spec.fields().size() - 1) ui.divider(fields);
      }
      b.addView(fields);
      b.addView(ui.action(tr("直接调整 · 离线"), "pen-line", false, () -> localEdit(id)));
    } else {
      section(b, tr("也可以从模板开始"), tr("不需要连接 AI，直接打开使用。"));
      LinearLayout templates = ui.card();
      for (AppSpec template : Templates.all())
        templates.addView(
            ui.actionRow(
                ui.appIcon(template),
                template.name(),
                null,
                () -> showApp(store.create(template, "template"), "overview", true)));
      b.addView(templates);
    }
    ui.space(b, 8);
    b.addView(
        ui.text(
            optimize ? tr("深度分析会发送应用定义与聚合使用摘要，不发送记录正文。") : tr("AI 会收到你的描述和应用定义；个人记录不会自动上传。"),
            11,
            Ui.MUTED,
            false));
  }

  private String fieldType(String type) {
    switch (type) {
      case "number":
        return tr("数字");
      case "date":
        return tr("日期");
      case "choice":
        return tr("选项");
      case "boolean":
        return tr("开关");
      default:
        return tr("文本");
    }
  }

  private void preview(
      String id, String base, AppSpec candidate, String explanation, String source) {
    if (id != null) {
      startActivityForResult(
          new Intent(this, EditorActivity.class)
              .putExtra("appId", id)
              .putExtra("candidate", candidate.toString())
              .putExtra("candidateBase", base)
              .putExtra("reason", explanation),
          EDIT);
      return;
    }
    Store.App old = id == null ? null : store.app(id);
    LinearLayout b =
        page(
            tr("预览应用"),
            candidate.name(),
            id == null ? () -> editor(null, null, false) : () -> showApp(id, screen, false),
            -1);
    steps(b, 1);
    LinearLayout identity = ui.row();
    identity.addView(ui.badge(ui.appIcon(candidate), Color.parseColor(candidate.accent()), 48));
    ui.gap(identity, 12);
    LinearLayout labels = ui.col();
    labels.addView(ui.text(candidate.name(), 22, Ui.INK, true));
    ui.space(labels, 6);
    labels.addView(
        ui.body(
            candidate.fields().size()
                + tr(" 个记录项 · ")
                + screenName(candidate.startScreen())
                + tr("首页")));
    identity.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    b.addView(identity);
    ui.space(b, 22);
    LinearLayout changes = ui.card();
    changes.setBackground(ui.shape(Ui.SOFT, 20, false));
    changes.addView(ui.text(tr("这次的调整"), 15, Ui.GREEN, true));
    ui.space(changes, 10);
    changes.addView(ui.body(explanation));
    if (old != null)
      for (String change : VersionPolicy.describe(old.spec, candidate)) {
        ui.space(changes, 6);
        changes.addView(ui.text("• " + change, 14, Ui.INK, false));
      }
    b.addView(changes);
    LinearLayout sample = ui.card();
    sample.addView(ui.text(tr("试填一下"), 18, Ui.INK, true));
    ui.space(sample, 8);
    sample.addView(ui.body(candidate.description()));
    ui.space(sample, 10);
    sample.addView(ui.tag(tr("默认打开：") + screenName(candidate.startScreen())));
    ui.space(sample, 12);
    for (AppSpec.Field f : candidate.fields()) renderField(sample, f, f.defaultValue);
    sample.addView(ui.body(tr("预览输入不会写入正式记录。")));
    b.addView(sample);
    LinearLayout actions = ui.row();
    ui.weighted(
        actions,
        ui.button(tr("暂不采用"), false, id == null ? this::home : () -> showApp(id, screen, false)));
    ui.weighted(
        actions,
        ui.action(
            tr("采用这个版本"),
            "check",
            true,
            () -> {
              try {
                String appId = id;
                if (id == null) appId = store.create(candidate, source);
                else store.apply(id, base, candidate, explanation, source, false);
                toast(tr("已采用，原有记录已保留"));
                showApp(appId, candidate.startScreen(), false);
              } catch (Exception e) {
                fail(e);
              }
            }));
    footer(actions);
  }

  private void localEdit(String id) {
    Store.App app = store.app(id);
    LinearLayout fields = ui.col();
    fields.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12));
    fields.addView(ui.body(tr("只调整外观与入口，不改变记录结构。")));
    EditText name = ui.input(tr("应用名称"), false);
    name.setTag("app-name");
    name.setText(app.spec.name());
    fields.addView(name);
    ui.space(fields, 12);
    Switch quick = ui.toggle(tr("显示快速记录入口"), app.spec.quickAdd()),
        compact = ui.toggle(tr("使用紧凑记录卡片"), app.spec.compact());
    fields.addView(quick);
    fields.addView(compact);
    fields.addView(ui.body(tr("默认打开")));
    Spinner start =
        ui.spinner(
            new String[] {tr("概览"), tr("记录"), tr("统计")},
            Arrays.asList("overview", "records", "stats").indexOf(app.spec.startScreen()));
    fields.addView(start);
    new AlertDialog.Builder(this)
        .setTitle(tr("离线调整"))
        .setView(fields)
        .setNegativeButton(tr("取消"), null)
        .setPositiveButton(
            tr("预览"),
            (d, w) -> {
              try {
                AppSpec next =
                    app.spec
                        .with("name", name.getText().toString().trim())
                        .with("quickAdd", quick.isChecked())
                        .with("compact", compact.isChecked())
                        .with(
                            "startScreen",
                            new String[] {"overview", "records", "stats"}
                                [start.getSelectedItemPosition()]);
                preview(id, app.versionId, next, tr("你手动调整了应用外观和默认入口。"), "local");
              } catch (Exception e) {
                fail(e);
              }
            })
        .show();
  }

  private void localFieldEdit(String id, String fieldId) {
    Store.App app = store.app(id);
    AppSpec.Field field = app.spec.field(fieldId);
    LinearLayout form = ui.col();
    form.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12));
    form.addView(ui.body(tr("调整显示名称与顺序，历史记录保持原来的字段标识。")));
    ui.space(form, 12);
    EditText label = ui.input(tr("显示名称"), false);
    label.setText(field.label);
    form.addView(label);
    ui.space(form, 12);
    String[] positions = new String[app.spec.fields().size()];
    int oldPosition = 0;
    for (int i = 0; i < positions.length; i++) {
      positions[i] = tr("第 ") + (i + 1) + tr(" 项");
      if (app.spec.fields().get(i).id.equals(fieldId)) oldPosition = i;
    }
    Spinner order = ui.spinner(positions, oldPosition);
    form.addView(order);
    new AlertDialog.Builder(this)
        .setTitle(tr("调整「") + field.label + "」")
        .setView(form)
        .setNegativeButton(tr("取消"), null)
        .setPositiveButton(
            tr("预览"),
            (d, w) -> {
              try {
                JSONObject raw = app.spec.json();
                JSONArray original = raw.optJSONArray("fields");
                List<JSONObject> rest = new ArrayList<>();
                JSONObject selected = null;
                for (int i = 0; i < original.length(); i++) {
                  JSONObject f = original.optJSONObject(i);
                  if (f.optString("id").equals(fieldId)) {
                    selected = f;
                    J.put(selected, "label", label.getText().toString().trim());
                  } else rest.add(f);
                }
                rest.add(order.getSelectedItemPosition(), selected);
                JSONArray next = new JSONArray();
                for (JSONObject f : rest) next.put(f);
                J.put(raw, "fields", next);
                preview(
                    id,
                    app.versionId,
                    AppSpec.parse(raw.toString()),
                    tr("你调整了字段显示名称或顺序，记录保持不变。"),
                    "local");
              } catch (Exception e) {
                fail(e);
              }
            })
        .show();
  }

  private void history(String id) {
    Store.App app = store.app(id);
    LinearLayout b =
        page(
            tr("版本与恢复"),
            app.spec.name() + tr(" · 只恢复应用，记录不会被删除"),
            () -> showApp(id, screen, false),
            -1);
    List<Store.Version> versions = store.versions(id);
    for (Store.Version version : versions) {
      LinearLayout card = ui.card();
      boolean current = app.versionId.equals(version.id);
      card.addView(ui.tag(current ? tr("当前版本") : versionName(version.source)));
      ui.space(card, 9);
      card.addView(ui.text(when(version.created), 17, Ui.INK, true));
      ui.space(card, 5);
      card.addView(ui.body(version.reason));
      if (!current) {
        ui.space(card, 12);
        card.addView(
            ui.button(
                tr("恢复到此版本"),
                false,
                () ->
                    new AlertDialog.Builder(this)
                        .setTitle(tr("恢复这个应用版本？"))
                        .setMessage(tr("将创建一个恢复版本；当前及历史记录都会保留。旧版未显示的新字段数据仍保存在本机。"))
                        .setNegativeButton(tr("取消"), null)
                        .setPositiveButton(
                            tr("恢复"),
                            (d, w) -> {
                              try {
                                store.apply(
                                    id,
                                    app.versionId,
                                    version.spec,
                                    tr("恢复至 ") + when(version.created),
                                    "restore",
                                    true);
                                showApp(id, version.spec.startScreen(), false);
                              } catch (Exception e) {
                                fail(e);
                              }
                            })
                        .show()));
      }
      b.addView(card);
    }
  }

  private void appMenu(String id) {
    Store.App app = store.app(id);
    new AlertDialog.Builder(this)
        .setTitle(app.spec.name())
        .setItems(
            new String[] {
              app.frozen ? tr("恢复自主优化") : tr("固定当前版本，暂停自主优化"), tr("用 AI 深度分析改进"), tr("删除这个应用")
            },
            (d, index) -> {
              if (index == 0) {
                store.freeze(id, !app.frozen);
                showApp(id, screen, false);
              } else if (index == 1) editor(id, null, true);
              else
                new AlertDialog.Builder(this)
                    .setTitle(tr("删除应用及其记录？"))
                    .setMessage(tr("将删除该应用的全部记录、历史版本和建议。可先在设置里导出备份。"))
                    .setNegativeButton(tr("保留"), null)
                    .setPositiveButton(
                        tr("删除"),
                        (dialog, w) -> {
                          store.deleteApp(id);
                          home();
                        })
                    .show();
            })
        .show();
  }

  private void insights() {
    currentApp = null;
    LinearLayout b = page(tr("生长"), null, null, 1);
    ui.space(b, 10);
    b.addView(ui.text(tr("慢慢变好"), 29, Ui.INK, true));
    ui.space(b, 8);
    b.addView(ui.body(tr("让应用的每次改进，都有迹可循。")));
    ui.space(b, 22);
    LinearLayout intro = ui.card();
    intro.setBackground(ui.shape(Ui.GREEN, 25, false));
    LinearLayout hero = ui.row();
    LinearLayout words = ui.col();
    words.addView(ui.eyebrow("GROW AT YOUR PACE", Ui.LIME));
    ui.space(words, 12);
    words.addView(ui.text(tr("先观察，\n再改变。"), 25, Color.WHITE, true));
    hero.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    hero.addView(ui.artwork(true), new LinearLayout.LayoutParams(ui.dp(108), ui.dp(108)));
    intro.addView(hero);
    ui.space(intro, 10);
    intro.addView(ui.text(tr("从你的使用习惯里，发现更顺手的方式。"), 12, 0xffc0d3c1, false));
    ui.space(intro, 18);
    Button observe = ui.action(tr("现在观察一次"), "sprout", false, this::analyzeUsage);
    observe.setBackground(ui.shape(Ui.LIME, 14, false));
    intro.addView(observe);
    b.addView(intro);
    List<Store.Proposal> proposals = store.proposals(null);
    section(b, tr("值得试试的小变化"), proposals.isEmpty() ? null : tr("可以先预览，是否改变由你决定。"));
    if (proposals.isEmpty()) {
      b.addView(ui.empty("leaf", tr("还在慢慢了解你"), tr("自然使用就好。有了足够依据，\n值得尝试的小改进会出现在这里。")));
    }
    for (Store.Proposal proposal : proposals) {
      Store.App app = store.app(proposal.appId);
      LinearLayout card = ui.card();
      LinearLayout heading = ui.row();
      heading.addView(ui.badge(ui.appIcon(app.spec), Color.parseColor(app.spec.accent()), 36));
      ui.gap(heading, 10);
      heading.addView(ui.text(app.spec.name(), 16, Ui.INK, true));
      card.addView(heading);
      ui.space(card, 14);
      card.addView(ui.body(proposal.reason));
      ui.space(card, 18);
      LinearLayout actions = ui.row();
      ui.weighted(
          actions,
          ui.button(
              tr("忽略"),
              false,
              () -> {
                store.dismiss(proposal.id);
                insights();
              }));
      ui.weighted(
          actions,
          ui.action(
              tr("预览修改"),
              "arrow-up-right",
              true,
              () ->
                  preview(
                      proposal.appId,
                      proposal.baseVersion,
                      proposal.candidate,
                      proposal.reason,
                      "rule")));
      card.addView(actions);
      b.addView(card);
    }
    section(b, tr("我的使用观察"), tr("近 7 天 · 当前应用版本"));
    for (Store.App app : store.apps()) {
      ReflectionEngine.Summary summary = store.summary(app.id, app.versionId);
      LinearLayout card = ui.card();
      LinearLayout heading = ui.row();
      heading.addView(ui.badge(ui.appIcon(app.spec), Color.parseColor(app.spec.accent()), 36));
      ui.gap(heading, 10);
      heading.addView(
          ui.text(app.spec.name(), 16, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
      if (app.frozen) heading.addView(ui.tag(tr("已固定")));
      card.addView(heading);
      ui.space(card, 20);
      LinearLayout counts = ui.row();
      ui.weighted(counts, usageCounter(summary.opens, tr("次打开")));
      ui.weighted(counts, usageCounter(summary.saves, tr("次保存")));
      ui.weighted(counts, usageCounter(summary.stats, tr("次看统计")));
      card.addView(counts);
      List<Store.Version> versions = store.versions(app.id);
      if (versions.size() > 1) {
        ui.space(card, 16);
        card.addView(
            ui.body(
                ReflectionEngine.evaluation(store.summary(app.id, versions.get(1).id), summary)));
      }
      ui.space(card, 16);
      ui.divider(card);
      card.addView(
          ui.actionRow(
              "arrow-up-right",
              tr("打开应用"),
              null,
              () -> showApp(app.id, app.spec.startScreen(), true)));
      b.addView(card);
    }
    LinearLayout note = ui.row();
    note.addView(ui.icon("shield-check", Ui.MUTED, 16));
    ui.gap(note, 8);
    note.addView(ui.text(tr("只观察操作与耗时，不采集输入正文。"), 11, Ui.MUTED, false));
    b.addView(note);
    ui.space(b, 8);
    b.addView(ui.text(tr("至少跨 2 个观察日、打开 4 次才考虑建议；样本不足时不判断是否改善。"), 11, Ui.MUTED, false));
  }

  private LinearLayout usageCounter(long value, String label) {
    LinearLayout counter = ui.col();
    counter.setGravity(Gravity.CENTER);
    TextView number = ui.text(String.valueOf(value), 25, Ui.GREEN, true);
    number.setGravity(Gravity.CENTER);
    counter.addView(number);
    ui.space(counter, 6);
    TextView caption = ui.text(label, 10, Ui.MUTED, false);
    caption.setGravity(Gravity.CENTER);
    counter.addView(caption);
    return counter;
  }

  private void analyzeUsage() {
    executor.submit(
        () -> {
          try {
            store.reflect(((LivingApplication) getApplication()).automatic());
            runOnUiThread(
                () -> {
                  if (!isDestroyed()) {
                    insights();
                    toast(tr("已完成本机观察"));
                  }
                });
          } catch (Exception error) {
            runOnUiThread(
                () -> {
                  if (!isDestroyed()) fail(error);
                });
          }
        });
  }

  private void settings() {
    currentApp = null;
    LinearLayout b = page(tr("设置"), tr("数据在自己手里，改变也由你决定。"), null, 2);
    ui.space(b, 10);
    b.addView(ui.text(tr("按你的偏好"), 29, Ui.INK, true));
    ui.space(b, 8);
    b.addView(ui.body(tr("数据在自己手里，改变也由你决定。")));
    ui.space(b, 10);
    LinearLayout language = ui.card();
    String chosenLanguage = Languages.requested(this);
    String selectedLanguage =
        chosenLanguage.equals(Languages.CHINESE)
            ? "简体中文"
            : chosenLanguage.equals(Languages.ENGLISH)
                ? "English"
                : getString(R.string.language_system);
    View languageRow =
        ui.actionRow(
            "sliders-horizontal",
            getString(R.string.language_title),
            selectedLanguage,
            () -> {
              String[] codes = {Languages.SYSTEM, Languages.CHINESE, Languages.ENGLISH};
              int selectedIndex = Arrays.asList(codes).indexOf(Languages.requested(this));
              new AlertDialog.Builder(this)
                  .setTitle(getString(R.string.language_title))
                  .setSingleChoiceItems(
                      new String[] {getString(R.string.language_system), "简体中文", "English"},
                      selectedIndex,
                      (dialog, which) -> {
                        dialog.dismiss();
                        Languages.choose(this, codes[which]);
                      })
                  .setNegativeButton(android.R.string.cancel, null)
                  .show();
            });
    languageRow.setTag("app-language");
    language.addView(languageRow);
    language.addView(ui.body(getString(R.string.language_description)));
    b.addView(language);
    section(b, tr("AI 连接"), tr("只有生成、自然语言修改和深度分析需要联网。"));
    LinearLayout connection = ui.card();
    connection.addView(ui.text(tr("服务地址"), 12, Ui.INK, true));
    ui.space(connection, 8);
    EditText base = ui.input("https://your-api.example/v1", false);
    base.setText(prefs.getString("base", DEFAULT_BASE));
    base.setTextSize(13);
    connection.addView(base);
    ui.space(connection, 10);
    connection.addView(ui.text(tr("模型"), 12, Ui.INK, true));
    ui.space(connection, 8);
    EditText model = ui.input(tr("模型名称"), false);
    model.setText(prefs.getString("model", "gpt-6.1-sol"));
    model.setTextSize(13);
    connection.addView(model);
    ui.space(connection, 10);
    connection.addView(ui.text("API Key", 12, Ui.INK, true));
    ui.space(connection, 8);
    EditText key = ui.input(tr("输入密钥，留空则保留已保存的"), false);
    key.setSaveEnabled(false);
    key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    key.setTransformationMethod(PasswordTransformationMethod.getInstance());
    connection.addView(key);
    ui.space(connection, 8);
    try {
      connection.addView(
          ui.body(vault.read().isEmpty() ? tr("尚未保存密钥") : tr("密钥已在本机加密保存，不会填回输入框或导出到备份。")));
    } catch (Exception e) {
      connection.addView(ui.body(tr("请重新保存凭据。")));
    }
    ui.space(connection, 10);
    connection.addView(
        ui.text(tr("填写可信的 HTTPS API 地址；HTTP 仅用于本机开发，传输不加密。"), 11, 0xff8a6e49, false));
    ui.space(connection, 14);
    Runnable save =
        () -> {
          try {
            String url = ResponsesProtocol.validateBase(base.getText().toString());
            String m = model.getText().toString().trim();
            if (m.isEmpty()) throw new IllegalArgumentException(tr("请填写模型名称"));
            if (!key.getText().toString().trim().isEmpty()) {
              vault.save(key.getText().toString());
              key.setText("");
            }
            prefs.edit().putString("base", url).putString("model", m).apply();
            toast(tr("连接配置已保存"));
          } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
          }
        };
    connection.addView(
        ui.button(
            tr("保存连接配置"),
            true,
            () -> {
              try {
                save.run();
                settings();
              } catch (Exception e) {
                fail(e);
              }
            }));
    ui.space(connection, 9);
    connection.addView(
        ui.button(
            tr("测试连接并发现模型"),
            false,
            () -> {
              try {
                save.run();
                client = new ResponsesClient(prefs.getString("base", DEFAULT_BASE), vault.read());
                remote(
                    client::models,
                    models -> {
                      if (models.isEmpty()) {
                        toast(tr("网关没有返回可用模型"));
                        return;
                      }
                      new AlertDialog.Builder(this)
                          .setTitle(tr("选择模型 · ") + models.size() + tr(" 个可用"))
                          .setItems(
                              models.toArray(new String[0]),
                              (d, index) -> {
                                prefs.edit().putString("model", models.get(index)).apply();
                                settings();
                              })
                          .setNegativeButton(tr("保留当前选择"), null)
                          .show();
                    });
              } catch (Exception e) {
                fail(e);
              }
            }));
    ui.space(connection, 8);
    connection.addView(
        ui.button(
            tr("清除已保存密钥"),
            false,
            () -> {
              try {
                vault.save("");
                settings();
              } catch (Exception e) {
                fail(e);
              }
            }));
    b.addView(connection);
    section(b, tr("自主优化"), tr("不需要 LLM，也不会在后台调用付费 API。"));
    LinearLayout adaptation = ui.card();
    Switch automatic = ui.toggle(tr("自动采用允许的小调整"), prefs.getBoolean("automatic", false));
    automatic.setOnCheckedChangeListener(
        (v, checked) -> prefs.edit().putBoolean("automatic", checked).apply());
    adaptation.addView(automatic);
    adaptation.addView(ui.body(tr("首版只自动增加快速记录入口，每个应用 24 小时内最多一次。其他修改仍先预览；每个应用可单独固定版本。")));
    b.addView(adaptation);
    section(b, tr("数据与备份"), tr("备份包含记录类应用定义和业务记录；不包含重构界面、密钥、使用事件和版本历史。"));
    LinearLayout backup = ui.card();
    backup.addView(
        ui.action(
            tr("导出本机备份"),
            "download",
            false,
            () -> {
              Intent intent =
                  new Intent(Intent.ACTION_CREATE_DOCUMENT)
                      .addCategory(Intent.CATEGORY_OPENABLE)
                      .setType("application/json")
                      .putExtra(Intent.EXTRA_TITLE, "living-apps-" + LocalDate.now() + ".json");
              startActivityForResult(intent, EXPORT);
            }));
    ui.space(backup, 8);
    backup.addView(
        ui.action(
            tr("导入为新的应用副本"),
            "upload",
            false,
            () -> {
              Intent intent =
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .addCategory(Intent.CATEGORY_OPENABLE)
                      .setType("application/json");
              startActivityForResult(intent, IMPORT);
            }));
    ui.space(backup, 8);
    backup.addView(
        ui.action(
            tr("清除使用观察数据"),
            "trash-2",
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle(tr("清除使用观察？"))
                    .setMessage(tr("只清除使用事件与待处理建议，个人记录和应用版本保留。"))
                    .setNegativeButton(tr("取消"), null)
                    .setPositiveButton(
                        tr("清除"),
                        (d, w) -> {
                          store.clearEvents();
                          toast(tr("使用观察数据已清除"));
                        })
                    .show()));
    b.addView(backup);
    LinearLayout legal = ui.card();
    legal.addView(
        ui.actionRow(
            "circle-help",
            tr("开源许可"),
            tr("Lucide 图标及 Feather 贡献者"),
            () -> {
              try {
                new AlertDialog.Builder(this)
                    .setTitle(tr("开源许可"))
                    .setMessage(asset("lucide-license.txt"))
                    .setPositiveButton(tr("知道了"), null)
                    .show();
              } catch (Exception error) {
                fail(error);
              }
            }));
    b.addView(legal);
    section(b, tr("关于这个版本"), null);
    String appVersion;
    try {
      appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (android.content.pm.PackageManager.NameNotFoundException error) {
      appVersion = "";
    }
    b.addView(ui.body(getString(R.string.about_version, appVersion)));
  }

  private String asset(String file) throws IOException {
    try (InputStream in = getAssets().open(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
      return out.toString("UTF-8");
    }
  }

  private <T> void remote(Callable<T> task, Consumer<T> success) {
    int token = ++requestToken;
    LinearLayout progress = ui.col();
    progress.setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(20));
    progress.addView(new ProgressBar(this));
    ui.space(progress, 12);
    progress.addView(ui.body(tr("正在与模型一起设计。完成后先展示预览，当前应用保持可用。")));
    loading =
        new AlertDialog.Builder(this)
            .setTitle(tr("正在处理"))
            .setView(progress)
            .setCancelable(false)
            .setNegativeButton(
                tr("取消"),
                (d, w) -> {
                  requestToken++;
                  if (client != null) client.cancel();
                  if (request != null) request.cancel(true);
                })
            .create();
    loading.show();
    request =
        executor.submit(
            () -> {
              try {
                T result = task.call();
                runOnUiThread(
                    () -> {
                      if (token != requestToken || isDestroyed()) return;
                      loading.dismiss();
                      success.accept(result);
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () -> {
                      if (token != requestToken || isDestroyed()) return;
                      loading.dismiss();
                      fail(e);
                    });
              }
            });
  }

  @Override
  protected void onActivityResult(int code, int result, Intent data) {
    super.onActivityResult(code, result, data);
    if (code == REBUILD) {
      home();
      return;
    }
    if (code == EDIT) {
      try {
        String id = data == null ? currentApp : data.getStringExtra("appId");
        if (id != null) {
          showApp(id, screen, false);
          pageScroll.post(() -> pageScroll.scrollTo(0, editorScrollY));
        } else home();
      } catch (Exception e) {
        home();
      }
      return;
    }
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (code == EXPORT) {
      try (OutputStream out = getContentResolver().openOutputStream(uri)) {
        if (out == null) throw new IOException(tr("无法写入所选文件"));
        out.write(store.exportBackup().getBytes(StandardCharsets.UTF_8));
        toast(tr("备份已导出，不含密钥"));
      } catch (Exception e) {
        fail(e);
      }
    } else if (code == IMPORT) {
      try (InputStream in = getContentResolver().openInputStream(uri);
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        if (in == null) throw new IOException(tr("无法读取所选文件"));
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
          if (out.size() + n > 5_000_000) throw new IOException(tr("备份超过 5 MB"));
          out.write(buffer, 0, n);
        }
        int count = store.importBackup(out.toString("UTF-8"));
        toast(tr("已导入 ") + count + tr(" 个独立应用副本"));
        home();
      } catch (Exception e) {
        fail(e);
      }
    }
  }
}

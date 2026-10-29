package dev.livingapps;

import static dev.livingapps.core.Messages.tr;

import android.accessibilityservice.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import dev.livingapps.core.*;
import java.util.*;
import org.json.*;

/** Explicit foreground sessions only. No remote control, screenshot capture or background agent. */
public final class RebuildAccessibilityService extends AccessibilityService {
  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(Languages.wrap(base));
  }

  static RebuildAccessibilityService connected;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private RebuildStore store;
  private RebuildStore.Entry session;
  private Ui ui;
  private WindowManager windows;
  private View overlay;
  private boolean busy, picking, waiting;
  private int generation;
  private String message = "", completedWidget = "", appendTo = "";
  private long completedAt;
  private final Map<String, TextView> values = new HashMap<>();
  private final Map<String, EditText> inputs = new HashMap<>();
  private final Map<String, Button> actions = new HashMap<>();
  private final Map<String, LinearLayout> collections = new HashMap<>();
  private TextView state;
  private Button verifyButton;
  private final BroadcastReceiver screenOff =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          stop();
        }
      };

  static final class Node implements AutoCloseable {
    final AccessibilityNodeInfo nativeNode;
    final JSONObject data;

    Node(AccessibilityNodeInfo n, JSONObject d) {
      nativeNode = n;
      data = d;
    }

    @Override
    public void close() {
      nativeNode.recycle();
    }
  }

  static final class Snapshot implements AutoCloseable {
    final List<Node> nodes = new ArrayList<>();
    boolean sensitive, available, foreignActive, truncated;
    int visited;

    List<JSONObject> data() {
      List<JSONObject> out = new ArrayList<>();
      for (Node n : nodes) out.add(n.data);
      return out;
    }

    @Override
    public void close() {
      for (Node n : nodes) n.close();
    }
  }

  @Override
  protected void onServiceConnected() {
    connected = this;
    store = new RebuildStore(this);
    ui = new Ui(this);
    windows = (WindowManager) getSystemService(WINDOW_SERVICE);
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(
          screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    listen(false);
  }

  @Override
  public void onInterrupt() {
    stop();
  }

  @Override
  public void onDestroy() {
    stop();
    try {
      unregisterReceiver(screenOff);
    } catch (IllegalArgumentException ignored) {
    }
    connected = null;
    if (store != null) store.close();
    super.onDestroy();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (session == null || picking || busy || waiting) return;
    int type = event.getEventType();
    if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      handler.removeCallbacks(refresh);
      handler.postDelayed(refresh, 200);
    }
  }

  private final Runnable refresh = () -> refreshValues();
  private final Runnable watchdog =
      new Runnable() {
        @Override
        public void run() {
          if (session == null) return;
          if (!waiting) {
            if (!picking && !busy) refreshValues();
            else
              try (Snapshot snapshot = snapshot()) {
                if (snapshot.foreignActive) {
                  stop();
                  return;
                }
              } catch (RuntimeException ignored) {
              }
          }
          if (session != null) handler.postDelayed(this, 1500);
        }
      };

  void begin(String id, boolean pick, String append) {
    stop();
    ui = new Ui(Languages.wrap(this));
    session = store.get(id);
    picking = pick;
    appendTo = append == null ? "" : append;
    try {
      assertCurrent();
      if (!getSharedPreferences("rebuild-consent", MODE_PRIVATE)
          .getBoolean(session.spec.pkg(), false))
        throw new IllegalStateException(tr("请先在 Living 阅读并同意本机操作授权"));
      listen(true);
      Intent launch = getPackageManager().getLaunchIntentForPackage(session.spec.pkg());
      if (launch == null) throw new IllegalStateException(tr("无法打开原应用"));
      launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(launch);
      waiting = true;
      handler.postDelayed(watchdog, 1500);
      final int token = generation;
      handler.postDelayed(
          () -> {
            if (token != generation || session == null) return;
            waiting = false;
            if (picking) dock(tr("在原应用打开目标页面，再选取信息或操作"));
            else render();
          },
          700);
    } catch (Exception e) {
      error(e);
      stop();
    }
  }

  void stop() {
    generation++;
    handler.removeCallbacksAndMessages(null);
    removeOverlay();
    session = null;
    busy = false;
    picking = false;
    waiting = false;
    completedWidget = "";
    message = "";
    appendTo = "";
    values.clear();
    inputs.clear();
    actions.clear();
    collections.clear();
    state = null;
    verifyButton = null;
    listen(false);
  }

  private void listen(boolean enabled) {
    AccessibilityServiceInfo info = getServiceInfo();
    if (info == null) return;
    info.eventTypes =
        enabled
            ? AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED
            : 0;
    info.packageNames =
        enabled && session != null
            ? new String[] {session.spec.pkg()}
            : new String[] {getPackageName()};
    setServiceInfo(info);
  }

  private void assertCurrent() {
    if (session == null) throw new IllegalStateException(tr("会话已结束"));
    RebuildStore.Entry current = store.get(session.id);
    if (current.revision != session.revision) throw new IllegalStateException(tr("界面已有新版本，请重新打开"));
    RebuildTarget target = RebuildTarget.get(this, session.spec.pkg());
    session.spec.assertTarget(target.pkg, target.version, target.certificate);
  }

  private Snapshot snapshot() {
    Snapshot out = new Snapshot();
    if (session == null) return out;
    for (AccessibilityWindowInfo window : getWindows()) {
      try {
        if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
        AccessibilityNodeInfo root = window.getRoot();
        if (root == null) continue;
        try {
          if (session.spec.pkg().contentEquals(root.getPackageName())) {
            out.available = true;
            walk(root, out, 0);
          } else if (window.isActive()) {
            out.foreignActive = true;
          }
        } finally {
          root.recycle();
        }
      } finally {
        window.recycle();
      }
    }
    return out;
  }

  private void walk(AccessibilityNodeInfo n, Snapshot out, int depth) {
    if (!session.spec.pkg().contentEquals(n.getPackageName())) return;
    if (depth > 40 || ++out.visited > 1200 || out.nodes.size() >= 300) {
      out.truncated = true;
      return;
    }
    if (n.isVisibleToUser()) {
      String text = string(n.getText()), desc = string(n.getContentDescription());
      boolean sensitive =
          n.isPassword() || (Build.VERSION.SDK_INT >= 34 && n.isAccessibilityDataSensitive());
      if (sensitive
          || text.equals("收银台")
          || text.equals("确认支付")
          || text.equalsIgnoreCase("checkout")) out.sensitive = true;
      if (!sensitive) {
        JSONObject data =
            J.obj(
                "viewId",
                string(n.getViewIdResourceName()),
                "text",
                n.isEditable() ? "" : text,
                "description",
                desc,
                "className",
                string(n.getClassName()),
                "click",
                n.isClickable(),
                "input",
                n.isEditable(),
                "scroll",
                n.isScrollable(),
                "enabled",
                n.isEnabled());
        out.nodes.add(new Node(AccessibilityNodeInfo.obtain(n), data));
      }
    }
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo child = n.getChild(i);
      if (child != null)
        try {
          walk(child, out, depth + 1);
        } finally {
          child.recycle();
        }
    }
  }

  private static String string(CharSequence s) {
    return s == null ? "" : s.toString();
  }

  private JSONObject selector(Node node, Snapshot snapshot, boolean read) {
    JSONObject d = node.data;
    JSONObject s =
        J.obj(
            "viewId",
            d.optString("viewId"),
            "className",
            d.optString("className"),
            "text",
            read ? "" : d.optString("text"),
            "description",
            read ? "" : d.optString("description"));
    if (s.optString("viewId").isEmpty()) {
      J.put(s, "text", d.optString("text"));
      J.put(s, "description", d.optString("description"));
    }
    RebuildSpec.selector(s);
    RebuildSpec.resolve(s, snapshot.data());
    return s;
  }

  private void removeOverlay() {
    if (overlay != null && windows != null) {
      try {
        windows.removeView(overlay);
      } catch (IllegalArgumentException ignored) {
      }
      overlay = null;
    }
  }

  private void show(View view, boolean full) {
    removeOverlay();
    overlay = view;
    WindowManager.LayoutParams p =
        new WindowManager.LayoutParams(
            -1,
            full ? -1 : -2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | (full ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            android.graphics.PixelFormat.TRANSLUCENT);
    p.gravity = full ? Gravity.TOP : Gravity.BOTTOM;
    p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
    try {
      windows.addView(view, p);
    } catch (RuntimeException e) {
      overlay = null;
      stop();
      error(new IllegalStateException(tr("无法显示覆盖界面，请检查系统授权")));
    }
  }

  private LinearLayout frame(String title) {
    LinearLayout root =
        new LinearLayout(this) {
          @Override
          public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
              if (event.getAction() == KeyEvent.ACTION_UP) returnToLiving();
              return true;
            }
            return super.dispatchKeyEvent(event);
          }
        };
    root.setOrientation(LinearLayout.VERTICAL);
    root.setFocusableInTouchMode(true);
    root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    root.setBackgroundColor(Ui.BG);
    root.setPadding(ui.dp(18), ui.dp(20), ui.dp(18), ui.dp(20));
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              ui.dp(18) + i.getSystemWindowInsetLeft(),
              ui.dp(20) + i.getSystemWindowInsetTop(),
              ui.dp(18) + i.getSystemWindowInsetRight(),
              ui.dp(20) + i.getSystemWindowInsetBottom());
          return i;
        });
    LinearLayout head = ui.row();
    head.addView(ui.badge("layout-grid", Ui.GREEN, 36));
    ui.gap(head, 10);
    head.addView(ui.text(title, 18, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(ui.iconButton("arrow-left", tr("退出重构界面"), () -> returnToLiving()));
    root.addView(head);
    ui.space(root, 12);
    return root;
  }

  private void dock(String hint) {
    values.clear();
    inputs.clear();
    actions.clear();
    collections.clear();
    state = null;
    verifyButton = null;
    LinearLayout root = ui.col();
    root.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(20));
    root.setBackground(ui.shape(Ui.BG, 20, true));
    root.addView(
        ui.text("Living · " + (picking ? tr("选取页面内容") : tr("原应用操作中")), 14, Ui.GREEN, true));
    root.addView(ui.body(hint));
    ui.space(root, 8);
    if (picking) root.addView(ui.button(tr("选取当前页面"), true, this::pickPage));
    else root.addView(ui.button(tr("回到我的界面"), true, this::render));
    root.addView(ui.button(tr("完成并返回 Living"), false, this::returnToLiving));
    show(root, false);
  }

  private void pickPage() {
    try {
      assertCurrent();
      try (Snapshot snap = snapshot()) {
        if (!snap.available) throw new IllegalStateException(tr("请先打开所选原应用的页面"));
        if (snap.foreignActive || snap.truncated)
          throw new IllegalStateException(tr("页面过于复杂或不在原应用中，请换一个页面"));
        if (snap.sensitive) throw new IllegalStateException(tr("此页包含敏感操作或受保护内容，请在原应用处理并换一个页面"));
        LinearLayout root = frame(tr("选择页面内容"));
        root.addView(ui.body(tr("只读取当前已加载页面。选取后可改名称和顺序；未匹配的内容不会保留旧值。")));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = ui.col();
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        int count = 0;
        Set<String> offeredLists = new HashSet<>();
        for (Node node : snap.nodes) {
          JSONObject d = node.data;
          String label = d.optString("text");
          if (label.isEmpty()) label = d.optString("description");
          if (label.isEmpty())
            label = d.optBoolean("input") ? tr("输入框") : d.optBoolean("scroll") ? tr("滚动列表") : "";
          if (label.isEmpty() || label.length() > 180) continue;
          final String title = label.length() > 50 ? label.substring(0, 50) : label;
          LinearLayout card = ui.card();
          card.addView(ui.text(title, 15, Ui.INK, true));
          boolean added = false;
          if (appendTo.isEmpty()
              && !d.optBoolean("input")
              && !d.optString("viewId").isEmpty()
              && !d.optString("text").isEmpty()) {
            JSONObject group =
                J.obj(
                    "viewId",
                    d.optString("viewId"),
                    "className",
                    d.optString("className"),
                    "text",
                    "",
                    "description",
                    "");
            int countMatches = 0;
            for (JSONObject candidate : snap.data())
              if (RebuildSpec.matches(group, candidate)) countMatches++;
            if (countMatches > 1 && offeredLists.add(group.toString())) {
              String page = RebuildSpec.pageSignature(snap.data());
              card.addView(
                  ui.button(
                      tr("显示同类列表（") + countMatches + tr(" 项，只读）"),
                      false,
                      () -> addSelection(tr("页面列表"), "list", group, page)));
              card.addView(
                  ui.button(
                      tr("显示同类列表，并允许点击项目"),
                      false,
                      () -> addSelection(tr("页面列表"), "list_click", group, page)));
              added = true;
            }
          }
          for (String kind : new String[] {"read", "click", "input", "scroll"}) {
            if (!kind.equals("read") && !d.optBoolean(kind)) continue;
            if (!appendTo.isEmpty() && kind.equals("read")) continue;
            try {
              JSONObject selected = selector(node, snap, kind.equals("read"));
              String button =
                  kind.equals("read")
                      ? tr("显示这条信息")
                      : kind.equals("click")
                          ? tr("添加点击动作")
                          : kind.equals("input") ? tr("添加输入动作") : tr("添加滚动动作");
              String page = RebuildSpec.pageSignature(snap.data());
              if (!kind.equals("read")
                  && RebuildSpec.sensitive(d.optString("text") + " " + d.optString("description")))
                continue;
              card.addView(
                  ui.button(button, false, () -> addSelection(title, kind, selected, page)));
              added = true;
            } catch (IllegalArgumentException ignored) {
            }
          }
          if (added) {
            list.addView(card);
            count++;
          }
        }
        if (count == 0) list.addView(ui.body(tr("此页没有可唯一定位的内容。可返回原应用换一页；不会使用坐标盲点。")));
        root.addView(ui.button(tr("返回原应用换一页"), false, () -> dock(tr("打开下一页后，再点选取"))));
        show(root, true);
      }
    } catch (Exception e) {
      error(e);
    }
  }

  private void addSelection(String label, String kind, JSONObject selector, String page) {
    try {
      assertCurrent();
      JSONObject json = session.spec.json();
      JSONArray widgets = json.optJSONArray("widgets");
      if (!appendTo.isEmpty()) {
        boolean found = false;
        for (int i = 0; i < widgets.length(); i++) {
          JSONObject w = widgets.optJSONObject(i);
          if (!w.optString("id").equals(appendTo)) continue;
          w.optJSONArray("steps").put(J.obj("op", kind, "selector", selector, "page", page));
          if (kind.equals("input")) J.put(w, "kind", "input");
          found = true;
        }
        if (!found) throw new IllegalArgumentException(tr("原动作不存在"));
      } else {
        boolean list = kind.startsWith("list");
        JSONObject w =
            J.obj(
                "id",
                "w" + UUID.randomUUID().toString().replace("-", ""),
                "label",
                label,
                "kind",
                list
                    ? "list"
                    : kind.equals("read") ? "read" : kind.equals("input") ? "input" : "action");
        if (kind.equals("read") || list) {
          J.put(w, "selector", selector);
          J.put(w, "page", page);
          if (list) J.put(w, "interactive", kind.equals("list_click"));
        } else J.put(w, "steps", J.arr(J.obj("op", kind, "selector", selector, "page", page)));
        widgets.put(w);
      }
      store.save(session.id, session.revision, new RebuildSpec(json));
      returnToLiving();
    } catch (Exception e) {
      error(e);
    }
  }

  private void render() {
    if (session == null) return;
    values.clear();
    inputs.clear();
    actions.clear();
    collections.clear();
    LinearLayout root = frame(session.spec.name());
    int fg = session.spec.dark() ? 0xffe6ede8 : Ui.INK,
        bg = session.spec.dark() ? 0xff132a22 : Ui.BG;
    root.setBackgroundColor(bg);
    LinearLayout heading = (LinearLayout) root.getChildAt(0);
    ((TextView) heading.getChildAt(2)).setTextColor(fg);
    ((ImageButton) heading.getChildAt(3))
        .setImageTintList(android.content.res.ColorStateList.valueOf(fg));
    root.addView(
        ui.text(tr("原应用执行 · 本机界面 · 按组件验证"), 11, session.spec.dark() ? Ui.LIME : Ui.MUTED, false));
    state =
        ui.text(
            message.isEmpty() ? tr("正在读取原应用页面…") : message,
            12,
            session.spec.dark() ? Ui.LIME : Ui.GREEN,
            false);
    root.addView(state);
    ui.space(root, 12);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    LinearLayout list = ui.col();
    scroll.addView(list);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    JSONArray widgets = session.spec.widgets();
    if (widgets.length() == 0) list.addView(ui.body(tr("还没有组件。返回 Living，在原应用页面选取信息和动作。")));
    for (int i = 0; i < widgets.length(); i++) {
      JSONObject w = widgets.optJSONObject(i);
      String id = w.optString("id"), kind = w.optString("kind");
      LinearLayout card = ui.card();
      if (session.spec.dark()) card.setBackground(ui.shape(0xff203c31, 20, true));
      int padding = session.spec.compact() ? 12 : 18;
      card.setPadding(ui.dp(padding), ui.dp(padding), ui.dp(padding), ui.dp(padding));
      card.addView(ui.text(w.optString("label"), 14, fg, true));
      ui.space(card, 8);
      if (kind.equals("read")) {
        TextView value = ui.text(tr("当前页面不可用"), session.spec.compact() ? 18 : 23, fg, true);
        values.put(id, value);
        card.addView(value);
      } else if (kind.equals("list")) {
        LinearLayout items = ui.col();
        collections.put(id, items);
        card.addView(
            ui.text(
                w.optBoolean("interactive") ? tr("点击项目由原应用执行 · 最多显示 40 项") : tr("只读列表 · 最多显示 40 项"),
                10,
                session.spec.dark() ? Ui.LIME : Ui.MUTED,
                false));
        card.addView(items);
      } else {
        if (kind.equals("input")) {
          EditText input = ui.input(tr("输入内容，仅用于本次操作"), false);
          input.setSaveEnabled(false);
          inputs.put(id, input);
          card.addView(input);
          ui.space(card, 8);
        }
        card.addView(
            ui.text(
                session.verified.contains(id) ? tr("本机试用已确认 · 当前适配版本") : tr("待试用确认"),
                10,
                session.spec.dark() ? Ui.LIME : Ui.MUTED,
                false));
        Button action = ui.button(tr("运行 · ") + w.optString("label"), true, () -> startAction(id));
        actions.put(id, action);
        card.addView(action);
      }
      list.addView(card);
    }
    verifyButton = ui.button(tr("结果正确，标记本机已试用"), false, this::confirmAttempt);
    verifyButton.setVisibility(completedWidget.isEmpty() ? View.GONE : View.VISIBLE);
    root.addView(verifyButton);
    root.addView(ui.button(tr("查看原应用 / 手动操作"), false, () -> dock(tr("原界面已显示；核对结果后可回到自定义界面"))));
    root.addView(ui.button(tr("停止并返回 Living"), false, this::returnToLiving));
    show(root, true);
    root.requestFocus();
    root.requestApplyInsets();
    refreshValues();
  }

  private void refreshValues() {
    if (session == null || picking || busy || waiting) return;
    try {
      assertCurrent();
      try (Snapshot s = snapshot()) {
        if (s.foreignActive) {
          stop();
          return;
        }
        boolean available = s.available && !s.sensitive && !s.truncated;
        for (Map.Entry<String, TextView> value : values.entrySet()) {
          String text = tr("当前页面未匹配");
          if (available)
            try {
              JSONObject w = session.spec.widget(value.getKey());
              if (!w.optString("page").equals(RebuildSpec.pageSignature(s.data())))
                throw new IllegalArgumentException(tr("页面结构已变化"));
              JSONObject node =
                  s.nodes.get(RebuildSpec.resolve(w.optJSONObject("selector"), s.data())).data;
              text = node.optString("text");
              if (text.isEmpty()) text = node.optString("description");
              if (text.isEmpty()) text = tr("此控件当前没有文本");
            } catch (IllegalArgumentException ignored) {
            }
          value.getValue().setText(text);
        }
        for (Button button : actions.values()) button.setEnabled(available);
        for (Map.Entry<String, LinearLayout> group : collections.entrySet()) {
          LinearLayout list = group.getValue();
          list.removeAllViews();
          JSONObject widget = session.spec.widget(group.getKey());
          int count = 0;
          if (available && widget.optString("page").equals(RebuildSpec.pageSignature(s.data()))) {
            for (Node node : s.nodes) {
              if (!RebuildSpec.matches(widget.optJSONObject("selector"), node.data)) continue;
              String label = node.data.optString("text");
              if (label.isEmpty()) label = node.data.optString("description");
              if (label.isEmpty() || label.length() > 180 || RebuildSpec.sensitive(label)) continue;
              if (widget.optBoolean("interactive")) {
                JSONObject selector =
                    J.obj(
                        "viewId",
                        node.data.optString("viewId"),
                        "className",
                        node.data.optString("className"),
                        "text",
                        node.data.optString("text"),
                        "description",
                        node.data.optString("description"));
                Button item = ui.button(label, false, () -> clickListItem(widget, selector));
                try {
                  RebuildSpec.resolve(selector, s.data());
                } catch (IllegalArgumentException e) {
                  item.setEnabled(false);
                }
                list.addView(item);
              } else
                list.addView(ui.text(label, 16, session.spec.dark() ? 0xffe6ede8 : Ui.INK, false));
              ui.space(list, 6);
              if (++count >= 40) break;
            }
          }
          if (count == 0) list.addView(ui.body(tr("当前页面未匹配到列表内容")));
        }
        if (state != null)
          state.setText(
              !available
                  ? tr("原页面不可读或包含敏感内容，请使用原应用")
                  : message.isEmpty() ? tr("已连接 · 显示原应用当前页面信息") : message);
        if (!available) {
          completedWidget = "";
          if (verifyButton != null) verifyButton.setVisibility(View.GONE);
        }
      }
    } catch (Exception e) {
      completedWidget = "";
      if (verifyButton != null) verifyButton.setVisibility(View.GONE);
      for (TextView value : values.values()) value.setText(tr("适配已失效"));
      for (LinearLayout list : collections.values()) {
        list.removeAllViews();
        list.addView(ui.body(tr("适配已失效")));
      }
      for (Button b : actions.values()) b.setEnabled(false);
      if (state != null) state.setText(e.getMessage());
    }
  }

  private void startAction(String id) {
    if (busy) return;
    try {
      assertCurrent();
      JSONObject widget = session.spec.widget(id);
      String input = inputs.containsKey(id) ? inputs.get(id).getText().toString() : "";
      if (input.length() > 500 || RebuildSpec.sensitive(input))
        throw new IllegalArgumentException(tr("此内容不适合通过重构界面输入，请使用原应用"));
      if (widget.optString("kind").equals("input") && input.trim().isEmpty())
        throw new IllegalArgumentException(tr("请先输入本次操作内容"));
      completedWidget = "";
      message = tr("执行中…");
      busy = true;
      for (Button b : actions.values()) b.setEnabled(false);
      if (verifyButton != null) verifyButton.setVisibility(View.GONE);
      final int token = generation;
      runStep(widget, 0, input, token, 0);
    } catch (Exception e) {
      busy = false;
      message = e.getMessage();
      refreshValues();
    }
  }

  private void runStep(JSONObject widget, int index, String input, int token, int retries) {
    if (token != generation || session == null) return;
    try {
      assertCurrent();
      JSONArray steps = widget.optJSONArray("steps");
      if (index == steps.length()) {
        busy = false;
        completedWidget = widget.optString("id");
        completedAt = SystemClock.uptimeMillis();
        message = tr("操作已发出，请核对原应用实际结果；尚未自动判定成功");
        render();
        return;
      }
      JSONObject step = steps.optJSONObject(index);
      try (Snapshot snap = snapshot()) {
        if (!snap.available || snap.sensitive || snap.foreignActive || snap.truncated)
          throw new IllegalStateException(tr("原页面不可操作，请返回原应用处理"));
        int found;
        try {
          if (!step.optString("page").equals(RebuildSpec.pageSignature(snap.data())))
            throw new IllegalArgumentException(tr("页面结构不匹配，请重新示范"));
          found = RebuildSpec.resolve(step.optJSONObject("selector"), snap.data());
        } catch (IllegalArgumentException e) {
          if (retries < 5
              && (e.getMessage().startsWith(tr("当前页面找不到"))
                  || e.getMessage().startsWith(tr("页面结构不匹配")))) {
            handler.postDelayed(() -> runStep(widget, index, input, token, retries + 1), 300);
            return;
          }
          throw e;
        }
        Node target = snap.nodes.get(found);
        JSONObject data = target.data;
        String op = step.optString("op");
        if (!data.optBoolean("enabled")
            || !data.optBoolean(op)
            || RebuildSpec.sensitive(data.optString("text") + " " + data.optString("description")))
          throw new IllegalStateException(tr("目标控件不可执行此操作"));
        int action =
            op.equals("click")
                ? AccessibilityNodeInfo.ACTION_CLICK
                : op.equals("input")
                    ? AccessibilityNodeInfo.ACTION_SET_TEXT
                    : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        Bundle args = new Bundle();
        if (op.equals("input"))
          args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, input);
        if (!target.nativeNode.performAction(action, args))
          throw new IllegalStateException(tr("原应用没有接受操作，请重新示范"));
      }
      handler.postDelayed(() -> runStep(widget, index + 1, input, token, 0), 450);
    } catch (Exception e) {
      busy = false;
      completedWidget = "";
      message = e.getMessage();
      render();
    }
  }

  private void confirmAttempt() {
    if (completedWidget.isEmpty() || busy) return;
    try {
      assertCurrent();
      try (Snapshot snapshot = snapshot()) {
        if (!snapshot.available || snapshot.sensitive)
          throw new IllegalStateException(tr("请先在原应用核对结果"));
      }
      if (SystemClock.uptimeMillis() - completedAt > 60000)
        throw new IllegalStateException(tr("本次试用已超过一分钟，请重新运行并核对"));
      store.verify(session.id, session.revision, completedWidget);
      session = store.get(session.id);
      completedWidget = "";
      message = tr("已记录你的本机试用确认；不代表其他版本或设备兼容");
      render();
    } catch (Exception e) {
      error(e);
    }
  }

  private void clickListItem(JSONObject widget, JSONObject selector) {
    if (busy) return;
    try {
      assertCurrent();
      completedWidget = "";
      try (Snapshot snap = snapshot()) {
        if (!snap.available
            || snap.sensitive
            || snap.foreignActive
            || snap.truncated
            || !widget.optString("page").equals(RebuildSpec.pageSignature(snap.data())))
          throw new IllegalStateException(tr("页面已变化，请重新读取列表"));
        AccessibilityNodeInfo target =
            AccessibilityNodeInfo.obtain(
                snap.nodes.get(RebuildSpec.resolve(selector, snap.data())).nativeNode);
        try {
          for (int depth = 0; depth < 5 && !target.isClickable(); depth++) {
            AccessibilityNodeInfo parent = target.getParent();
            target.recycle();
            target = parent;
            if (target == null) break;
          }
          if (target == null
              || !target.isClickable()
              || !target.isEnabled()
              || !session.spec.pkg().contentEquals(target.getPackageName())
              || RebuildSpec.sensitive(
                  string(target.getText()) + " " + string(target.getContentDescription())))
            throw new IllegalStateException(tr("这个列表项没有可用的点击操作，请使用原应用"));
          if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            throw new IllegalStateException(tr("原应用没有接受点击"));
        } finally {
          if (target != null) target.recycle();
        }
      }
      completedWidget = widget.optString("id");
      completedAt = SystemClock.uptimeMillis();
      message = tr("已点击列表项，请核对原应用实际结果");
      render();
    } catch (Exception e) {
      message = e.getMessage();
      refreshValues();
    }
  }

  private void returnToLiving() {
    String id = session == null ? null : session.id;
    stop();
    Intent intent =
        new Intent(this, RebuildActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("shell", id);
    startActivity(intent);
  }

  private void error(Exception e) {
    Toast.makeText(this, e.getMessage() == null ? tr("暂时没有完成") : e.getMessage(), Toast.LENGTH_LONG)
        .show();
  }
}

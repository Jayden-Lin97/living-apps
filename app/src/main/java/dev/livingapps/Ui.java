package dev.livingapps;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import dev.livingapps.core.AppSpec;

/** Native design system; no web surface or runtime UI dependency. */
final class Ui {
  static final int BG = 0xfff6f8f5,
      INK = 0xff1e2d26,
      MUTED = 0xff68756c,
      GREEN = 0xff1c3f35,
      LINE = 0xffe3e9e1,
      SOFT = 0xffeaf0e7,
      LIME = 0xffdbeeab;
  final Context c;

  Ui(Context c) {
    this.c = c;
  }

  int dp(float value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }

  LinearLayout col() {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.VERTICAL);
    v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    return v;
  }

  LinearLayout row() {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    return v;
  }

  TextView text(String value, int size, int color, boolean bold) {
    TextView v = new TextView(c);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setIncludeFontPadding(false);
    v.setLineSpacing(dp(3), 1);
    v.setFontFeatureSettings("kern");
    v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    return v;
  }

  TextView title(String value) {
    return text(value, 27, INK, true);
  }

  TextView body(String value) {
    return text(value, 13, MUTED, false);
  }

  TextView eyebrow(String value, int color) {
    TextView v = text(value, 10, color, true);
    v.setLetterSpacing(.14f);
    return v;
  }

  GradientDrawable shape(int color, int radius, boolean stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    if (stroke) d.setStroke(Math.max(1, dp(.7f)), LINE);
    return d;
  }

  int wash(int color) {
    return Color.rgb(
        (Color.red(color) + 255 * 9) / 10,
        (Color.green(color) + 255 * 9) / 10,
        (Color.blue(color) + 255 * 9) / 10);
  }

  void touch(View view, int radius, Runnable action) {
    view.setForeground(
        new RippleDrawable(
            ColorStateList.valueOf(0x18396850), null, shape(Color.WHITE, radius, false)));
    view.setClipToOutline(true);
    view.setFocusable(true);
    view.setOnClickListener(v -> action.run());
  }

  void space(LinearLayout parent, int height) {
    parent.addView(new Space(c), new LinearLayout.LayoutParams(1, dp(height)));
  }

  void gap(LinearLayout parent, int width) {
    parent.addView(new Space(c), new LinearLayout.LayoutParams(dp(width), 1));
  }

  LinearLayout card() {
    LinearLayout v = col();
    v.setPadding(dp(18), dp(18), dp(18), dp(18));
    v.setBackground(shape(Color.WHITE, 22, true));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(12);
    v.setLayoutParams(p);
    return v;
  }

  Button button(String label, boolean primary, Runnable action) {
    Button b = new Button(c);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setMinHeight(dp(48));
    b.setMinimumHeight(dp(48));
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setTextColor(primary ? Color.WHITE : GREEN);
    b.setStateListAnimator(null);
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(primary ? 0x33ffffff : 0x18396850),
            shape(primary ? GREEN : Color.WHITE, 15, !primary),
            shape(Color.WHITE, 15, false)));
    b.setPadding(dp(14), dp(12), dp(14), dp(12));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  Button action(String label, String icon, boolean primary, Runnable action) {
    Button b = button(label, primary, action);
    Drawable d = drawable(icon, primary ? Color.WHITE : GREEN);
    d.setBounds(0, 0, dp(18), dp(18));
    b.setCompoundDrawablesRelative(d, null, null, null);
    b.setCompoundDrawablePadding(dp(9));
    return b;
  }

  Button chip(String label, Runnable action) {
    Button b = button(label, false, action);
    b.setTextSize(12);
    b.setMinHeight(dp(40));
    b.setMinimumHeight(dp(40));
    b.setPadding(dp(14), dp(8), dp(14), dp(8));
    return b;
  }

  void weighted(LinearLayout row, View view) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.setMargins(dp(3), 0, dp(3), 0);
    row.addView(view, p);
  }

  ImageView icon(String name, int color, int size) {
    ImageView v = new ImageView(c);
    v.setImageDrawable(drawable(name, color));
    v.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
    v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return v;
  }

  FrameLayout badge(String name, int color, int size) {
    FrameLayout box = new FrameLayout(c);
    box.setBackground(shape(wash(color), 15, false));
    int s = size > 42 ? 25 : 21;
    if (name.startsWith("glyph:")) {
      TextView glyph = text(name.substring(6), s, color, true);
      glyph.setGravity(Gravity.CENTER);
      box.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
    } else {
      box.addView(icon(name, color, s), new FrameLayout.LayoutParams(dp(s), dp(s), Gravity.CENTER));
    }
    box.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
    return box;
  }

  ImageButton iconButton(String name, String description, Runnable action) {
    ImageButton b = new ImageButton(c);
    b.setImageDrawable(drawable(name, INK));
    b.setPadding(dp(13), dp(13), dp(13), dp(13));
    b.setContentDescription(description);
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x18396850),
            shape(Color.TRANSPARENT, 16, false),
            shape(Color.WHITE, 16, false)));
    b.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  LinearLayout navigationItem(String name, String label, boolean selected, Runnable action) {
    LinearLayout item = col();
    item.setGravity(Gravity.CENTER);
    item.setPadding(dp(4), dp(8), dp(4), dp(8));
    FrameLayout badge = badge(name, selected ? GREEN : MUTED, 40);
    badge.setBackground(shape(selected ? SOFT : Color.TRANSPARENT, 13, false));
    item.addView(badge, new LinearLayout.LayoutParams(dp(52), dp(30)));
    space(item, 5);
    TextView caption = text(label, 11, selected ? GREEN : MUTED, selected);
    caption.setGravity(Gravity.CENTER);
    item.addView(caption);
    item.setTag("nav-" + label);
    item.setSelected(selected);
    touch(item, 18, action);
    return item;
  }

  LinearLayout actionRow(String name, String title, String subtitle, Runnable action) {
    LinearLayout row = row();
    row.setPadding(0, dp(12), 0, dp(12));
    row.setMinimumHeight(dp(56));
    row.addView(badge(name, GREEN, 38));
    gap(row, 12);
    LinearLayout labels = col();
    labels.addView(text(title, 14, INK, true));
    if (subtitle != null) {
      space(labels, 4);
      labels.addView(body(subtitle));
    }
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    gap(row, 6);
    row.addView(icon("chevron-right", MUTED, 17));
    touch(row, 12, action);
    return row;
  }

  void divider(LinearLayout parent) {
    View line = new View(c);
    line.setBackgroundColor(LINE);
    parent.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(.6f))));
  }

  LinearLayout empty(String name, String title, String description) {
    LinearLayout v = card();
    v.setGravity(Gravity.CENTER);
    v.setPadding(dp(20), dp(24), dp(20), dp(24));
    v.addView(badge(name, GREEN, 48));
    space(v, 14);
    TextView heading = text(title, 16, INK, true);
    heading.setGravity(Gravity.CENTER);
    v.addView(heading);
    space(v, 7);
    TextView detail = body(description);
    detail.setGravity(Gravity.CENTER);
    v.addView(detail);
    return v;
  }

  EditText input(String hint, boolean multiline) {
    EditText v = new EditText(c);
    v.setHint(hint);
    v.setTextSize(15);
    v.setTextColor(INK);
    v.setHintTextColor(MUTED);
    v.setPadding(dp(15), dp(14), dp(15), dp(14));
    v.setBackground(shape(Color.WHITE, 15, true));
    v.setSingleLine(!multiline);
    v.setGravity(Gravity.TOP | Gravity.START);
    v.setLineSpacing(dp(5), 1);
    if (multiline) {
      v.setMinLines(5);
      v.setInputType(
          InputType.TYPE_CLASS_TEXT
              | InputType.TYPE_TEXT_FLAG_MULTI_LINE
              | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }
    return v;
  }

  TextView tag(String value) {
    TextView v = text(value, 10, GREEN, true);
    v.setPadding(dp(9), dp(5), dp(9), dp(5));
    v.setBackground(shape(SOFT, 8, false));
    return v;
  }

  Spinner spinner(String[] values, int selected) {
    Spinner spinner = new Spinner(c);
    ArrayAdapter<String> a =
        new ArrayAdapter<String>(c, android.R.layout.simple_spinner_dropdown_item, values) {
          @Override
          public View getView(int position, View convert, ViewGroup parent) {
            TextView t = (TextView) super.getView(position, convert, parent);
            t.setTextSize(15);
            t.setTextColor(INK);
            t.setPadding(dp(12), dp(10), dp(12), dp(10));
            return t;
          }
        };
    spinner.setAdapter(a);
    spinner.setSelection(Math.max(0, selected));
    spinner.setBackgroundTintList(ColorStateList.valueOf(LINE));
    spinner.setMinimumHeight(dp(48));
    return spinner;
  }

  Switch toggle(String label, boolean checked) {
    Switch v = new Switch(c);
    v.setText(label);
    v.setTextSize(14);
    v.setTextColor(INK);
    v.setPadding(0, dp(12), 0, dp(12));
    v.setSwitchPadding(dp(12));
    int[][] states = {new int[] {android.R.attr.state_checked}, new int[] {}};
    v.setThumbTintList(new ColorStateList(states, new int[] {GREEN, Color.WHITE}));
    v.setTrackTintList(new ColorStateList(states, new int[] {0xffc5d9b9, LINE}));
    v.setChecked(checked);
    return v;
  }

  void styleDialog(AlertDialog dialog) {
    if (dialog.getWindow() != null)
      dialog.getWindow().setBackgroundDrawable(shape(Color.WHITE, 26, false));
    for (int id :
        new int[] {
          AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL
        }) {
      Button b = dialog.getButton(id);
      if (b != null) {
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(id == AlertDialog.BUTTON_NEUTRAL ? 0xffa65e51 : GREEN);
      }
    }
  }

  String appIcon(AppSpec spec) {
    if (!spec.icon().equals("◈") && !spec.icon().equals("◉") && !spec.icon().equals("▤"))
      return "glyph:" + spec.icon();
    if (spec.field("amount") != null) return "wallet";
    if (spec.field("volume") != null) return "droplets";
    if (spec.field("book") != null) return "book-open";
    return "notebook-pen";
  }

  /** Original local vector illustration, decorative only. */
  View artwork(boolean growth) {
    View view =
        new View(c) {
          final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
          final Path stem = new Path(), leaf = new Path(), small = new Path();

          @Override
          protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth() / 120f, getHeight() / 120f);
            canvas.save();
            canvas.translate((getWidth() - 120 * scale) / 2, (getHeight() - 120 * scale) / 2);
            canvas.scale(scale, scale);
            paint.setColor(0xff35594b);
            canvas.drawCircle(66, 61, 49, paint);
            paint.setColor(0xff456653);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(.7f);
            canvas.drawCircle(66, 61, 52, paint);
            paint.setStyle(Paint.Style.FILL);
            if (growth) {
              paint.setColor(0xff63845d);
              canvas.drawRoundRect(35, 77, 95, 85, 4, 4, paint);
              paint.setColor(LIME);
              paint.setStyle(Paint.Style.STROKE);
              paint.setStrokeWidth(3);
              paint.setStrokeCap(Paint.Cap.ROUND);
              stem.reset();
              stem.moveTo(65, 78);
              stem.cubicTo(64, 53, 68, 47, 81, 33);
              canvas.drawPath(stem, paint);
              paint.setStyle(Paint.Style.FILL);
              leaf.reset();
              leaf.moveTo(68, 54);
              leaf.cubicTo(67, 29, 88, 28, 94, 29);
              leaf.cubicTo(97, 48, 85, 58, 68, 54);
              canvas.drawPath(leaf, paint);
              paint.setColor(0xff9ec28a);
              small.reset();
              small.moveTo(65, 65);
              small.cubicTo(42, 70, 35, 52, 38, 44);
              small.cubicTo(58, 41, 66, 50, 65, 65);
              canvas.drawPath(small, paint);
            } else {
              canvas.save();
              canvas.rotate(-14, 60, 62);
              paint.setColor(0xffa3bc8a);
              canvas.drawRoundRect(26, 23, 91, 98, 13, 13, paint);
              paint.setColor(LIME);
              canvas.drawRoundRect(36, 19, 102, 93, 13, 13, paint);
              paint.setColor(GREEN);
              canvas.drawRoundRect(47, 30, 72, 54, 7, 7, paint);
              paint.setColor(LIME);
              paint.setStrokeWidth(1.7f);
              canvas.drawLine(59, 36, 59, 48, paint);
              canvas.drawLine(53, 42, 65, 42, paint);
              paint.setColor(0xff91ad77);
              canvas.drawRoundRect(47, 65, 89, 69, 2, 2, paint);
              canvas.drawRoundRect(47, 76, 76, 80, 2, 2, paint);
              canvas.restore();
            }
            paint.setColor(LIME);
            canvas.drawCircle(103, 19, 3, paint);
            canvas.drawCircle(18, 87, 2, paint);
            paint.setStrokeWidth(1.6f);
            canvas.drawLine(22, 27, 22, 35, paint);
            canvas.drawLine(18, 31, 26, 31, paint);
            canvas.restore();
          }
        };
    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return view;
  }

  Drawable drawable(String name, int color) {
    int id;
    switch (name) {
      case "sprout":
        id = R.drawable.ic_sprout;
        break;
      case "settings-2":
        id = R.drawable.ic_settings_2;
        break;
      case "plus":
        id = R.drawable.ic_plus;
        break;
      case "arrow-left":
        id = R.drawable.ic_arrow_left;
        break;
      case "chevron-right":
        id = R.drawable.ic_chevron_right;
        break;
      case "wand-sparkles":
        id = R.drawable.ic_wand_sparkles;
        break;
      case "search":
        id = R.drawable.ic_search;
        break;
      case "shield-check":
        id = R.drawable.ic_shield_check;
        break;
      case "history":
        id = R.drawable.ic_history;
        break;
      case "ellipsis":
        id = R.drawable.ic_ellipsis;
        break;
      case "pen-line":
        id = R.drawable.ic_pen_line;
        break;
      case "book-open":
        id = R.drawable.ic_book_open;
        break;
      case "droplets":
        id = R.drawable.ic_droplets;
        break;
      case "wallet":
        id = R.drawable.ic_wallet;
        break;
      case "check":
        id = R.drawable.ic_check;
        break;
      case "arrow-up-right":
        id = R.drawable.ic_arrow_up_right;
        break;
      case "download":
        id = R.drawable.ic_download;
        break;
      case "upload":
        id = R.drawable.ic_upload;
        break;
      case "lock":
        id = R.drawable.ic_lock;
        break;
      case "leaf":
        id = R.drawable.ic_leaf;
        break;
      case "clock-3":
        id = R.drawable.ic_clock_3;
        break;
      case "chart-column":
        id = R.drawable.ic_chart_column;
        break;
      case "sliders-horizontal":
        id = R.drawable.ic_sliders_horizontal;
        break;
      case "circle-help":
        id = R.drawable.ic_circle_help;
        break;
      case "trash-2":
        id = R.drawable.ic_trash_2;
        break;
      case "notebook-pen":
        id = R.drawable.ic_notebook_pen;
        break;
      default:
        id = R.drawable.ic_layout_grid;
    }
    Drawable d = c.getDrawable(id).mutate();
    d.setTint(color);
    return d;
  }
}

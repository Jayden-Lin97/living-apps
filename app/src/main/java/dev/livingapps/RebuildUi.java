package dev.livingapps;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Presentation only: existing bindings supply every value and operation. */
final class RebuildUi {
  final Ui ui;
  final boolean dark;
  final int background, foreground, muted, surface, line, accent, accentText;

  RebuildUi(Ui ui, boolean dark) {
    this.ui = ui;
    this.dark = dark;
    background = dark ? 0xff101e1a : Ui.BG;
    foreground = dark ? 0xfff1f5ee : Ui.INK;
    muted = dark ? 0xffa7b9af : Ui.MUTED;
    surface = dark ? 0xff1d3028 : 0xffedf2e9;
    line = dark ? 0xff30463a : Ui.LINE;
    accent = dark ? Ui.LIME : Ui.GREEN;
    accentText = dark ? Ui.GREEN : Color.WHITE;
  }

  ImageButton icon(String name, String label, Runnable action) {
    ImageButton button = ui.iconButton(name, label, action);
    button.setImageTintList(ColorStateList.valueOf(foreground));
    return button;
  }

  Button button(String label, boolean primary, Runnable action) {
    Button button = ui.button(label, primary, action);
    button.setTextColor(primary ? accentText : foreground);
    button.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x224f8769),
            ui.shape(primary ? accent : surface, 18, false),
            ui.shape(Color.WHITE, 18, false)));
    return button;
  }

  LinearLayout section() {
    LinearLayout col = ui.col();
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.bottomMargin = ui.dp(22);
    col.setLayoutParams(params);
    return col;
  }

  Button item(String label, int index, Runnable action) {
    Button button = button(label, false, action);
    button.setTextColor(foreground);
    button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    button.setTextSize(15);
    button.setMinHeight(ui.dp(58));
    button.setMinimumHeight(ui.dp(58));
    button.setPadding(ui.dp(16), ui.dp(12), ui.dp(14), ui.dp(12));
    button.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x225b926e),
            ui.shape(Color.TRANSPARENT, 12, false),
            ui.shape(Color.WHITE, 12, false)));
    Drawable arrow = ui.drawable("chevron-right", muted);
    arrow.setBounds(0, 0, ui.dp(16), ui.dp(16));
    button.setCompoundDrawablesRelative(null, null, arrow, null);
    button.setCompoundDrawablePadding(ui.dp(10));
    button.setTag("rebuild-list-item:" + index);
    return button;
  }

  void divider(LinearLayout parent) {
    View divider = new View(ui.c);
    divider.setBackgroundColor(line);
    parent.addView(divider, new LinearLayout.LayoutParams(-1, ui.dp(1)));
  }

  View artwork(boolean compact) {
    View art =
        new View(ui.c) {
          private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
          private final Path clip = new Path(), contour = new Path();
          private final Shader gradient =
              new LinearGradient(
                  0,
                  184,
                  310,
                  0,
                  new int[] {0xff193d37, 0xff3b6454, 0xff9caa79},
                  null,
                  Shader.TileMode.CLAMP);

          @Override
          protected void onDraw(Canvas original) {
            super.onDraw(original);
            Canvas canvas = original;
            canvas.save();
            canvas.scale(getWidth() / 320f, getHeight() / 184f);
            clip.reset();
            clip.addRoundRect(0, 0, 320, 184, 22, 22, Path.Direction.CW);
            canvas.clipPath(clip);
            paint.setShader(gradient);
            canvas.drawRect(0, 0, 320, 184, paint);
            paint.setShader(null);
            paint.setColor(0xffded39a);
            canvas.drawCircle(232, 57, 34, paint);
            paint.setColor(0xffd5dc9e);
            paint.setAlpha(28);
            canvas.drawCircle(232, 57, 51, paint);
            paint.setAlpha(255);
            for (int i = 0; i < 9; i++) {
              contour.reset();
              float y = 72 + i * 15;
              contour.moveTo(-20, y + 55);
              contour.cubicTo(70, y - 25, 110, y + 90, 184, y + 13);
              contour.cubicTo(236, y - 42, 267, y + 3, 340, y - 25);
              paint.setStyle(Paint.Style.STROKE);
              paint.setStrokeWidth(i == 3 ? 1.8f : .8f);
              paint.setColor(i == 3 ? 0xffe4e6b6 : 0x66d8e5bb);
              canvas.drawPath(contour, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffe4e6b6);
            canvas.drawCircle(184, 130, 3, paint);
            canvas.restore();
          }
        };
    art.setLayoutParams(new LinearLayout.LayoutParams(-1, ui.dp(compact ? 142 : 184)));
    // Original project artwork, not an album cover or source-app data.
    art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return art;
  }
}

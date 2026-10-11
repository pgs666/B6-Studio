package dev.b6.studio;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Arrays;
import java.util.Locale;

public final class LedActivity extends StudioActivity {
  private int type = -1, period, brightness = 100;
  private int[] colors = Protocol.defaultLedColors();
  private boolean dirty, binding, known, brightnessSupported;
  private long revision = -1;
  private String modeSignature = "";
  private MaterialButton mode, apply, read, reset, add;
  private MaterialSwitch light;
  private TextView status, speedLabel, brightnessLabel;
  private LinearLayout colorCard, colorRow, speedCard, brightnessCard;
  private Slider speed, brightnessSlider;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean waiting;
  private final Runnable timeout =
      () -> {
        if (waiting) {
          waiting = false;
          status.setText("设备未回报灯效，请重新读取；部分旧固件可能不支持。 ");
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    toolbar("灯光与色彩");
    scrollContent();
    if (state != null) {
      type = state.getInt("type", -1);
      period = state.getInt("period");
      brightness = state.getInt("brightness", 100);
      int[] saved = state.getIntArray("colors");
      if (saved != null) colors = saved;
      dirty = state.getBoolean("dirty");
      known = state.getBoolean("known");
      brightnessSupported = state.getBoolean("brightnessSupported");
    }
    LinearLayout intro = card("散热器灯光");
    light = new MaterialSwitch(this);
    light.setText("开启灯光");
    light.setOnCheckedChangeListener(
        (b, checked) -> {
          if (!binding) {
            ble.send(new byte[] {0x10, (byte) (checked ? 255 : 0)});
            if (checked) request(0);
          }
        });
    intro.addView(light);
    status = text("连接后读取设备灯效", 13, false);
    intro.addView(status);
    space(intro, 12);
    mode = button("选择灯效", R.drawable.ic_palette);
    mode.setOnClickListener(v -> chooseMode());
    intro.addView(mode, new LinearLayout.LayoutParams(-1, -2));
    intro.addView(text("快速连按两次散热器电源键，也可切换灯效。变频旋转会随制冷功率自动变速，B6X 还会改变冷暖色。", 12, false));
    speedCard = card("灯效速度");
    speedLabel = text("", 13, false);
    speedCard.addView(speedLabel);
    speed = slider(speedCard, 0, 2, 1, 1);
    speed.setLabelFormatter(v -> new String[] {"慢速", "中速", "快速"}[Math.round(v)]);
    speed.addOnChangeListener(
        (s, v, user) -> {
          if (user) {
            period = Protocol.ledPeriods(type)[Math.round(v)];
            dirty = true;
            labels();
          }
        });
    brightnessCard = card("灯光亮度");
    brightnessLabel = text("", 13, false);
    brightnessCard.addView(brightnessLabel);
    brightnessSlider = slider(brightnessCard, 20, 100, 1, brightness);
    brightnessSlider.setLabelFormatter(v -> Math.round(v) + "%");
    brightnessSlider.addOnChangeListener(
        (s, v, user) -> {
          if (user) {
            brightness = Math.round(v);
            dirty = true;
            labels();
          }
        });
    colorCard = card("灯效颜色");
    colorCard.addView(text("点色块编辑，长按删除；保留 2–8 个颜色。", 12, false));
    space(colorCard, 10);
    colorRow = new LinearLayout(this);
    colorRow.setOrientation(LinearLayout.VERTICAL);
    colorCard.addView(colorRow);
    add = button("添加颜色", R.drawable.ic_palette);
    add.setOnClickListener(
        v -> {
          if (colors.length >= 8) return;
          int[] next = Arrays.copyOf(colors, colors.length + 1);
          next[next.length - 1] = 0xFFFFFF;
          colors = next;
          dirty = true;
          drawColors();
          pick(colors.length - 1);
        });
    colorCard.addView(add, new LinearLayout.LayoutParams(-1, -2));
    reset = button("恢复默认颜色", R.drawable.ic_refresh);
    reset.setOnClickListener(
        v -> {
          colors = Protocol.defaultLedColors();
          dirty = true;
          drawColors();
          labels();
        });
    colorCard.addView(reset, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout actions = card("应用灯效");
    apply = button("应用灯效", R.drawable.ic_lightbulb);
    apply.setOnClickListener(v -> apply());
    actions.addView(apply, new LinearLayout.LayoutParams(-1, -2));
    read = button("重新读取", R.drawable.ic_refresh);
    read.setOnClickListener(
        v -> {
          dirty = false;
          request(type < 0 ? 0 : type);
        });
    actions.addView(read, new LinearLayout.LayoutParams(-1, -2));
    actions.addView(text("先编辑，再应用。实际灯效以散热器回报为准。", 12, false));
    ble.changes.observe(this, v -> update());
    update();
    drawColors();
    finishInsets();
    if (ble.ready && !dirty) request(type < 0 ? 0 : type);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putInt("type", type);
    state.putInt("period", period);
    state.putInt("brightness", brightness);
    state.putIntArray("colors", colors);
    state.putBoolean("dirty", dirty);
    state.putBoolean("known", known);
    state.putBoolean("brightnessSupported", brightnessSupported);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onStop() {
    handler.removeCallbacks(timeout);
    super.onStop();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (waiting) handler.postDelayed(timeout, 4000);
  }

  private Slider slider(LinearLayout parent, int from, int to, int step, int value) {
    Slider s = new Slider(this);
    s.setValueFrom(from);
    s.setValueTo(to);
    s.setStepSize(step);
    s.setValue(value);
    parent.addView(s, new LinearLayout.LayoutParams(-1, dp(60)));
    return s;
  }

  private boolean smart() {
    return ble.telemetry != null && (ble.telemetry.mode == 0 || ble.telemetry.mode == 3);
  }

  private int[] allowed() {
    return ble.isB6X()
        ? (smart() ? new int[] {12, 3, 5, 6, 4} : new int[] {3, 5, 6, 4})
        : (smart() ? new int[] {12, 3, 5, 11} : new int[] {3, 5, 11});
  }

  private boolean allowed(int value) {
    for (int t : allowed()) if (t == value) return true;
    return false;
  }

  private void chooseMode() {
    int[] values = allowed();
    String[] names = new String[values.length];
    int selected = -1;
    for (int i = 0; i < values.length; i++) {
      names[i] = Protocol.ledName(values[i]);
      if (values[i] == type) selected = i;
    }
    new MaterialAlertDialogBuilder(this)
        .setTitle("灯效模式")
        .setSingleChoiceItems(
            names,
            selected,
            (dialog, index) -> {
              type = values[index];
              dirty = false;
              known = false;
              mode.setText(Protocol.ledName(type));
              request(type);
              update();
              dialog.dismiss();
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void request(int requested) {
    if (!ble.ready) return;
    waiting = true;
    status.setText("正在读取灯效…");
    handler.removeCallbacks(timeout);
    handler.postDelayed(timeout, 4000);
    ble.readLed(requested);
  }

  private void update() {
    binding = true;
    light.setEnabled(ble.ready && ble.telemetry != null);
    light.setChecked(ble.telemetry != null && ble.telemetry.light);
    mode.setEnabled(ble.ready && ble.telemetry != null);
    read.setEnabled(ble.ready);
    if (!ble.ready) {
      known = false;
      status.setText("请返回概览连接散热器");
    }
    if (revision != ble.ledRevision) {
      revision = ble.ledRevision;
      Protocol.LedConfig config = ble.led;
      if (config != null && (type < 0 || config.type == type) && !dirty) {
        type = config.type;
        period = config.period;
        brightnessSupported = config.supportsBrightness();
        brightness = brightnessSupported ? config.brightness : 100;
        if (config.colors.length >= 2) colors = config.colors.clone();
        known = true;
        waiting = false;
        handler.removeCallbacks(timeout);
        status.setText(
            "已读取「"
                + Protocol.ledName(type)
                + "」 · "
                + ble.deviceName
                + (!brightnessSupported && type != 12 ? "\n当前固件未提供亮度调节" : ""));
        int[] periods = Protocol.ledPeriods(type);
        for (int i = 0; i < 3; i++) if (periods[i] == period) speed.setValue(i);
        brightnessSlider.setValue(brightness);
        drawColors();
      }
    }
    String sig = ble.deviceName + smart() + type + known + brightnessSupported;
    if (!sig.equals(modeSignature)) {
      modeSignature = sig;
      mode.setText(type < 0 ? "选择灯效" : Protocol.ledName(type));
      ((View) speedCard.getParent())
          .setVisibility(known && type != 12 && type != 11 ? View.VISIBLE : View.GONE);
      ((View) brightnessCard.getParent())
          .setVisibility(known && type != 12 && brightnessSupported ? View.VISIBLE : View.GONE);
      ((View) colorCard.getParent())
          .setVisibility(
              known && ble.isB6X() && type != 12 && type != 3 ? View.VISIBLE : View.GONE);
    }
    apply.setEnabled(ble.ready && known && allowed(type));
    speed.setEnabled(ble.ready && known);
    brightnessSlider.setEnabled(ble.ready && known);
    add.setEnabled(ble.ready && known && colors.length < 8);
    reset.setEnabled(ble.ready && known);
    if (known && !allowed(type)) status.setText("当前散热模式不支持此灯效，请重新选择");
    labels();
    binding = false;
  }

  private void labels() {
    if (speedLabel == null) return;
    int[] values = Protocol.ledPeriods(type);
    String label = "设备当前速度";
    for (int i = 0; i < 3; i++) if (values[i] == period) label = new String[] {"慢速", "中速", "快速"}[i];
    speedLabel.setText(label);
    brightnessLabel.setText("亮度 · " + brightness + "%");
  }

  private void drawColors() {
    colorRow.removeAllViews();
    for (int row = 0; row < (colors.length + 3) / 4; row++) {
      LinearLayout line = new LinearLayout(this);
      for (int i = row * 4; i < Math.min(colors.length, row * 4 + 4); i++) {
        final int index = i;
        MaterialButton swatch =
            button(String.format(Locale.ROOT, "#%06X", colors[i] & 0xFFFFFF), 0);
        int c = colors[i] | 0xFF000000;
        swatch.setBackgroundTintList(ColorStateList.valueOf(c));
        swatch.setTextColor(
            (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) > 140000
                ? Color.BLACK
                : Color.WHITE);
        swatch.setTextSize(10);
        swatch.setMinWidth(0);
        swatch.setPadding(dp(2), 0, dp(2), 0);
        swatch.setContentDescription("颜色 " + (i + 1) + " " + swatch.getText());
        swatch.setOnClickListener(v -> pick(index));
        swatch.setOnLongClickListener(
            v -> {
              if (colors.length <= 2) {
                Snackbar.make(root, "至少需要两个颜色", Snackbar.LENGTH_SHORT).show();
                return true;
              }
              int[] next = new int[colors.length - 1];
              System.arraycopy(colors, 0, next, 0, index);
              System.arraycopy(colors, index + 1, next, index, colors.length - index - 1);
              colors = next;
              dirty = true;
              drawColors();
              return true;
            });
        line.addView(swatch, new LinearLayout.LayoutParams(0, dp(56), 1));
      }
      LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
      if (row > 0) rowParams.topMargin = dp(8);
      colorRow.addView(line, rowParams);
    }
    UiSpacing.apply(colorRow);
    add.setEnabled(ble.ready && known && colors.length < 8);
  }

  private void pick(int index) {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(24), dp(8), dp(24), 0);
    TextInputLayout field = new TextInputLayout(this);
    field.setHint("颜色 HEX · #RRGGBB");
    TextInputEditText input = new TextInputEditText(field.getContext());
    input.setSingleLine(true);
    input.setText(String.format(Locale.ROOT, "#%06X", colors[index] & 0xFFFFFF));
    field.addView(input);
    box.addView(field);
    View preview = new View(this);
    preview.setBackgroundColor(colors[index] | 0xFF000000);
    box.addView(preview, new LinearLayout.LayoutParams(-1, dp(32)));
    space(box, 8);
    float[] hsv = new float[3];
    Color.colorToHSV(colors[index] | 0xFF000000, hsv);
    box.addView(text("色相", 13, false));
    Slider hue = slider(box, 0, 359, 0, Math.min(359, Math.round(hsv[0])));
    box.addView(text("饱和度", 13, false));
    Slider sat = slider(box, 0, 100, 0, Math.round(hsv[1] * 100));
    box.addView(text("明度", 13, false));
    Slider val = slider(box, 0, 100, 0, Math.round(hsv[2] * 100));
    Slider.OnChangeListener change =
        (s, v, user) -> {
          if (user) {
            int c =
                Color.HSVToColor(
                    new float[] {hue.getValue(), sat.getValue() / 100, val.getValue() / 100});
            input.setText(String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF));
            preview.setBackgroundColor(c);
          }
        };
    hue.addOnChangeListener(change);
    sat.addOnChangeListener(change);
    val.addOnChangeListener(change);
    androidx.appcompat.app.AlertDialog dialog =
        new MaterialAlertDialogBuilder(this)
            .setTitle("编辑颜色 " + (index + 1))
            .setView(box)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      String value =
                          input.getText() == null ? "" : input.getText().toString().trim();
                      if (!value.matches("#?[0-9a-fA-F]{6}")) {
                        field.setError("请输入 6 位颜色值");
                        return;
                      }
                      colors[index] = Integer.parseInt(value.replace("#", ""), 16);
                      dirty = true;
                      drawColors();
                      dialog.dismiss();
                    }));
    dialog.show();
  }

  private void apply() {
    try {
      if (!allowed(type)) {
        Snackbar.make(root, "请先选择支持的灯效", Snackbar.LENGTH_SHORT).show();
        return;
      }
      if (ble.setLed(
          type,
          type == 12 ? 0 : period,
          type == 12 || !brightnessSupported ? 0 : brightness,
          type == 12 ? null : colors)) {
        dirty = false;
        waiting = true;
        status.setText("已发送，等待设备回报…");
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, 4000);
      }
    } catch (IllegalArgumentException e) {
      Snackbar.make(root, e.getMessage(), Snackbar.LENGTH_LONG).show();
    }
  }
}

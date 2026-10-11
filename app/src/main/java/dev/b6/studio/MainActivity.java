package dev.b6.studio;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.ScanResult;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.*;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import java.util.*;

public final class MainActivity extends AppCompatActivity {
  private BleController ble;
  private LinearLayout shell, body;
  private TextView connectionLine,
      mainValue,
      hotValue,
      rpmValue,
      coldValue,
      deviceLine,
      deviceDetails,
      freshness;
  private TextView targetLabel, windLabel, coldLabel, controlActual, modeHint;
  private LinearLayout deviceList;
  private MaterialButton connectButton, applyButton, experimentButton, coldProtectionButton;
  private MaterialSwitch lightSwitch, expertSwitch;
  private Slider targetSlider, windSlider, coldSlider;
  private MaterialButtonToggleGroup modes;
  private final ArrayList<View> gated = new ArrayList<>();
  private int page = 0;
  private boolean binding;
  private String shownError = "";
  private String deviceSignature = "";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable tick =
      new Runnable() {
        public void run() {
          updateFreshness();
          handler.postDelayed(this, 1000);
        }
      };

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private int color(int attr) {
    return MaterialColors.getColor(shell, attr);
  }

  private int surface() {
    return color(com.google.android.material.R.attr.colorSurface);
  }

  private int primary() {
    return color(com.google.android.material.R.attr.colorPrimary);
  }

  private int textColor() {
    return color(com.google.android.material.R.attr.colorOnSurface);
  }

  @Override
  public void onCreate(Bundle state) {
    com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
    super.onCreate(state);
    ble = ((StudioApplication) getApplication()).controller();
    if (state != null) page = Math.min(2, state.getInt("page", 0));
    WindowCompat.enableEdgeToEdge(getWindow());
    shell = new LinearLayout(this);
    shell.setOrientation(LinearLayout.VERTICAL);
    shell.setBackgroundColor(surface());
    setContentView(shell);
    ViewCompat.setOnApplyWindowInsetsListener(
        shell,
        (v, i) -> {
          androidx.core.graphics.Insets b =
              i.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          v.setPadding(b.left, b.top, b.right, 0);
          // The root owns top/cutout/side insets; Material BottomNavigationView owns the bottom.
          // Forward only the remaining bottom inset, so it is never applied twice.
          return new WindowInsetsCompat.Builder(i)
              .setInsets(
                  WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout(),
                  androidx.core.graphics.Insets.NONE)
              .setInsets(
                  WindowInsetsCompat.Type.navigationBars(),
                  androidx.core.graphics.Insets.of(0, 0, 0, b.bottom))
              .build();
        });
    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(24), dp(12), dp(16), dp(6));
    LinearLayout titles = new LinearLayout(this);
    titles.setOrientation(LinearLayout.VERTICAL);
    TextView title = text("B6 Studio", 25, true);
    titles.addView(title);
    connectionLine = text("散热器控制中心", 12, false);
    titles.addView(connectionLine);
    top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
    shell.addView(top);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(20), dp(16), dp(20), dp(24));
    scroll.addView(body);
    shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    BottomNavigationView nav = new BottomNavigationView(this);
    nav.setBackgroundColor(surface());
    nav.getMenu().add(0, R.id.nav_overview, 0, "概览").setIcon(R.drawable.ic_dashboard);
    nav.getMenu().add(0, R.id.nav_controls, 1, "控制").setIcon(R.drawable.ic_tune);
    nav.getMenu().add(0, R.id.nav_about, 2, "关于").setIcon(R.drawable.ic_info);
    nav.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
    nav.setSelectedItemId(
        page == 0 ? R.id.nav_overview : page == 1 ? R.id.nav_controls : R.id.nav_about);
    nav.setOnItemSelectedListener(
        item -> {
          page =
              item.getItemId() == R.id.nav_overview
                  ? 0
                  : item.getItemId() == R.id.nav_controls ? 1 : 2;
          render();
          return true;
        });
    shell.addView(nav);
    ble.changes.observe(this, v -> update());
    render();
    ViewCompat.requestApplyInsets(shell);
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler.post(tick);
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(tick);
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putInt("page", page);
    super.onSaveInstanceState(state);
  }

  private TextView text(String s, int size, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(textColor());
    t.setFontFeatureSettings("tnum");
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  private void space(LinearLayout l, int size) {
    View s = new View(this);
    l.addView(s, new LinearLayout.LayoutParams(1, dp(size)));
  }

  private LinearLayout card(boolean hero) {
    MaterialCardView c = new MaterialCardView(this);
    c.setRadius(dp(hero ? 28 : 24));
    c.setCardElevation(0);
    c.setStrokeWidth(0);
    c.setCardBackgroundColor(
        hero
            ? color(com.google.android.material.R.attr.colorPrimaryContainer)
            : color(com.google.android.material.R.attr.colorSurfaceContainerLow));
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(20), dp(20), dp(20), dp(20));
    c.addView(content);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(14);
    body.addView(c, p);
    return content;
  }

  private void heading(LinearLayout l, String title, int icon) {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    ImageView image = new ImageView(this);
    image.setImageResource(icon);
    image.setImageTintList(android.content.res.ColorStateList.valueOf(primary()));
    row.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));
    TextView t = text(title, 15, true);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
    p.leftMargin = dp(10);
    row.addView(t, p);
    l.addView(row);
    space(l, 12);
  }

  private MaterialButton button(String label, int icon, boolean tonal) {
    MaterialButton b =
        new MaterialButton(
            this,
            null,
            tonal
                ? com.google.android.material.R.attr.materialButtonOutlinedStyle
                : com.google.android.material.R.attr.materialButtonStyle);
    b.setText(label);
    b.setAllCaps(false);
    b.setCornerRadius(dp(24));
    b.setMinHeight(dp(48));
    if (icon != 0) {
      b.setIconResource(icon);
      b.setIconSize(dp(20));
    }
    return b;
  }

  private void render() {
    body.removeAllViews();
    gated.clear();
    mainValue =
        hotValue =
            rpmValue = coldValue = deviceLine = deviceDetails = freshness = controlActual = null;
    deviceList = null;
    connectButton = applyButton = experimentButton = coldProtectionButton = null;
    lightSwitch = expertSwitch = null;
    targetSlider = windSlider = coldSlider = null;
    modes = null;
    deviceSignature = "";
    if (page == 0) overview();
    else if (page == 1) controls();
    else about();
    update();
    UiSpacing.apply(body);
  }

  private void overview() {
    LinearLayout hero = card(true);
    heading(hero, "冷面温度", R.drawable.ic_ac_unit);
    mainValue = text("—", 62, true);
    mainValue.setTextColor(color(com.google.android.material.R.attr.colorOnPrimaryContainer));
    hero.addView(mainValue);
    freshness = text("连接后显示设备实时读数", 12, false);
    hero.addView(freshness);
    LinearLayout metrics = card(false);
    heading(metrics, "运行状态", R.drawable.ic_speed);
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    hotValue = metric(row, "热面温度", "—");
    rpmValue = metric(row, "风扇转速", "—");
    coldValue = metric(row, "制冷强度", "—");
    metrics.addView(row);
    space(metrics, 12);
    controlActual = text("等待设备状态", 12, false);
    metrics.addView(controlActual);
    LinearLayout device = card(false);
    heading(device, "我的散热器", R.drawable.ic_bluetooth);
    deviceLine = text("B6 / B6X", 20, true);
    device.addView(deviceLine);
    space(device, 6);
    deviceDetails = text("支持飞智 B6 与 B6X，使用蓝牙直连。", 13, false);
    device.addView(deviceDetails);
    space(device, 12);
    connectButton = button("寻找散热器", R.drawable.ic_bluetooth, false);
    connectButton.setOnClickListener(v -> connectAction());
    device.addView(connectButton, new LinearLayout.LayoutParams(-1, -2));
    deviceList = new LinearLayout(this);
    deviceList.setOrientation(LinearLayout.VERTICAL);
    device.addView(deviceList);
    TextView note = text("从「控制」调整温控、风扇和灯光。", 13, false);
    note.setPadding(dp(6), dp(6), 0, 0);
    body.addView(note);
  }

  private TextView metric(LinearLayout row, String label, String value) {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(text(label, 12, false));
    space(box, 8);
    TextView v = text(value, 22, true);
    box.addView(v);
    row.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
    return v;
  }

  private void controls() {
    TextView title = text("调到刚刚好", 28, true);
    body.addView(title);
    space(body, 6);
    body.addView(text("调整参数后点「应用设置」发送到散热器。", 13, false));
    space(body, 18);
    LinearLayout c = card(false);
    heading(c, "运行模式", R.drawable.ic_tune);
    modes = new MaterialButtonToggleGroup(this);
    modes.setSingleSelection(true);
    modes.setSelectionRequired(true);
    android.graphics.drawable.GradientDrawable segmentBackground =
        new android.graphics.drawable.GradientDrawable();
    segmentBackground.setColor(color(com.google.android.material.R.attr.colorSurfaceVariant));
    segmentBackground.setCornerRadius(dp(24));
    modes.setBackground(segmentBackground);
    modes.setClipToOutline(true);
    String[] labels = {"智能温控", "固定功率"};
    for (int i = 0; i < 2; i++) {
      MaterialButton b = button(labels[i], 0, true);
      b.setId(i == 0 ? 100 : 102);
      b.setStrokeWidth(0);
      b.setInsetTop(0);
      b.setInsetBottom(0);
      b.setMinWidth(0);
      b.setPadding(dp(8), 0, dp(8), 0);
      modes.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
    }
    c.addView(modes);
    modes.check(ble.draftMode == 0 ? 100 : 102);
    modes.addOnButtonCheckedListener(
        (g, id, checked) -> {
          if (checked && !binding) {
            ble.draftMode = id - 100;
            ble.draftDirty = true;
            update();
          }
        });
    space(c, 12);
    modeHint = text("", 13, false);
    c.addView(modeHint);
    space(c, 12);
    controlActual = text("等待设备状态", 12, false);
    c.addView(controlActual);
    expertSwitch = new MaterialSwitch(this);
    expertSwitch.setText("专家参数");
    expertSwitch.setTextColor(textColor());
    expertSwitch.setOnCheckedChangeListener(
        (b, checked) -> {
          if (!binding) {
            ble.draftCustom = checked ? 1 : 0;
            ble.draftDirty = true;
          }
        });
    c.addView(expertSwitch);
    gated.add(expertSwitch);
    LinearLayout sliders = card(false);
    heading(sliders, "温控与动力", R.drawable.ic_thermostat);
    targetLabel = text("目标温度", 15, true);
    sliders.addView(targetLabel);
    targetSlider = slider(sliders, 5, 35, 1, ble.draftTemp);
    targetSlider.addOnChangeListener(
        (s, v, u) -> {
          if (u) {
            ble.draftTemp = Math.round(v);
            ble.draftDirty = true;
            updateLabels();
          }
        });
    space(sliders, 8);
    windLabel = text("风扇设定", 15, true);
    sliders.addView(windLabel);
    windSlider = slider(sliders, 2000, fanMax(), 1, ble.draftWind);
    windSlider.addOnChangeListener(
        (s, v, u) -> {
          if (u) {
            ble.draftWind = Math.round(v);
            ble.draftDirty = true;
            updateLabels();
          }
        });
    space(sliders, 8);
    coldLabel = text("制冷强度", 15, true);
    sliders.addView(coldLabel);
    coldSlider = slider(sliders, 0, 255, 1, ble.draftCold);
    coldSlider.addOnChangeListener(
        (s, v, u) -> {
          if (u) {
            ble.draftCold = Math.round(v);
            ble.draftDirty = true;
            updateLabels();
          }
        });
    applyButton = button("应用设置", R.drawable.ic_tune, false);
    applyButton.setOnClickListener(v -> apply());
    sliders.addView(applyButton, new LinearLayout.LayoutParams(-1, -2));
    gated.add(applyButton);
    LinearLayout lights = card(false);
    heading(lights, "灯光", R.drawable.ic_lightbulb);
    lightSwitch = new MaterialSwitch(this);
    lightSwitch.setText("散热器灯光");
    lightSwitch.setTextColor(textColor());
    lightSwitch.setOnCheckedChangeListener(
        (b, checked) -> {
          if (!binding) {
            ble.send(new byte[] {0x10, (byte) (checked ? 255 : 0)});
            toast("灯光命令已发送，等待设备回报");
          }
        });
    lights.addView(lightSwitch);
    gated.add(lightSwitch);
    MaterialButton effects = button("灯效、颜色与亮度", R.drawable.ic_lightbulb, true);
    effects.setOnClickListener(v -> startActivity(new Intent(this, LedActivity.class)));
    lights.addView(effects, new LinearLayout.LayoutParams(-1, -2));
    gated.add(effects);
    LinearLayout protection = card(false);
    heading(protection, "保护机制", R.drawable.ic_info);
    protection.addView(text("过热保护由设备强制启用；过冷保护可在固定功率模式调整。", 13, false));
    coldProtectionButton = button("过冷保护", R.drawable.ic_thermostat, true);
    coldProtectionButton.setOnClickListener(v -> protection());
    protection.addView(coldProtectionButton, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout experiment = card(false);
    heading(experiment, "实验超频", R.drawable.ic_bolt);
    experiment.addView(text("保留原版实验命令。开启后功耗与温度可能升高，建议观察实时读数。", 13, false));
    space(experiment, 10);
    experimentButton = button("启用实验超频", R.drawable.ic_bolt, true);
    experimentButton.setOnClickListener(v -> experiment());
    experiment.addView(experimentButton, new LinearLayout.LayoutParams(-1, -2));
    gated.add(experimentButton);
    TextView hint = text("制冷值范围 0–255；目标温度范围 5–35°C。", 12, false);
    body.addView(hint);
    gated.add(modes);
    gated.add(windSlider);
    gated.add(coldSlider);
    updateLabels();
  }

  private Slider slider(LinearLayout l, int low, int high, int step, int value) {
    Slider s = new Slider(this);
    s.setValueFrom(low);
    s.setValueTo(high);
    s.setStepSize(step);
    s.setValue(Math.max(low, Math.min(high, value)));
    s.setLabelFormatter(v -> Integer.toString(Math.round(v)));
    l.addView(s, new LinearLayout.LayoutParams(-1, dp(60)));
    return s;
  }

  private int fanMax() {
    Protocol.Telemetry t = ble.telemetry;
    return t == null
        ? Math.max(8000, ble.draftWind)
        : Math.max(8000, Math.max(ble.draftWind, Math.max(t.wind, t.boostWind)));
  }

  private void updateLabels() {
    if (targetLabel == null) return;
    boolean smart = ble.draftMode == 0;
    expertSwitch.setVisibility(smart ? View.VISIBLE : View.GONE);
    targetLabel.setVisibility(smart ? View.VISIBLE : View.GONE);
    targetSlider.setVisibility(smart ? View.VISIBLE : View.GONE);
    modeHint.setText(smart ? "自动维持目标温度；风扇与制冷设定作为运行上限。" : "按固定风速和制冷强度运行，不设置目标温度。");
    targetLabel.setText("目标温度   " + ble.draftTemp + " °C");
    windLabel.setText("风扇设定   " + ble.draftWind + " RPM");
    coldLabel.setText("制冷强度   " + ble.draftCold + " / 255");
  }

  private void apply() {
    Protocol.Telemetry t = ble.telemetry;
    if (t == null) {
      toast("等待散热器回报状态");
      return;
    }
    if (ble.experimentKnown && ble.experimentValue > 0 && ble.draftCold >= 255) {
      toast("实验超频开启时，制冷强度不能超过 254");
      return;
    }
    int wind = t.wind, cold = t.cold, bw = t.boostWind, bc = t.boostCold;
    if (ble.draftMode == 2 || ble.draftMode == 0 && t.boostAvailable) {
      bw = ble.draftWind;
      bc = ble.draftCold;
    } else {
      wind = ble.draftWind;
      cold = ble.draftCold;
    }

    try {
      if (ble.send(
          Protocol.mode(ble.draftMode, ble.draftTemp, wind, cold, bw, bc, ble.draftCustom))) {
        if (ble.experimentKnown && ble.experimentValue > 0 && ble.draftCold < 255)
          ble.send(Protocol.experiment(true, ble.draftCold + 1, 0));
        toast("设置已发送，实际值以设备回报为准");
        ble.draftDirty = false;
      }
    } catch (IllegalArgumentException e) {
      toast(e.getMessage());
    }
  }

  private void experiment() {
    if (!ble.experimentKnown || ble.telemetry == null) {
      toast("等待实验模式状态");
      return;
    }
    boolean enable = ble.experimentValue == 0;
    if (!enable) {
      ble.send(Protocol.experiment(false, 0, 0));
      ble.send(new byte[] {0x52, 0});
      return;
    }
    int cold = ble.telemetry.realCold();
    if (cold >= 255) {
      toast("先把制冷强度降至 254 以下，再开启实验超频");
      return;
    }
    new MaterialAlertDialogBuilder(this)
        .setTitle("开启实验超频？")
        .setMessage("将发送原版实验命令。请保持供电稳定，并留意散热器热面温度。")
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "开启",
            (d, w) -> {
              ble.send(Protocol.experiment(true, cold + 1, 0));
              ble.send(new byte[] {0x52, 0});
            })
        .show();
  }

  private void about() {
    LinearLayout intro = card(true);
    heading(intro, "B6 Studio", R.drawable.ic_info);
    intro.addView(text("让散热器焕新", 26, true));
    space(intro, 8);
    intro.addView(text("版本 1.1.1 · Material Design 3\n支持飞智 B6 / B6X", 13, false));
    LinearLayout tools = card(false);
    heading(tools, "工具", R.drawable.ic_terminal);
    MaterialButton log = button("通信日志", R.drawable.ic_terminal, true);
    log.setOnClickListener(v -> startActivity(new Intent(this, LogsActivity.class)));
    tools.addView(log, new LinearLayout.LayoutParams(-1, -2));
    tools.addView(text("查看收发记录，支持暂停、复制和清空。日志仅缓存在内存，自动淘汰旧记录。", 12, false));
    space(tools, 12);
    MaterialButton refresh = button("重新读取设备状态", R.drawable.ic_refresh, true);
    refresh.setOnClickListener(
        v -> {
          ble.send(new byte[] {0x13});
          ble.send(new byte[] {0x52, 0});
          ble.readLed(0);
          toast("正在读取设备状态");
        });
    tools.addView(refresh, new LinearLayout.LayoutParams(-1, -2));
    gated.add(refresh);
    LinearLayout appearance = card(false);
    heading(appearance, "外观", R.drawable.ic_palette);
    MaterialButton theme = button("主题外观", R.drawable.ic_palette, true);
    theme.setOnClickListener(
        v -> {
          int current =
              getSharedPreferences("appearance", 0)
                  .getInt(
                      "night", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
          int selected =
              current == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                  ? 1
                  : current == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES ? 2 : 0;
          new MaterialAlertDialogBuilder(this)
              .setTitle("主题外观")
              .setSingleChoiceItems(
                  new String[] {"跟随系统", "浅色", "深色"},
                  selected,
                  (d, index) -> {
                    int chosen =
                        index == 0
                            ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                            : index == 1
                                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
                    getSharedPreferences("appearance", 0).edit().putInt("night", chosen).apply();
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(chosen);
                    d.dismiss();
                  })
              .setNegativeButton("取消", null)
              .show();
        });
    appearance.addView(theme, new LinearLayout.LayoutParams(-1, -2));
    appearance.addView(text("Android 12 及以上使用系统壁纸动态配色。", 12, false));
    LinearLayout credits = card(false);
    heading(credits, "关于应用", R.drawable.ic_info);
    credits.addView(
        text("独立重写的蓝牙控制工具。图标来自 Google Material Icons，使用 Apache License 2.0 许可。", 13, false));
    space(credits, 8);
    credits.addView(text("固件升级请使用飞智官方应用。", 12, false));
  }

  private void protection() {
    Protocol.Telemetry t = ble.telemetry;
    if (t == null) return;
    boolean enable = !t.coldProtection;
    if (enable) {
      ble.send(Protocol.coldProtection(true));
      return;
    }
    new MaterialAlertDialogBuilder(this)
        .setTitle("关闭过冷保护？")
        .setMessage("固定功率空载运行时，低温可能产生冷凝水。关闭后设备将不再通过此保护自动断电。")
        .setNegativeButton("取消", null)
        .setPositiveButton("关闭保护", (d, w) -> ble.send(Protocol.coldProtection(false)))
        .show();
  }

  private void connectAction() {
    if (ble.ready || ble.connecting) {
      new MaterialAlertDialogBuilder(this)
          .setTitle(ble.deviceName)
          .setMessage(ble.status)
          .setNegativeButton("取消", null)
          .setPositiveButton("断开连接", (d, w) -> ble.disconnect())
          .show();
      return;
    }
    if (!ble.permissions()) {
      String[] p =
          Build.VERSION.SDK_INT >= 31
              ? new String[] {
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
              }
              : new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
      requestPermissions(p, 42);
      return;
    }
    BluetoothAdapter a = ble.adapter();
    if (a == null) {
      toast("这台设备不支持蓝牙");
      return;
    }
    if (!a.isEnabled()) {
      try {
        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 43);
      } catch (SecurityException e) {
        toast("请在系统设置中开启蓝牙");
      }
      return;
    }
    if (Build.VERSION.SDK_INT <= 30) {
      android.location.LocationManager lm =
          getSystemService(android.location.LocationManager.class);
      boolean enabled =
          Build.VERSION.SDK_INT >= 28
              ? lm.isLocationEnabled()
              : lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                  || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
      if (!enabled) {
        toast("Android 11 及以下扫描需要开启位置服务");
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        return;
      }
    }
    if (ble.scanning) {
      ble.stopScan();
      ble.status = "扫描已停止";
      update();
      return;
    }
    page = 0;
    ((BottomNavigationView) shell.getChildAt(2)).setSelectedItemId(R.id.nav_overview);
    ble.scan();
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] p, int[] results) {
    super.onRequestPermissionsResult(code, p, results);
    if (code == 42) {
      if (ble.permissions()) connectAction();
      else
        new MaterialAlertDialogBuilder(this)
            .setTitle("需要附近设备权限")
            .setMessage("允许后才能寻找并连接散热器。可以在应用设置中开启。")
            .setNegativeButton("取消", null)
            .setPositiveButton(
                "应用设置",
                (d, w) ->
                    startActivity(
                        new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + getPackageName()))))
            .show();
    }
  }

  @Override
  protected void onActivityResult(int code, int result, Intent data) {
    super.onActivityResult(code, result, data);
    if (code == 43 && result == RESULT_OK) connectAction();
  }

  private void updateFreshness() {
    if (freshness == null) return;
    boolean live = ble.ready && ble.telemetry != null;
    long age = (SystemClock.elapsedRealtime() - ble.lastReading) / 1000;
    freshness.setText(live ? (age <= 3 ? "实时读数 · °C" : "上次更新 " + age + " 秒前 · °C") : "连接后显示设备实时读数");
  }

  private void update() {
    if (shell == null) return;
    binding = true;
    connectionLine.setText(ble.status);
    Protocol.Telemetry t = ble.telemetry;
    boolean live = ble.ready && t != null;
    if (mainValue != null) {
      mainValue.setText(live ? t.temperature() + "°" : "—");
      hotValue.setText(live ? t.hot + "°" : "—");
      rpmValue.setText(live ? Integer.toString(t.rpm) : "—");
      coldValue.setText(live ? Integer.toString(t.realCold()) : "—");
      long age = (SystemClock.elapsedRealtime() - ble.lastReading) / 1000;
      freshness.setText(
          live ? (age <= 3 ? "实时读数 · °C" : "上次更新 " + age + " 秒前 · °C") : "连接后显示设备实时读数");
      deviceLine.setText(ble.ready || ble.connecting ? ble.deviceName : "B6 / B6X");
      deviceDetails.setText(ble.ready ? ble.address + "\n固件 " + ble.firmware : ble.status);
      connectButton.setText(
          ble.ready ? "断开连接" : ble.connecting ? "取消连接" : ble.scanning ? "停止扫描" : "寻找散热器");
      drawDevices();
    }
    if (controlActual != null)
      controlActual.setText(
          live
              ? "当前模式：" + modeName(t.mode) + " · 风扇设定 " + t.realWind() + " · 制冷 " + t.realCold()
              : "等待设备状态");
    for (View v : gated) v.setEnabled(live);
    if (coldProtectionButton != null) {
      coldProtectionButton.setEnabled(live && (t.mode == 1 || t.mode == 2));
      coldProtectionButton.setText(
          live ? "过冷保护 · " + (t.coldProtection ? "已开启" : "已关闭") : "过冷保护 · 等待设备");
    }
    if (modes != null)
      for (int i = 0; i < modes.getChildCount(); i++) modes.getChildAt(i).setEnabled(live);
    if (targetSlider != null) {
      targetSlider.setEnabled(live && ble.draftMode == 0);
      if (!targetSlider.isPressed()) targetSlider.setValue(ble.draftTemp);
      if (!windSlider.isPressed()) {
        windSlider.setValueTo(fanMax());
        windSlider.setValue(ble.draftWind);
      }
      if (!coldSlider.isPressed()) coldSlider.setValue(ble.draftCold);
      modes.check(ble.draftMode == 0 ? 100 : 102);
      updateLabels();
      expertSwitch.setChecked(ble.draftCustom == 1);
      lightSwitch.setChecked(live && t.light);
      experimentButton.setEnabled(live && ble.experimentKnown);
      experimentButton.setText(
          ble.experimentKnown && ble.experimentValue != 0 ? "关闭实验超频（已开启）" : "启用实验超频");
    }
    binding = false;
    if (!ble.lastError.isEmpty() && !ble.lastError.equals(shownError)) {
      shownError = ble.lastError;
      toast(shownError);
    }
  }

  private String modeName(int mode) {
    return mode == 0 ? "智能温控" : mode == 1 ? "固定功率（标准）" : mode == 2 ? "固定功率" : "自定义智能";
  }

  private void drawDevices() {
    if (deviceList == null) return;
    String signature = ble.devices.keySet().toString() + ble.ready + ble.connecting;
    if (signature.equals(deviceSignature)) return;
    deviceSignature = signature;
    deviceList.removeAllViews();
    if (ble.ready || ble.connecting) return;
    for (ScanResult r : ble.devices.values()) {
      String name = r.getScanRecord() == null ? null : r.getScanRecord().getDeviceName();
      if (name == null) name = "Flydigi B6";
      MaterialButton b = button(name + "   " + r.getRssi() + " dBm", R.drawable.ic_bluetooth, true);
      b.setOnClickListener(v -> ble.connect(r));
      deviceList.addView(b, new LinearLayout.LayoutParams(-1, -2));
      TextView mac = text(r.getDevice().getAddress(), 11, false);
      mac.setPadding(dp(12), 0, 0, dp(8));
      deviceList.addView(mac);
    }
  }

  private void toast(String s) {
    Snackbar.make(shell, s, Snackbar.LENGTH_LONG).show();
  }
}

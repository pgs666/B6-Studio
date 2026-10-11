package dev.b6.studio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Wire format independently reconstructed from the supplied APK. */
public final class Protocol {
  private Protocol() {}

  public static byte[] mode(
      int mode, int target, int wind, int cold, int boostWind, int boostCold, int custom) {
    range(mode, 0, 2);
    range(target, 5, 35);
    range(wind, 0, 25599);
    range(boostWind, 0, 25599);
    range(cold, 0, 255);
    range(boostCold, 0, 255);
    range(custom, 0, 1);
    if (mode == 0)
      return new byte[] {
        0x21,
        (byte) target,
        (byte) cold,
        (byte) (wind / 100),
        (byte) boostCold,
        (byte) (boostWind / 100),
        (byte) (wind % 100),
        (byte) (boostWind % 100),
        (byte) custom
      };
    int w = mode == 1 ? wind : boostWind;
    return new byte[] {
      (byte) (mode == 1 ? 0x22 : 0x23),
      (byte) (mode == 1 ? cold : boostCold),
      (byte) (w / 100),
      (byte) (w % 100)
    };
  }

  public static byte[] experiment(boolean enabled, int cold, int wind) {
    range(cold, 0, 255);
    range(wind, 0, 6000);
    return new byte[] {0x52, 1, (byte) (enabled ? cold : 0), (byte) (enabled ? wind / 100 : 0)};
  }

  private static void range(int v, int min, int max) {
    if (v < min || v > max) throw new IllegalArgumentException("参数超出协议范围");
  }

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  public static String hex(byte[] data) {
    if (data.length == 0) return "";
    char[] out = new char[data.length * 3 - 1];
    for (int i = 0; i < data.length; i++) {
      int value = data[i] & 255, at = i * 3;
      out[at] = HEX[value >>> 4];
      out[at + 1] = HEX[value & 15];
      if (at + 2 < out.length) out[at + 2] = ' ';
    }
    return new String(out);
  }

  public static boolean ledType(int type) {
    return type == 3 || type == 4 || type == 5 || type == 6 || type == 11 || type == 12;
  }

  public static String ledName(int type) {
    switch (type) {
      case 3:
        return "流光";
      case 4:
        return "切换";
      case 5:
        return "呼吸";
      case 6:
        return "渐变";
      case 11:
        return "常亮";
      case 12:
        return "变频旋转";
      default:
        return "未知灯效";
    }
  }

  public static int[] ledPeriods(int type) {
    switch (type) {
      case 3:
        return new int[] {6, 4, 2};
      case 4:
      case 5:
        return new int[] {8, 5, 3};
      case 6:
        return new int[] {10, 8, 6};
      default:
        return new int[] {0, 0, 0};
    }
  }

  public static int[] defaultLedColors() {
    return new int[] {
      0xFF0000, 0xFF6A00, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0x9A00FF, 0xFF0080
    };
  }

  /** Null colors and zero period/brightness preserve firmware defaults (mode-only command). */
  public static List<byte[]> led(int type, int period, int brightness, int[] colors) {
    if (!ledType(type)) throw new IllegalArgumentException("不支持的 B6 灯效");
    range(period, 0, 255);
    range(brightness, 0, 100);
    if (brightness > 0 && brightness < 20) throw new IllegalArgumentException("亮度应为 20–100%");
    if (colors != null) range(colors.length, 2, 8);
    List<byte[]> result = new ArrayList<>();
    int count = colors == null ? 0 : colors.length;
    for (int packet = 0; packet < Math.max(1, (count + 3) / 4); packet++) {
      byte[] data = new byte[20];
      data[0] = 0x50;
      data[1] = (byte) type;
      data[2] = (byte) period;
      data[3] = (byte) count;
      data[4] = (byte) (colors == null ? 0 : packet + 1);
      for (int i = packet * 4; i < Math.min(count, packet * 4 + 4); i++) {
        int at = 5 + (i - packet * 4) * 3;
        data[at] = (byte) (colors[i] >> 16);
        data[at + 1] = (byte) (colors[i] >> 8);
        data[at + 2] = (byte) colors[i];
      }
      // Correct the official SDK's second-packet bug: brightness belongs in BOTH packets.
      data[17] = (byte) brightness;
      int sum = 0;
      for (int i = 0; i < 19; i++) sum += data[i] & 255;
      data[19] = (byte) sum;
      result.add(data);
    }
    result.add(new byte[] {0x51, (byte) type});
    return result;
  }

  public static byte[] coldProtection(boolean enabled) {
    return new byte[] {0x32, 0, (byte) (enabled ? 1 : 0)};
  }

  public static final class LedConfig {
    public final int type, period, brightness;
    public final int[] colors;

    public boolean supportsBrightness() {
      return brightness >= 20 && brightness <= 100;
    }

    public LedConfig(int type, int period, int brightness, int[] colors) {
      this.type = type;
      this.period = period;
      this.brightness = brightness;
      this.colors = colors.clone();
    }
  }

  /** Never publishes a partial, truncated, out-of-order or mismatched color list. */
  public static final class LedAssembler {
    private LedConfig first;
    private int count;

    public void reset() {
      first = null;
      count = 0;
    }

    public LedConfig accept(byte[] data) {
      if (data == null || data.length == 0 || data[0] != 0x51) return null;
      if (data.length < 18) {
        reset();
        return null;
      }
      int type = data[1] & 255,
          period = data[2] & 255,
          n = data[3] & 255,
          index = data[4] & 255,
          brightness = data[17] & 255;
      if (!ledType(type) || n > 8 || (index != 1 && index != 2)) {
        reset();
        return null;
      }
      int start = index == 1 ? 0 : 4, size = Math.min(4, n - start);
      if (size < 0) {
        reset();
        return null;
      }
      int[] colors = new int[size];
      for (int i = 0; i < size; i++) {
        int at = 5 + i * 3;
        colors[i] = ((data[at] & 255) << 16) | ((data[at + 1] & 255) << 8) | (data[at + 2] & 255);
      }
      if (index == 1) {
        reset();
        LedConfig c = new LedConfig(type, period, brightness, colors);
        if (n <= 4) return c;
        first = c;
        count = n;
        return null;
      }
      if (first == null || count != n || first.type != type || first.period != period) {
        reset();
        return null;
      }
      int[] all = Arrays.copyOf(first.colors, n);
      System.arraycopy(colors, 0, all, 4, size);
      // Older SDK sends brightness=0 in packet 2; packet 1 is authoritative.
      LedConfig complete = new LedConfig(type, period, first.brightness, all);
      reset();
      return complete;
    }
  }

  public static final class Telemetry {
    public int temp,
        decimal,
        hot,
        mode,
        target,
        wind,
        cold,
        boostWind,
        boostCold,
        rpm,
        windRun,
        coldRun;
    public boolean boostAvailable, light, coldProtection, hotProtection, coldProtectionActive;

    public static Telemetry decode(byte[] d) {
      if (d == null || d.length < 20 || d[0] != 0x13) return null;
      Telemetry t = new Telemetry();
      t.temp = d[2];
      t.decimal = d[3] & 255;
      t.mode = d[4] & 255;
      t.boostAvailable = (d[5] & 255) == 255;
      t.wind = (d[6] & 255) * 100 + (d[18] & 255);
      t.cold = d[7] & 255;
      t.light = (d[8] & 255) == 255;
      t.target = d[9] & 255;
      t.rpm = (d[10] & 255) | ((d[11] & 255) << 8);
      t.boostWind = (d[12] & 255) * 100 + (d[19] & 255);
      t.boostCold = d[13] & 255;
      t.windRun = d[14] & 255;
      t.coldRun = d[15] & 255;
      t.hot = d[16];
      t.coldProtection = (d[17] & 15) == 1;
      t.hotProtection = ((d[17] & 255) >> 4) == 1;
      t.coldProtectionActive = ((d[17] & 255) >> 4) == 2;
      return t;
    }

    public int realWind() {
      return (mode == 0 || mode == 3) && boostAvailable ? boostWind : wind;
    }

    public int realCold() {
      return (mode == 0 || mode == 3) && boostAvailable ? boostCold : cold;
    }

    public String temperature() {
      return (temp < 0 ? "−" : "") + Math.abs(temp) + "." + decimal;
    }
  }
}

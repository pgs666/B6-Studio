package dev.b6.studio;

import android.Manifest;
import android.app.Application;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.*;

@SuppressWarnings("MissingPermission")
public final class BleController extends AndroidViewModel {
  public static final UUID SERVICE = uuid("ffe0"),
      WRITE = uuid("ffe1"),
      READ = uuid("ffe4"),
      CCC = uuid("2902");
  public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
  public final LinkedHashMap<String, ScanResult> devices = new LinkedHashMap<>();
  public final LogStore logs = new LogStore();
  public final MutableLiveData<Long> logChanges = new MutableLiveData<>(0L);
  public boolean rawNotifications = true;
  public final LinkedHashMap<Integer, Protocol.LedConfig> ledConfigs = new LinkedHashMap<>();
  public Protocol.LedConfig led;
  public long ledRevision;
  private final Protocol.LedAssembler ledAssembler = new Protocol.LedAssembler();
  public String status = "尚未连接",
      deviceName = "B6 / B6X",
      address = "",
      firmware = "—",
      lastError = "";
  public boolean scanning, ready, connecting, experimentKnown;
  public int experimentValue;
  public long lastReading;
  public Protocol.Telemetry telemetry;
  public int draftMode = 0, draftTemp = 10, draftWind = 4000, draftCold = 100, draftCustom = 0;
  public boolean draftDirty = false;
  private final Handler main = new Handler(Looper.getMainLooper());
  private BluetoothLeScanner scanner;
  private BluetoothGatt gatt;
  private BluetoothGattCharacteristic writer;
  private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
  private boolean busy;
  private int writeType, revision;
  private final Runnable scanTimeout =
      () -> {
        stopScan();
        if (devices.isEmpty()) status = "未发现 B6，确认设备已通电";
        else status = "请选择要连接的设备";
        changed();
      };
  private final Runnable connectTimeout =
      () -> {
        if (!ready) {
          disconnect();
          fail("连接超时，请重试");
        }
      };
  private final Runnable writeTimeout =
      () -> {
        if (busy) {
          queue.clear();
          busy = false;
          fail("写入超时，请重新连接");
        }
      };

  public BleController(Application app) {
    super(app);
  }

  private static UUID uuid(String s) {
    return UUID.fromString("0000" + s + "-0000-1000-8000-00805f9b34fb");
  }

  public BluetoothAdapter adapter() {
    BluetoothManager m = getApplication().getSystemService(BluetoothManager.class);
    return m == null ? null : m.getAdapter();
  }

  public boolean permissions() {
    if (Build.VERSION.SDK_INT >= 31)
      return granted(Manifest.permission.BLUETOOTH_SCAN)
          && granted(Manifest.permission.BLUETOOTH_CONNECT);
    return granted(Manifest.permission.ACCESS_FINE_LOCATION);
  }

  private boolean granted(String p) {
    return getApplication().checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
  }

  private boolean changePending, logPending;
  private final Runnable publishChanges =
      () -> {
        changePending = false;
        changes.setValue(++revision);
      };
  private final Runnable publishLogs =
      () -> {
        logPending = false;
        logChanges.setValue(logs.revision());
      };

  private void changed() {
    if (!changePending) {
      changePending = true;
      main.postDelayed(publishChanges, 100);
    }
  }

  private void log(String text) {
    logs.add(text, System.currentTimeMillis());
    if (!text.startsWith("RX")) Log.i("B6Studio", text);
    if (!logPending) {
      logPending = true;
      main.postDelayed(publishLogs, 250);
    }
  }

  public void clearLogs() {
    logs.clear();
    if (!logPending) {
      logPending = true;
      main.postDelayed(publishLogs, 250);
    }
  }

  public boolean isB6X() {
    return deviceName.contains("B6X");
  }

  public boolean setLed(int type, int period, int brightness, int[] colors) {
    if (!Protocol.ledType(type) || (!isB6X() && (type == 4 || type == 6))) {
      fail("此型号不支持该灯效");
      return false;
    }
    if (type == 12 && (telemetry == null || (telemetry.mode != 0 && telemetry.mode != 3))) {
      fail("变频旋转仅在智能模式可用");
      return false;
    }
    java.util.List<byte[]> packets =
        Protocol.led(type, period, brightness, isB6X() && type != 3 && type != 12 ? colors : null);
    if (!ready || !permissions() || queue.size() + packets.size() > 40) {
      fail("连接不可用或命令队列已满");
      return false;
    }
    for (byte[] packet : packets) queue.addLast(packet);
    pump();
    return true;
  }

  public void readLed(int type) {
    ledAssembler.reset();
    send(new byte[] {0x51, (byte) type});
  }

  private void fail(String text) {
    lastError = text;
    status = text;
    log("错误 · " + text);
    changed();
  }

  public void scan() {
    if (!permissions()) {
      fail("请允许附近设备权限");
      return;
    }
    BluetoothAdapter a = adapter();
    if (a == null || !a.isEnabled()) {
      fail("请先开启蓝牙");
      return;
    }
    stopScan();
    devices.clear();
    scanner = a.getBluetoothLeScanner();
    if (scanner == null) {
      fail("蓝牙扫描不可用");
      return;
    }
    scanning = true;
    status = "正在寻找 B6 / B6X…";
    lastError = "";
    try {
      scanner.startScan(
          Collections.emptyList(),
          new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
          scanCallback);
      main.postDelayed(scanTimeout, 12000);
      log("开始 BLE 扫描");
      changed();
    } catch (SecurityException | IllegalStateException e) {
      scanning = false;
      fail("扫描失败：" + e.getMessage());
    }
  }

  public void stopScan() {
    main.removeCallbacks(scanTimeout);
    if (scanner != null && scanning)
      try {
        scanner.stopScan(scanCallback);
      } catch (SecurityException | IllegalStateException ignored) {
      }
    scanning = false;
  }

  private final ScanCallback scanCallback =
      new ScanCallback() {
        @Override
        public void onScanResult(int type, ScanResult result) {
          main.post(
              () -> {
                if (!scanning) return;
                String name =
                    result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                if (name == null)
                  try {
                    name = result.getDevice().getName();
                  } catch (SecurityException ignored) {
                  }
                // Match the actual B6 family, never attach to another Flydigi product.
                if (name == null
                    || !(name.equals("Flydigi B6")
                        || name.startsWith("Flydigi B6X")
                        || name.startsWith("Flydigi B6 "))) return;
                String mac = result.getDevice().getAddress();
                boolean fresh = !devices.containsKey(mac);
                devices.put(mac, result);
                if (fresh) {
                  log("发现 " + name + " · " + mac);
                  changed();
                }
              });
        }

        @Override
        public void onScanFailed(int code) {
          main.post(
              () -> {
                scanning = false;
                main.removeCallbacks(scanTimeout);
                fail("扫描失败，代码 " + code);
              });
        }
      };

  public void connect(ScanResult result) {
    if (!permissions()) {
      fail("缺少附近设备权限");
      return;
    }
    disconnect();
    stopScan();
    address = result.getDevice().getAddress();
    deviceName = result.getDevice().getName();
    if (deviceName == null) deviceName = "Flydigi B6";
    firmware = "—";
    telemetry = null;
    experimentKnown = false;
    led = null;
    ledConfigs.clear();
    ledAssembler.reset();
    ledRevision++;
    draftDirty = false;
    lastError = "";
    connecting = true;
    status = "正在连接 " + deviceName;
    log(status);
    changed();
    try {
      gatt =
          result
              .getDevice()
              .connectGatt(getApplication(), false, callback, BluetoothDevice.TRANSPORT_LE);
      main.postDelayed(connectTimeout, 20000);
    } catch (SecurityException | IllegalArgumentException e) {
      connecting = false;
      fail("连接失败：" + e.getMessage());
    }
  }

  private final BluetoothGattCallback callback =
      new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
          main.post(
              () -> {
                if (g != gatt) {
                  g.close();
                  return;
                }
                if (statusCode != BluetoothGatt.GATT_SUCCESS
                    || newState == BluetoothProfile.STATE_DISCONNECTED) {
                  disconnect();
                  fail("连接已断开 · GATT " + statusCode);
                  return;
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                  status = "正在发现蓝牙服务…";
                  changed();
                  if (!g.discoverServices()) {
                    disconnect();
                    fail("服务发现启动失败");
                  }
                }
              });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int code) {
          main.post(
              () -> {
                if (g != gatt) return;
                BluetoothGattService s = g.getService(SERVICE);
                if (code != 0 || s == null) {
                  disconnect();
                  fail("设备未提供 B6 服务");
                  return;
                }
                writer = s.getCharacteristic(WRITE);
                BluetoothGattCharacteristic reader = s.getCharacteristic(READ);
                if (writer == null || reader == null) {
                  disconnect();
                  fail("B6 控制通道不完整");
                  return;
                }
                writeType =
                    (writer.getProperties()
                                & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
                            != 0
                        ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                BluetoothGattDescriptor c = reader.getDescriptor(CCC);
                if (c == null || !g.setCharacteristicNotification(reader, true)) {
                  disconnect();
                  fail("无法订阅设备状态");
                  return;
                }
                byte[] value =
                    (reader.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                boolean ok;
                if (Build.VERSION.SDK_INT >= 33)
                  ok = g.writeDescriptor(c, value) == BluetoothStatusCodes.SUCCESS;
                else {
                  c.setValue(value);
                  ok = g.writeDescriptor(c);
                }
                log("服务 FFE0 · 写入 FFE1 · 通知 FFE4");
                status = "正在订阅实时状态…";
                changed();
                if (!ok) {
                  disconnect();
                  fail("订阅请求失败");
                }
              });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int code) {
          main.post(
              () -> {
                if (g != gatt) return;
                if (code != 0) {
                  disconnect();
                  fail("订阅失败 · GATT " + code);
                  return;
                }
                ready = true;
                connecting = false;
                main.removeCallbacks(connectTimeout);
                status = "已连接";
                log("通知订阅成功");
                changed();
                send(new byte[] {0x13});
                send(new byte[] {0x52, 0});
                readLed(0);
              });
        }

        @Override
        public void onCharacteristicChanged(
            BluetoothGatt g, BluetoothGattCharacteristic c, byte[] data) {
          receive(g, data.clone());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
          byte[] d = c.getValue();
          if (d != null) receive(g, d.clone());
        }

        @Override
        public void onCharacteristicWrite(
            BluetoothGatt g, BluetoothGattCharacteristic c, int code) {
          main.post(
              () -> {
                if (g != gatt || !busy) return;
                main.removeCallbacks(writeTimeout);
                busy = false;
                if (code != 0) {
                  queue.clear();
                  fail("写入失败 · GATT " + code);
                  return;
                }
                main.postDelayed(BleController.this::pump, 80);
              });
        }
      };

  private static final class Incoming {
    final BluetoothGatt gatt;
    final byte[] data;

    Incoming(BluetoothGatt g, byte[] d) {
      gatt = g;
      data = d;
    }
  }

  private final ArrayDeque<Incoming> incoming = new ArrayDeque<>();
  private boolean drainPending;
  private int dropped;

  private void receive(BluetoothGatt g, byte[] d) {
    if (d.length > 512) return;
    synchronized (incoming) {
      if (incoming.size() >= 128) {
        dropped++;
        return;
      }
      incoming.addLast(new Incoming(g, d));
      if (!drainPending) {
        drainPending = true;
        main.post(drainIncoming);
      }
    }
  }

  private final Runnable drainIncoming =
      new Runnable() {
        public void run() {
          for (int i = 0; i < 32; i++) {
            Incoming packet;
            synchronized (incoming) {
              packet = incoming.pollFirst();
              if (packet == null) {
                drainPending = false;
                break;
              }
            }
            receiveNow(packet.gatt, packet.data);
          }
          synchronized (incoming) {
            if (dropped > 0) {
              log("通知过密，丢弃 " + dropped + " 个包");
              dropped = 0;
            }
            if (drainPending) main.post(this);
          }
        }
      };

  private void receiveNow(BluetoothGatt g, byte[] d) {
    if (g != gatt || d.length == 0) return;
    if (rawNotifications) log("RX  " + Protocol.hex(d));
    if (d[0] == 0x13) {
      Protocol.Telemetry t = Protocol.Telemetry.decode(d);
      if (t == null) {
        log("忽略不完整状态包（" + d.length + " 字节）");
        changed();
        return;
      }
      telemetry = t;
      lastReading = SystemClock.elapsedRealtime();
      if (!draftDirty) {
        draftMode = t.mode == 3 ? 0 : Math.min(2, t.mode);
        draftCustom = t.mode == 3 ? 1 : 0;
        draftTemp = Math.max(5, Math.min(35, t.target));
        draftWind = Math.max(2000, t.realWind());
        draftCold = t.realCold();
      }
    } else if (d[0] == 0x51) {
      Protocol.LedConfig c = ledAssembler.accept(d);
      if (c != null) {
        led = c;
        ledConfigs.put(c.type, c);
        ledRevision++;
      }
    } else if (d[0] == 0x52 && d.length >= 4) {
      experimentValue = d[2] & 255;
      experimentKnown = true;
    } else if (d.length >= 5 && isVersion(d)) {
      firmware = new String(d, 0, 5, java.nio.charset.StandardCharsets.US_ASCII);
    }
    changed();
  }

  private boolean isVersion(byte[] d) {
    for (int i = 0; i < 5; i++) if (d[i] < 32 || d[i] > 126) return false;
    return true;
  }

  public boolean send(byte[] data) {
    if (!permissions()) {
      fail("附近设备权限已撤销");
      return false;
    }
    if (!ready) {
      fail("请先连接散热器");
      return false;
    }
    if (queue.size() >= 40) {
      fail("命令队列已满，请稍后重试");
      return false;
    }
    queue.addLast(data.clone());
    pump();
    return true;
  }

  private void pump() {
    if (busy || !ready || gatt == null || writer == null) return;
    byte[] data = queue.pollFirst();
    if (data == null) return;
    busy = true;
    boolean ok;
    try {
      if (Build.VERSION.SDK_INT >= 33)
        ok = gatt.writeCharacteristic(writer, data, writeType) == BluetoothStatusCodes.SUCCESS;
      else {
        writer.setWriteType(writeType);
        writer.setValue(data);
        ok = gatt.writeCharacteristic(writer);
      }
    } catch (SecurityException | IllegalStateException e) {
      ok = false;
    }
    if (!ok) {
      busy = false;
      queue.clear();
      fail("蓝牙写入请求未被接受");
      return;
    }
    log("TX  " + Protocol.hex(data));
    main.postDelayed(writeTimeout, 3000);
  }

  public void disconnect() {
    stopScan();
    main.removeCallbacks(connectTimeout);
    main.removeCallbacks(writeTimeout);
    synchronized (incoming) {
      incoming.clear();
      dropped = 0;
    }
    ledAssembler.reset();
    led = null;
    ledConfigs.clear();
    ledRevision++;
    queue.clear();
    busy = false;
    writer = null;
    ready = false;
    connecting = false;
    BluetoothGatt old = gatt;
    gatt = null;
    if (old != null)
      try {
        old.disconnect();
        old.close();
        log("关闭 GATT 连接");
      } catch (SecurityException ignored) {
      }
    telemetry = null;
    experimentKnown = false;
    status = "尚未连接";
    changed();
  }

  public String logText() {
    return logs.text();
  }

  @Override
  protected void onCleared() {
    disconnect();
    main.removeCallbacksAndMessages(null);
  }
}

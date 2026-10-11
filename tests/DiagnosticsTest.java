import dev.b6.studio.LogStore;
import dev.b6.studio.Protocol;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class DiagnosticsTest {
  private static int checks;

  private static void check(boolean value) {
    checks++;
    if (!value) throw new AssertionError("check " + checks);
  }

  private static void rejects(Runnable task) {
    try {
      task.run();
    } catch (IllegalArgumentException ok) {
      checks++;
      return;
    }
    throw new AssertionError("range accepted");
  }

  private static byte[] reply(byte[] packet) {
    byte[] data = packet.clone();
    data[0] = 0x51;
    return data;
  }

  public static void main(String[] args) {
    int[] colors = Protocol.defaultLedColors();
    List<byte[]> commands = Protocol.led(5, 8, 64, colors);
    check(commands.size() == 3);
    check(Arrays.equals(commands.get(2), new byte[] {0x51, 5}));
    for (int i = 0; i < 2; i++) {
      byte[] p = commands.get(i);
      check(
          p.length == 20
              && p[0] == 0x50
              && p[1] == 5
              && p[2] == 8
              && p[3] == 8
              && p[4] == i + 1
              && p[17] == 64);
      int sum = 0;
      for (int j = 0; j < 19; j++) sum += p[j] & 255;
      check((p[19] & 255) == (sum & 255));
    }
    // Captured SDK contract: red / orange / yellow / green in packet 1, cyan / blue / violet / pink
    // in packet 2.
    check(
        Protocol.hex(commands.get(0))
            .equals("50 05 08 08 01 FF 00 00 FF 6A 00 FF FF 00 00 FF 00 40 00 0B"));
    Protocol.LedAssembler parser = new Protocol.LedAssembler();
    byte[] first = reply(commands.get(0)), second = reply(commands.get(1));
    check(parser.accept(second) == null);
    check(parser.accept(first) == null);
    Protocol.LedConfig result = parser.accept(second);
    check(
        result != null
            && result.type == 5
            && result.period == 8
            && result.brightness == 64
            && Arrays.equals(result.colors, colors));
    check(result.supportsBrightness());
    check(!new Protocol.LedConfig(5, 5, 0, colors).supportsBrightness());
    check(parser.accept(second) == null);
    parser.accept(first);
    second[2] = 5;
    check(parser.accept(second) == null);
    second = reply(commands.get(1));
    parser.accept(first);
    parser.accept(Arrays.copyOf(first, 12));
    check(parser.accept(second) == null);
    parser.accept(first);
    second[17] = 0;
    result = parser.accept(second);
    check(result != null && result.brightness == 64);
    List<byte[]> two = Protocol.led(4, 5, 20, new int[] {0x123456, 0xABCDEF});
    result = parser.accept(reply(two.get(0)));
    check(result != null && result.colors.length == 2 && result.colors[1] == 0xABCDEF);
    byte[] mode = Protocol.led(3, 0, 0, null).get(0);
    check(mode[3] == 0 && mode[4] == 0 && mode[17] == 0);
    check(Arrays.equals(Protocol.coldProtection(true), new byte[] {0x32, 0, 1}));
    check(Arrays.equals(Protocol.coldProtection(false), new byte[] {0x32, 0, 0}));
    rejects(() -> Protocol.led(16, 3, 100, colors));
    rejects(() -> Protocol.led(5, 256, 100, colors));
    rejects(() -> Protocol.led(5, 3, 19, colors));
    rejects(() -> Protocol.led(5, 3, 100, new int[9]));
    Random random = new Random(6);
    for (int i = 0; i < 10000; i++) {
      byte[] d = new byte[random.nextInt(40)];
      random.nextBytes(d);
      if (d.length > 0) d[0] = 0x51;
      parser.accept(d);
    }
    checks++;
    LogStore logs = new LogStore();
    String longMessage = "x".repeat(2048);
    long started = System.nanoTime();
    for (int i = 0; i < 100000; i++) {
      logs.add(i + " " + longMessage, 1700000000000L + i);
      check(logs.size() <= LogStore.MAX_ENTRIES && logs.chars() <= LogStore.MAX_CHARS);
    }
    List<LogStore.Entry> snapshot = logs.snapshot();
    check(snapshot.size() > 0 && snapshot.size() <= 500);
    long id = snapshot.get(0).id;
    for (LogStore.Entry e : snapshot) check(e.text.length() <= LogStore.MAX_LINE);
    long revision = logs.revision();
    logs.clear();
    check(logs.size() == 0 && logs.chars() == 0 && logs.revision() > revision);
    logs.add("new", 0);
    check(
        logs.snapshot().get(0).id > snapshot.get(snapshot.size() - 1).id
            && snapshot.get(0).id == id);
    for (int i = 0; i < 1000; i++) logs.add("small", 0);
    check(logs.size() == 500);
    check(logs.text().split("\n").length == 500);
    System.out.println(
        "PASS: "
            + checks
            + " LED/log checks, 100k burst in "
            + ((System.nanoTime() - started) / 1000000)
            + "ms; buffer "
            + snapshot.size()
            + " rows for long lines");
  }
}

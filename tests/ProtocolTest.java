import dev.b6.studio.Protocol;
import java.util.Arrays;

public final class ProtocolTest {
  private static int checks;

  private static void packet(byte[] got, int... expected) {
    checks++;
    if (got.length != expected.length) throw new AssertionError("packet length");
    for (int i = 0; i < expected.length; i++)
      if ((got[i] & 255) != expected[i])
        throw new AssertionError("packet byte " + i + ": " + Protocol.hex(got));
  }

  private static void rejects(Runnable action) {
    checks++;
    try {
      action.run();
    } catch (IllegalArgumentException e) {
      return;
    }
    throw new AssertionError("out-of-range accepted");
  }

  private static void check(boolean condition) {
    checks++;
    if (!condition) throw new AssertionError("telemetry mismatch");
  }

  public static void main(String[] args) {
    // Golden packets from the original B6 controller, with decimal RPM split into quotient and
    // remainder.
    packet(Protocol.mode(0, 10, 4567, 100, 5789, 230, 0), 0x21, 10, 100, 45, 230, 57, 67, 89, 0);
    packet(Protocol.mode(1, 10, 4567, 100, 5789, 230, 0), 0x22, 100, 45, 67);
    packet(Protocol.mode(2, 10, 4567, 100, 5789, 230, 0), 0x23, 230, 57, 89);
    packet(Protocol.mode(0, 35, 2000, 0, 6000, 255, 1), 0x21, 35, 0, 20, 255, 60, 0, 0, 1);
    packet(Protocol.mode(2, 5, 7240, 247, 7240, 247, 0), 0x23, 247, 72, 40);
    packet(Protocol.experiment(true, 101, 0), 0x52, 1, 101, 0);
    packet(Protocol.experiment(true, 230, 5700), 0x52, 1, 230, 57);
    packet(Protocol.experiment(false, 230, 5700), 0x52, 1, 0, 0);
    rejects(() -> Protocol.mode(0, 10, 4567, 300, 5700, 230, 0));
    rejects(() -> Protocol.mode(0, 4, 4567, 100, 5700, 230, 0));
    rejects(() -> Protocol.mode(2, 10, 4567, 100, 25600, 230, 0));
    rejects(() -> Protocol.experiment(true, 256, 0));
    byte[] d = {
      0x13, 0, -3, 5, 0, (byte) 255, 40, 100, (byte) 255, 10, 0x34, 0x12, 57, (byte) 230, 80, 90,
      45, 0x11, 67, 89
    };
    Protocol.Telemetry t = Protocol.Telemetry.decode(d);
    check(t != null && t.temp == -3 && t.decimal == 5 && t.temperature().equals("−3.5"));
    check(t.rpm == 4660 && t.wind == 4067 && t.boostWind == 5789 && t.boostCold == 230);
    check(
        t.realWind() == 5789
            && t.realCold() == 230
            && t.light
            && t.coldProtection
            && t.hotProtection);
    check(Protocol.Telemetry.decode(Arrays.copyOf(d, 19)) == null);
    check(Protocol.Telemetry.decode(new byte[0]) == null);
    check(Protocol.Telemetry.decode(null) == null);
    d[4] = 3;
    t = Protocol.Telemetry.decode(d);
    check(t.realWind() == 5789 && t.realCold() == 230);
    d[0] = 0x52;
    check(Protocol.Telemetry.decode(d) == null);
    System.out.println("PASS: " + checks + " protocol checks");
  }
}

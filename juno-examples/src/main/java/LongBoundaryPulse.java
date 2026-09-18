import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

/** Pulses the built-in LED using long arrays, method calls, returns, and static storage. */
public final class LongBoundaryPulse {
    private static long pauseMillis;

    private LongBoundaryPulse() {
    }

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(13);
        long[] deltas = new long[2];
        deltas[0] = 60L;
        deltas[1] = -60L;
        pauseMillis = 120L;
        boolean increasing = true;

        while (true) {
            led.high();
            Delay.millis((int) pauseMillis);
            led.low();
            Delay.millis((int) pauseMillis);

            pauseMillis = advance(pauseMillis, increasing ? deltas[0] : deltas[1]);
            if (pauseMillis >= 480L) {
                increasing = false;
            } else if (pauseMillis <= 120L) {
                increasing = true;
            }
        }
    }

    private static long advance(long value, long delta) {
        return value + delta;
    }
}

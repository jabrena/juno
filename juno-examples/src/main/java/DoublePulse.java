import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

/** Pulses the built-in LED using double arrays, method calls, returns, and static storage. */
public final class DoublePulse {
    private static double pauseMillis;

    private DoublePulse() {
    }

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(13);
        double[] deltas = new double[2];
        deltas[0] = 40.0;
        deltas[1] = -40.0;
        pauseMillis = 120.0;
        boolean increasing = true;

        while (true) {
            led.high();
            Delay.millis((int) pauseMillis);
            led.low();
            Delay.millis((int) pauseMillis);

            pauseMillis = advance(pauseMillis, increasing ? deltas[0] : deltas[1]);
            if (pauseMillis >= 480.0) {
                increasing = false;
            } else if (pauseMillis <= 120.0) {
                increasing = true;
            }
        }
    }

    private static double advance(double value, double delta) {
        return value + delta;
    }
}

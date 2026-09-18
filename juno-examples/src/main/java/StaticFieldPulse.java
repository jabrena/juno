import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

/** Pulses the built-in LED using mutable static float state. */
public final class StaticFieldPulse {
    private static float pauseMillis;

    private StaticFieldPulse() {
    }

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(13);
        pauseMillis = 100.0f;
        while (true) {
            led.high();
            Delay.millis((int) pauseMillis);
            led.low();
            Delay.millis((int) pauseMillis);

            pauseMillis = pauseMillis + 50.0f;
            if (pauseMillis > 500.0f) {
                pauseMillis = 100.0f;
            }
        }
    }
}

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

/** Pulses the built-in LED with a delay computed using float locals and arithmetic. */
public final class FloatPulse {
    private FloatPulse() {
    }

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(13);
        float pauseMillis = 100.0f;
        float[] deltas = new float[2];
        deltas[0] = 25.0f;
        deltas[1] = -25.0f;
        boolean increasing = true;
        while (true) {
            led.high();
            Delay.millis((int) pauseMillis);
            led.low();
            Delay.millis((int) pauseMillis);

            float delta = increasing ? deltas[0] : deltas[1];
            pauseMillis = advance(pauseMillis, delta);
            if (pauseMillis >= 500.0f) {
                increasing = false;
            } else if (pauseMillis <= 100.0f) {
                increasing = true;
            }
        }
    }

    private static float advance(float value, float delta) {
        return value + delta;
    }
}

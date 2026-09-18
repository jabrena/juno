import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

/**
 * Intentionally exhausts Juno's arena to demonstrate {@code JUNO-RISK-001} on real hardware.
 * A one-second startup light is followed by eight short flashes; the ninth 1 KiB allocation then
 * calls {@code juno_panic()}.
 */
public final class RuntimeRiskArenaExhaustion {
    private static final int INTS_PER_ALLOCATION = 256;

    private RuntimeRiskArenaExhaustion() {
    }

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(13);
        int iteration = 0;

        led.high();
        Delay.millis(1000);
        led.low();
        Delay.millis(1000);
        while (true) {
            int[] allocation = new int[INTS_PER_ALLOCATION];
            allocation[0] = iteration;
            led.high();
            Delay.millis(250);
            led.low();
            Delay.millis(250);
            iteration = iteration + 1;
        }
    }
}

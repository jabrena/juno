import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

/** Board test for a bounded arena allocation and a clean {@code juno inspect --risks} report. */
public final class RuntimeRiskSafePulse {
    private RuntimeRiskSafePulse() {
    }

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(13);
        int[] doubledPauses = new int[2];
        doubledPauses[0] = 240;
        doubledPauses[1] = 720;
        int index = 0;

        while (true) {
            int pause = doubledPauses[index] / 2;
            led.high();
            Delay.millis(pause);
            led.low();
            Delay.millis(pause);
            index = index == 0 ? 1 : 0;
        }
    }
}

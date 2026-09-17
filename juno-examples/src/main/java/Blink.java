import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

public final class Blink {
    private static final int LED = 13;

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(LED);
        while (true) {
            led.high();
            Delay.millis(500);
            led.low();
            Delay.millis(500);
        }
    }
}

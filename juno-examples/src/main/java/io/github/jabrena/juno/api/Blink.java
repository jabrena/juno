package io.github.jabrena.juno.api;

@Board(ArduinoUnoR4WiFi.class)
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

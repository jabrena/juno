package demo;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.Gpio;

/** Digital and analog pins, the clock, delays and the random generator. */
public final class GpioApi {
    public static void main(String[] args) {
        Gpio.pinMode(Gpio.D7, Gpio.OUTPUT);
        Gpio.pinMode(Gpio.D2, Gpio.INPUT_PULLUP);
        Gpio.digitalWrite(Gpio.D7, true);
        Gpio.analogWrite(Gpio.D9, Gpio.analogRead(Gpio.A0) / 4);
        Random.seed(Clock.micros());
        int start = Clock.millis();
        while (Clock.millis() - start < 10 && !Gpio.digitalRead(Gpio.D2)) {
            Delay.micros(Random.nextInt(1, 100));
        }
        Delay.millis(1);
    }
}

package demo;

import io.github.jabrena.juno.hardware.ArduinoOne;

import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;
import io.github.jabrena.juno.hardware.gpio.Gpio;

/** Digital and analog pins, the clock, delays and the random generator. */
public final class GpioApi {
    public static void main(String[] args) {
        Gpio gpio = ArduinoOne.getGpio();
        Clock clock = ArduinoOne.getClock();
        gpio.pinMode(Gpio.getD7(), Gpio.getOutput());
        gpio.pinMode(Gpio.getD2(), Gpio.getInputPullup());
        gpio.digitalWrite(Gpio.getD7(), true);
        gpio.analogWrite(Gpio.getD9(), gpio.analogRead(Gpio.getA0()) / 4);
        Random.seed(clock.micros());
        int start = clock.millis();
        while (clock.millis() - start < 10 && !gpio.digitalRead(Gpio.getD2())) {
            Delay.micros(Random.nextInt(1, 100));
        }
        Delay.millis(1);
    }
}

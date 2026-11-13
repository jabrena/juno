package demo;

import io.github.jabrena.juno.hardware.MCU;

import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;
import io.github.jabrena.juno.hardware.gpio.Gpio;

/** Digital and analog pins, the clock, delays and the random generator. */
public final class GpioApi {
    public static void main(String[] args) {
        Gpio gpio = MCU.getGpio();
        Clock clock = MCU.getClock();
        int digitalOutput = gpio.digitalPin("D7");
        int digitalInput = gpio.digitalPin("D2");
        int pwmOutput = gpio.digitalPin("D9");
        int analogInput = gpio.analogPin("A0");
        gpio.pinMode(digitalOutput, gpio.getOutput());
        gpio.pinMode(digitalInput, gpio.getInputPullup());
        gpio.digitalWrite(digitalOutput, true);
        gpio.analogWrite(pwmOutput, gpio.analogRead(analogInput) / 4);
        Random.seed(clock.micros());
        int start = clock.millis();
        while (clock.millis() - start < 10 && !gpio.digitalRead(digitalInput)) {
            Delay.micros(Random.nextInt(1, 100));
        }
        Delay.millis(1);
    }
}

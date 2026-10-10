package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.motors.Servo;

/** A hobby servo on a PWM pin. R4 only for now: the shim uses NUM_DIGITAL_PINS, which the UNO Q core spells NUM_OF_DIGITAL_PINS. */
@Board(ArduinoUnoR4WiFi.class)
public final class ServoApi {
    public static void main(String[] args) {
        Servo servo = Servo.of(Gpio.D10);
        
        for (int angle = 0; angle <= 180; angle += 30) {
            servo.write(angle);
            Delay.millis(15);
        }
    }
}

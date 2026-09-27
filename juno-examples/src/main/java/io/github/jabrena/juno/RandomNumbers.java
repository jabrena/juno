package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates seeding and generating pseudorandom values within bounded ranges. */
public final class RandomNumbers {
    private RandomNumbers() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Random.seed(Clock.micros());

        Serial.println("Random numbers demo");
        Serial.println("Five dice rolls");
        for (int roll = 1; roll <= 5; roll++) {
            int die = Random.nextInt(1, 7);
            Serial.print("roll: ");
            Serial.println(die);
        }

        int percentage = Random.nextInt(100);
        Serial.print("value from 0 to 99: ");
        Serial.println(percentage);

        int temperature = Random.nextInt(-10, 41);
        Serial.print("value from -10 to 40: ");
        Serial.println(temperature);
    }
}

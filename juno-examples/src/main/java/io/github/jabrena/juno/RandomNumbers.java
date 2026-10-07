package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Demonstrates seeding and generating pseudorandom values within bounded ranges. */
public final class RandomNumbers {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        RandomNumbers numbers = new RandomNumbers();

        numbers.seed();

        Serial.println("Random numbers demo");
        numbers.diceRolls();
        numbers.percentage();
        numbers.temperature();
    }

    private void seed() {
        Random.seed(Clock.micros());
    }

    private void diceRolls() {
        Serial.println("Five dice rolls");
        for (int roll = 1; roll <= 5; roll++) {
            int die = Random.nextInt(1, 7);
            Serial.print("roll: ");
            Serial.println(die);
        }
    }

    private void percentage() {
        int percentage = Random.nextInt(100);
        Serial.print("value from 0 to 99: ");
        Serial.println(percentage);
    }

    private void temperature() {
        int temperature = Random.nextInt(-10, 41);
        Serial.print("value from -10 to 40: ");
        Serial.println(temperature);
    }
}

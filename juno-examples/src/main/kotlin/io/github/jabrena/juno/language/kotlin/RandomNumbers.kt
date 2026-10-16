package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Clock
import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.Random
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates seeding and generating pseudorandom values within bounded ranges. */
object RandomNumbers {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        seed()

        Serial.println("Random numbers demo")
        diceRolls()
        percentage()
        temperature()
    }

    private fun seed() {
        Random.seed(Clock.micros())
    }

    private fun diceRolls() {
        Serial.println("Five dice rolls")
        repeat(5) {
            Serial.print("roll: ")
            Serial.println(Random.nextInt(1, 7))
        }
    }

    private fun percentage() {
        Serial.print("value from 0 to 99: ")
        Serial.println(Random.nextInt(100))
    }

    private fun temperature() {
        Serial.print("value from -10 to 40: ")
        Serial.println(Random.nextInt(-10, 41))
    }
}

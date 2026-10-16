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
        for (roll in 1..5) {
            val die = Random.nextInt(1, 7)
            Serial.print("roll: ")
            Serial.println(die)
        }
    }

    private fun percentage() {
        val percentage = Random.nextInt(100)
        Serial.print("value from 0 to 99: ")
        Serial.println(percentage)
    }

    private fun temperature() {
        val temperature = Random.nextInt(-10, 41)
        Serial.print("value from -10 to 40: ")
        Serial.println(temperature)
    }
}

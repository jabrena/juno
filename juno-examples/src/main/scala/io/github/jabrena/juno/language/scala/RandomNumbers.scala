package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Clock
import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.Random
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates seeding and generating pseudorandom values within bounded ranges. */
final class RandomNumbers

object RandomNumbers {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        seed()

        Serial.println("Random numbers demo")
        diceRolls()
        percentage()
        temperature()
    }

    @static
    private def seed(): Unit = {
        Random.seed(Clock.micros())
    }

    @static
    private def diceRolls(): Unit = {
        Serial.println("Five dice rolls")
        var roll = 1
        while (roll <= 5) {
            val die = Random.nextInt(1, 7)
            Serial.print("roll: ")
            Serial.println(die)
            roll += 1
        }
    }

    @static
    private def percentage(): Unit = {
        val percentage = Random.nextInt(100)
        Serial.print("value from 0 to 99: ")
        Serial.println(percentage)
    }

    @static
    private def temperature(): Unit = {
        val temperature = Random.nextInt(-10, 41)
        Serial.print("value from -10 to 40: ")
        Serial.println(temperature)
    }
}

package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates constants, static variables, local variables, assignment, and block scope. */
final class Variables

object Variables {
    @static private final val STEP = 5
    @static private var counter = 10

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        val board = "UNO R4 WiFi"
        val active = true
        val originalCounter = counter
        val distance = 123456789012L
        val temperature = 23.5f
        val voltage = 3.3

        Serial.println("Variables demo")
        Serial.println(s"board: $board")
        Serial.println(s"active: $active")
        Serial.println(s"original counter: $originalCounter")

        counter += STEP
        Serial.println(s"updated counter: $counter")

        Serial.println(s"distance: $distance")
        Serial.println(s"temperature: $temperature")
        Serial.println(s"voltage: $voltage")

        if (active) {
            val doubledCounter = counter * 2
            Serial.println(s"block-scoped value: $doubledCounter")
        }
    }
}

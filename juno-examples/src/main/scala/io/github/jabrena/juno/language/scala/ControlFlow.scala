package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/**
 * Demonstrates sequence, bifurcation, and iteration control-flow structures.
 *
 * A `for (i <- 1 to 3)` loop goes through scala.collection Range classes, which the examples cannot use, and Scala has
 * no `do`-`while`, so the iteration examples use `while` loops. The last one tests its condition after the body by
 * setting a flag.
 */
final class ControlFlow

object ControlFlow {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Control flow demo")

        Serial.println("Sequence")
        val initialValue = 6
        val doubledValue = initialValue * 2
        val finalValue = doubledValue + 3
        Serial.println("initial value: " + initialValue)
        Serial.println("doubled value: " + doubledValue)
        Serial.println("final value: " + finalValue)

        Serial.println("Bifurcation")
        if (finalValue >= 10) {
            Serial.println("final value is at least 10")
        } else {
            Serial.println("final value is below 10")
        }

        val remainder = finalValue % 3
        remainder match {
            case 0 => Serial.println("divisible by 3")
            case 1 => Serial.println("remainder is 1")
            case _ => Serial.println("remainder is 2")
        }

        Serial.println("Iteration with for")
        var index = 1
        while (index <= 3) {
            Serial.println("for index: " + index)
            index += 1
        }

        Serial.println("Iteration with while")
        var countdown = 3
        while (countdown > 0) {
            Serial.println("countdown: " + countdown)
            countdown -= 1
        }

        Serial.println("Iteration with do-while")
        var attempt = 1
        var again = true
        while (again) {
            Serial.println("attempt: " + attempt)
            attempt += 1
            again = attempt <= 2
        }

        Serial.println("Control flow complete")
    }
}

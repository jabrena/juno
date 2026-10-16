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
        Serial.println(s"initial value: $initialValue")
        Serial.println(s"doubled value: $doubledValue")
        Serial.println(s"final value: $finalValue")

        Serial.println("Bifurcation")
        Serial.println(if (finalValue >= 10) "final value is at least 10" else "final value is below 10")

        (finalValue % 3) match {
            case 0 => Serial.println("divisible by 3")
            case 1 => Serial.println("remainder is 1")
            case _ => Serial.println("remainder is 2")
        }

        Serial.println("Iteration with for")
        var index = 1
        while (index <= 3) {
            Serial.println(s"for index: $index")
            index += 1
        }

        Serial.println("Iteration with while")
        var countdown = 3
        while (countdown > 0) {
            Serial.println(s"countdown: $countdown")
            countdown -= 1
        }

        Serial.println("Iteration with do-while")
        var attempt = 1
        var again = true
        while (again) {
            Serial.println(s"attempt: $attempt")
            attempt += 1
            again = attempt <= 2
        }

        Serial.println("Control flow complete")
    }
}

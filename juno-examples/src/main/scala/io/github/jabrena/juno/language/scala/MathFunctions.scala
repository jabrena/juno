package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates java.lang.Math: integer helpers, rounding, powers and roots, and trigonometry. */
final class MathFunctions

object MathFunctions {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Math functions demo")

        integerHelpers()
        rounding()
        powersAndRoots()
        trigonometry()
    }

    @static
    private def integerHelpers(): Unit = {
        Serial.println("Integer helpers")
        val temperature = -17
        Serial.println(s"abs(-17): ${Math.abs(temperature)}")
        Serial.println(s"min(-17, 4): ${Math.min(temperature, 4)}")
        Serial.println(s"max(-17, 4): ${Math.max(temperature, 4)}")
        Serial.println(s"clamp(300, 0, 255): ${Math.clamp(300L, 0, 255)}")
        // Unlike / and %, floorDiv/floorMod round toward negative infinity.
        Serial.println(s"-17 / 5: ${temperature / 5}")
        Serial.println(s"-17 % 5: ${temperature % 5}")
        Serial.println(s"floorDiv(-17, 5): ${Math.floorDiv(temperature, 5)}")
        Serial.println(s"floorMod(-17, 5): ${Math.floorMod(temperature, 5)}")
    }

    @static
    private def rounding(): Unit = {
        Serial.println("Rounding")
        val reading = -2.5
        Serial.println(s"floor(-2.5): ${Math.floor(reading)}")
        Serial.println(s"ceil(-2.5): ${Math.ceil(reading)}")
        Serial.println(s"round(-2.5): ${Math.round(reading)}")
        Serial.println(s"signum(-2.5): ${Math.signum(reading)}")
    }

    @static
    private def powersAndRoots(): Unit = {
        Serial.println("Powers and roots")
        Serial.println(s"pow(2, 10): ${Math.pow(2.0, 10.0)}")
        Serial.println(s"sqrt(2): ${Math.sqrt(2.0)}")
        Serial.println(s"cbrt(27): ${Math.cbrt(27.0)}")
        Serial.println(s"hypot(3, 4): ${Math.hypot(3.0, 4.0)}")
        Serial.println(s"exp(1): ${Math.exp(1.0)}")
        Serial.println(s"log(E): ${Math.log(Math.E)}")
        Serial.println(s"log10(1000): ${Math.log10(1000.0)}")
    }

    @static
    private def trigonometry(): Unit = {
        Serial.println("Trigonometry every 45 degrees")
        var step = 0
        while (step <= 4) {
            val degrees = step * 45
            val radians = Math.toRadians(degrees.toDouble)
            Serial.println(s"sin($degrees): ${Math.sin(radians)}")
            Serial.println(s"cos($degrees): ${Math.cos(radians)}")
            step += 1
        }
        Serial.println(s"atan2(1, 1) deg: ${Math.toDegrees(Math.atan2(1.0, 1.0))}")
        Serial.println(s"PI: ${Math.PI}")
    }
}

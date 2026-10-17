package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/**
 * Demonstrates the double-precision java.lang.Math functions: powers and roots, exponentials and logarithms, and
 * trigonometry. Double-precision libm is only on the UNO R4 WiFi and UNO Q toolchains, so the other boards cannot
 * build it; MathFunctionsSinglePrecision covers the rest.
 */
@Board(Array(classOf[ArduinoUnoR4WiFi], classOf[ArduinoUnoQ]))
final class MathFunctionsDoublePrecision

object MathFunctionsDoublePrecision {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Math functions, double precision demo")

        powersAndRoots()
        trigonometry()
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

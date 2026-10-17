package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Demonstrates the double-precision kotlin.math functions: powers and roots, exponentials and logarithms, and
 * trigonometry. Double-precision libm is only on the UNO R4 WiFi and UNO Q toolchains, so the other boards cannot
 * build it; [MathFunctionsSinglePrecision] covers the rest.
 */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class)
object MathFunctionsDoublePrecision {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Math functions, double precision demo")

        powersAndRoots()
        trigonometry()
    }

    private fun powersAndRoots() {
        Serial.println("Powers and roots")
        Serial.println("pow(2, 10): ${2.0.pow(10)}")
        Serial.println("sqrt(2): ${sqrt(2.0)}")
        Serial.println("cbrt(27): ${cbrt(27.0)}")
        Serial.println("hypot(3, 4): ${hypot(3.0, 4.0)}")
        Serial.println("exp(1): ${exp(1.0)}")
        Serial.println("log(E): ${ln(E)}")
        Serial.println("log10(1000): ${log10(1000.0)}")
    }

    private fun trigonometry() {
        Serial.println("Trigonometry every 45 degrees")
        for (step in 0..4) {
            val degrees = step * 45
            val radians = Math.toRadians(degrees.toDouble())
            Serial.println("sin($degrees): ${sin(radians)}")
            Serial.println("cos($degrees): ${cos(radians)}")
        }
        Serial.println("atan2(1, 1) deg: ${Math.toDegrees(atan2(1.0, 1.0))}")
        Serial.println("PI: $PI")
    }
}

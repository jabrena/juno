package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Demonstrates kotlin.math for absolute values, rounding, powers, roots, and trigonometry, plus the java.lang.Math
 * helpers that have no kotlin.math counterpart.
 */
object MathFunctions {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Math functions demo")

        integerHelpers()
        rounding()
        powersAndRoots()
        trigonometry()
    }

    private fun integerHelpers() {
        Serial.println("Integer helpers")
        val temperature = -17
        Serial.println("abs(-17): ${abs(temperature)}")
        Serial.println("min(-17, 4): ${min(temperature, 4)}")
        Serial.println("max(-17, 4): ${max(temperature, 4)}")
        // No kotlin.math counterpart for clamp, so this stays on java.lang.Math.
        Serial.println("clamp(300, 0, 255): ${Math.clamp(300, 0, 255)}")
        // Unlike / and %, floorDiv/floorMod round toward negative infinity.
        Serial.println("-17 / 5: ${temperature / 5}")
        Serial.println("-17 % 5: ${temperature % 5}")
        Serial.println("floorDiv(-17, 5): ${Math.floorDiv(temperature, 5)}")
        Serial.println("floorMod(-17, 5): ${Math.floorMod(temperature, 5)}")
    }

    private fun rounding() {
        Serial.println("Rounding")
        val reading = -2.5
        Serial.println("floor(-2.5): ${floor(reading)}")
        Serial.println("ceil(-2.5): ${ceil(reading)}")
        // kotlin.math.round rounds half to even and returns a Double; Math.round rounds half up and returns a Long.
        Serial.println("round(-2.5): ${Math.round(reading)}")
        Serial.println("sign(-2.5): ${sign(reading)}")
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

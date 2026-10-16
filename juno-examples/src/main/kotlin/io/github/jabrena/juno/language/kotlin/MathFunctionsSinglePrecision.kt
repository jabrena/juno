package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Demonstrates kotlin.math and java.lang.Math on Int and Float values. Every call here builds on every board: it
 * avoids the double-precision libm functions that [MathFunctionsDoublePrecision] uses, which the UNO R3 and UNO WiFi
 * Rev2 toolchains do not provide.
 */
object MathFunctionsSinglePrecision {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Math functions, single precision demo")

        integerHelpers()
        floatHelpers()
        rounding()
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

    private fun floatHelpers() {
        Serial.println("Float helpers")
        val reading = -2.5f
        Serial.println("abs(-2.5): ${abs(reading)}")
        Serial.println("min(-2.5, 1.5): ${min(reading, 1.5f)}")
        Serial.println("max(-2.5, 1.5): ${max(reading, 1.5f)}")
        Serial.println("clamp(-2.5, 0, 1): ${Math.clamp(reading, 0f, 1f)}")
        Serial.println("sign(-2.5): ${sign(reading)}")
    }

    private fun rounding() {
        Serial.println("Rounding")
        val reading = -2.5f
        Serial.println("floor(-2.5): ${floor(reading)}")
        Serial.println("ceil(-2.5): ${ceil(reading)}")
        // Math.round rounds half up and returns an Int, unlike kotlin.math.round, which rounds half to even.
        Serial.println("round(-2.5): ${Math.round(reading)}")
        Serial.println("sqrt(2): ${sqrt(2.0f)}")
    }
}

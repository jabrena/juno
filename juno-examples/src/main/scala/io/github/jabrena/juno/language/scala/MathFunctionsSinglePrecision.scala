package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/**
 * Demonstrates java.lang.Math on Int and Float values. Every call here builds on every board: it avoids the
 * double-precision libm functions that MathFunctionsDoublePrecision uses, which the UNO R3 and UNO WiFi Rev2
 * toolchains do not provide.
 */
final class MathFunctionsSinglePrecision

object MathFunctionsSinglePrecision {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Math functions, single precision demo")

        integerHelpers()
        floatHelpers()
        rounding()
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
    private def floatHelpers(): Unit = {
        Serial.println("Float helpers")
        val reading = -2.5f
        Serial.println(s"abs(-2.5): ${Math.abs(reading)}")
        Serial.println(s"min(-2.5, 1.5): ${Math.min(reading, 1.5f)}")
        Serial.println(s"max(-2.5, 1.5): ${Math.max(reading, 1.5f)}")
        Serial.println(s"clamp(-2.5, 0, 1): ${Math.clamp(reading, 0.0f, 1.0f)}")
        Serial.println(s"signum(-2.5): ${Math.signum(reading)}")
    }

    @static
    private def rounding(): Unit = {
        Serial.println("Rounding")
        val reading = -2.5f
        // floor and ceil take a double, so the float is widened explicitly: an implicit widening calls scala.Float$.
        Serial.println(s"floor(-2.5): ${Math.floor(reading.toDouble)}")
        Serial.println(s"ceil(-2.5): ${Math.ceil(reading.toDouble)}")
        Serial.println(s"round(-2.5): ${Math.round(reading)}")
        val two = 2.0f
        Serial.println(s"sqrt(2): ${Math.sqrt(two.toDouble)}")
    }
}

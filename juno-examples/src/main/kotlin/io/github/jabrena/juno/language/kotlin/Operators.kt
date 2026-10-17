package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates arithmetic, assignment, comparison, logical, bitwise, shift, and unary operators. */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class, ArduinoUnoR3WiFiRev2::class)
object Operators {
    private const val LEFT = 12
    private const val RIGHT = 5

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Using operators demo")

        arithmetic()
        assignment()
        comparison()
        logical()
        bitwise()
        shifts()
        unary()
    }

    private fun arithmetic() {
        Serial.println("Arithmetic operators")
        Serial.println("12 + 5 = ${LEFT + RIGHT}")
        Serial.println("12 - 5 = ${LEFT - RIGHT}")
        Serial.println("12 * 5 = ${LEFT * RIGHT}")
        Serial.println("12 / 5 = ${LEFT / RIGHT}")
        Serial.println("12 % 5 = ${LEFT % RIGHT}")
    }

    private fun assignment() {
        Serial.println("Assignment operators")
        var result = LEFT
        result += RIGHT
        Serial.println("result += 5: $result")
        result *= 2
        Serial.println("result *= 2: $result")
    }

    private fun comparison() {
        Serial.println("Comparison operators")
        Serial.println("12 == 5: ${LEFT == RIGHT}")
        Serial.println("12 != 5: ${LEFT != RIGHT}")
        Serial.println("12 < 5: ${LEFT < RIGHT}")
        Serial.println("12 >= 5: ${LEFT >= RIGHT}")
    }

    private fun logical() {
        val positive = LEFT > 0
        val singleDigit = RIGHT < 10
        Serial.println("Logical operators")
        Serial.println("positive && singleDigit: ${positive && singleDigit}")
        Serial.println("positive || singleDigit: ${positive || singleDigit}")
        Serial.println("!positive: ${!positive}")
    }

    private fun bitwise() {
        Serial.println("Bitwise operators")
        Serial.println("12 & 5 = ${LEFT and RIGHT}")
        Serial.println("12 | 5 = ${LEFT or RIGHT}")
        Serial.println("12 ^ 5 = ${LEFT xor RIGHT}")
    }

    private fun shifts() {
        Serial.println("Shift operators")
        Serial.println("12 << 2 = ${LEFT shl 2}")
        Serial.println("12 >> 1 = ${LEFT shr 1}")
        Serial.println("-16 >>> 2 = ${-16 ushr 2}")
    }

    private fun unary() {
        Serial.println("Unary operators")
        var value = LEFT
        value++
        Serial.println("12++ = $value")
        value--
        Serial.println("13-- = $value")
        Serial.println("-12 = ${-LEFT}")
    }
}

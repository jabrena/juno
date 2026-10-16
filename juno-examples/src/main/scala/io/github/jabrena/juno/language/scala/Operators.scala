package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates arithmetic, assignment, comparison, logical, bitwise, shift, and unary operators. */
final class Operators

object Operators {
    private final val LEFT = 12
    private final val RIGHT = 5

    @static
    def main(args: Array[String]): Unit = {
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

    @static
    private def arithmetic(): Unit = {
        Serial.println("Arithmetic operators")
        Serial.println("12 + 5 = " + (LEFT + RIGHT))
        Serial.println("12 - 5 = " + (LEFT - RIGHT))
        Serial.println("12 * 5 = " + (LEFT * RIGHT))
        Serial.println("12 / 5 = " + (LEFT / RIGHT))
        Serial.println("12 % 5 = " + (LEFT % RIGHT))
    }

    @static
    private def assignment(): Unit = {
        Serial.println("Assignment operators")
        var result = LEFT
        result += RIGHT
        Serial.println("result += 5: " + result)
        result *= 2
        Serial.println("result *= 2: " + result)
    }

    @static
    private def comparison(): Unit = {
        Serial.println("Comparison operators")
        Serial.println("12 == 5: " + (LEFT == RIGHT))
        Serial.println("12 != 5: " + (LEFT != RIGHT))
        Serial.println("12 < 5: " + (LEFT < RIGHT))
        Serial.println("12 >= 5: " + (LEFT >= RIGHT))
    }

    @static
    private def logical(): Unit = {
        val positive = LEFT > 0
        val singleDigit = RIGHT < 10
        Serial.println("Logical operators")
        Serial.println("positive && singleDigit: " + (positive && singleDigit))
        Serial.println("positive || singleDigit: " + (positive || singleDigit))
        Serial.println("!positive: " + !positive)
    }

    @static
    private def bitwise(): Unit = {
        Serial.println("Bitwise operators")
        Serial.println("12 & 5 = " + (LEFT & RIGHT))
        Serial.println("12 | 5 = " + (LEFT | RIGHT))
        Serial.println("12 ^ 5 = " + (LEFT ^ RIGHT))
    }

    @static
    private def shifts(): Unit = {
        Serial.println("Shift operators")
        Serial.println("12 << 2 = " + (LEFT << 2))
        Serial.println("12 >> 1 = " + (LEFT >> 1))
        Serial.println("-16 >>> 2 = " + (-16 >>> 2))
    }

    @static
    private def unary(): Unit = {
        Serial.println("Unary operators")
        var value = LEFT
        value += 1
        Serial.println("12++ = " + value)
        value -= 1
        Serial.println("13-- = " + value)
        Serial.println("-12 = " + (-LEFT))
    }
}

package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates calling functions with parameters and return values, reporting each result with a string interpolation. */
final class Methods

object Methods {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        printHeading()

        val left = 12
        val right = 5
        val sum = add(left, right)
        val squared = square(sum)

        Serial.println(s"$left + $right = $sum")
        Serial.println(s"$sum squared = $squared")
        Serial.println(s"$sum is even: ${isEven(sum)}")

        val product = multiply(123456L, 1000L)
        Serial.println(s"long product: $product")

        val mean = average(12.5, 7.5)
        Serial.println(s"average: $mean")

        val combined = addThenDouble(left, right)
        Serial.println(s"add then double: $combined")
    }

    @static
    private def printHeading(): Unit = {
        Serial.println("Defining methods demo")
    }

    @static
    private def add(first: Int, second: Int): Int = first + second

    @static
    private def square(value: Int): Int = value * value

    @static
    private def isEven(value: Int): Boolean = value % 2 == 0

    @static
    private def multiply(first: Long, second: Long): Long = first * second

    @static
    private def average(first: Double, second: Double): Double = (first + second) / 2.0

    @static
    private def addThenDouble(first: Int, second: Int): Int = doubleValue(add(first, second))

    @static
    private def doubleValue(value: Int): Int = value * 2
}

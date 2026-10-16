package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/**
 * Demonstrates calling functions with parameters and return values, reporting each result with a string template.
 */
object Methods {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        printHeading()

        val left = 12
        val right = 5
        val sum = add(left, right)
        val squared = square(sum)

        Serial.println("$left + $right = $sum")
        Serial.println("$sum squared = $squared")
        Serial.println("$sum is even: ${isEven(sum)}")

        val product = multiply(123456L, 1000L)
        Serial.println("long product: $product")

        val mean = average(12.5, 7.5)
        Serial.println("average: $mean")

        val combined = addThenDouble(left, right)
        Serial.println("add then double: $combined")
    }

    private fun printHeading() {
        Serial.println("Defining methods demo")
    }

    private fun add(first: Int, second: Int): Int = first + second

    private fun square(value: Int): Int = value * value

    private fun isEven(value: Int): Boolean = value % 2 == 0

    private fun multiply(first: Long, second: Long): Long = first * second

    private fun average(first: Double, second: Double): Double = (first + second) / 2.0

    private fun addThenDouble(first: Int, second: Int): Int = doubleValue(add(first, second))

    private fun doubleValue(value: Int): Int = value * 2
}

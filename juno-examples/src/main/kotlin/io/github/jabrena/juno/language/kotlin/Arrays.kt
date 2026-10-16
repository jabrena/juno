package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates fixed-size primitive arrays: indexing, iteration, passing to functions, and a 2D array. */
object Arrays {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Arrays demo")

        val numbers = IntArray(5) { (it + 1) * 10 }
        Serial.println("length: ${numbers.size}")
        Serial.println("first: ${numbers[0]}")
        Serial.println("last: ${numbers[numbers.size - 1]}")
        printSummary(numbers)

        reverseInPlace(numbers)
        Serial.println("after reverse, first: ${numbers[0]}")

        val voltages = doubleArrayOf(3.5, 5.0, 1.5, 2.0)
        Serial.println("mean voltage: ${mean(voltages)}")

        val big = LongArray(2)
        big[0] = 3_000_000_000L
        big[1] = big[0] * 2
        Serial.println("big[1]: ${big[1]}")

        val grid = Array(2) { row -> IntArray(3) { column -> row * 3 + column } }
        Serial.println("grid[1][2]: ${grid[1][2]}")
    }

    // An IntArray knows its own size, so the function needs no separate count parameter.
    private fun printSummary(values: IntArray) {
        var total = 0
        var largest = values[0]
        for (value in values) {
            total += value
            if (value > largest) {
                largest = value
            }
        }
        Serial.println("sum: $total")
        Serial.println("max: $largest")
    }

    // Swaps from both ends toward the middle. The stdlib's reverse() is not a call the board can lower.
    private fun reverseInPlace(values: IntArray) {
        var left = 0
        var right = values.size - 1
        while (left < right) {
            val swap = values[left]
            values[left] = values[right]
            values[right] = swap
            left++
            right--
        }
    }

    private fun mean(values: DoubleArray): Double {
        var total = 0.0
        for (value in values) {
            total += value
        }
        return total / values.size
    }
}

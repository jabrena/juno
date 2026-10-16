package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates fixed-size primitive arrays: indexing, loops, passing to functions, and a 2D array. */
object Arrays {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Arrays demo")

        val numbers = IntArray(5)
        for (index in numbers.indices) {
            numbers[index] = (index + 1) * 10
        }
        Serial.println("length: " + numbers.size)
        Serial.println("first: " + numbers[0])
        Serial.println("last: " + numbers[numbers.size - 1])
        Serial.println("sum: " + sum(numbers, numbers.size))
        Serial.println("max: " + max(numbers, numbers.size))

        reverse(numbers, numbers.size)
        Serial.println("after reverse, first: " + numbers[0])

        val voltages = doubleArrayOf(3.5, 5.0, 1.5, 2.0)
        var total = 0.0
        for (index in voltages.indices) {
            total += voltages[index]
        }
        Serial.println("mean voltage: " + total / voltages.size)

        val big = LongArray(2)
        big[0] = 3000000000L
        big[1] = big[0] * 2
        Serial.println("big[1]: " + big[1])

        val grid = Array(2) { IntArray(3) }
        for (row in 0 until 2) {
            for (column in 0 until 3) {
                grid[row][column] = row * 3 + column
            }
        }
        Serial.println("grid[1][2]: " + grid[1][2])
    }

    // An array parameter has no known length, so callers pass the element count.
    private fun sum(values: IntArray, count: Int): Int {
        var total = 0
        for (index in 0 until count) {
            total += values[index]
        }
        return total
    }

    private fun max(values: IntArray, count: Int): Int {
        var largest = values[0]
        for (index in 1 until count) {
            if (values[index] > largest) {
                largest = values[index]
            }
        }
        return largest
    }

    private fun reverse(values: IntArray, count: Int) {
        var left = 0
        var right = count - 1
        while (left < right) {
            val swap = values[left]
            values[left] = values[right]
            values[right] = swap
            left++
            right--
        }
    }
}

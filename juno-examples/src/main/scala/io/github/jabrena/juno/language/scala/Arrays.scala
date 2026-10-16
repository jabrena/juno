package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates fixed-size primitive arrays: indexing, loops, passing to functions, and a 2D array. */
final class Arrays

object Arrays {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Arrays demo")

        val numbers = new Array[Int](5)
        var index = 0
        while (index < numbers.length) {
            numbers(index) = (index + 1) * 10
            index += 1
        }
        Serial.println("length: " + numbers.length)
        Serial.println("first: " + numbers(0))
        Serial.println("last: " + numbers(numbers.length - 1))
        Serial.println("sum: " + sum(numbers, numbers.length))
        Serial.println("max: " + max(numbers, numbers.length))

        reverse(numbers, numbers.length)
        Serial.println("after reverse, first: " + numbers(0))

        val voltages = new Array[Double](4)
        voltages(0) = 3.5
        voltages(1) = 5.0
        voltages(2) = 1.5
        voltages(3) = 2.0
        var total = 0.0
        index = 0
        while (index < voltages.length) {
            total += voltages(index)
            index += 1
        }
        Serial.println("mean voltage: " + total / voltages.length)

        val big = new Array[Long](2)
        big(0) = 3000000000L
        big(1) = big(0) * 2
        Serial.println("big[1]: " + big(1))

        val grid = new Array[Array[Int]](2)
        grid(0) = new Array[Int](3)
        grid(1) = new Array[Int](3)
        var row = 0
        while (row < 2) {
            var column = 0
            while (column < 3) {
                grid(row)(column) = row * 3 + column
                column += 1
            }
            row += 1
        }
        Serial.println("grid[1][2]: " + grid(1)(2))
    }

    // An array parameter has no known length, so callers pass the element count.
    @static
    private def sum(values: Array[Int], count: Int): Int = {
        var total = 0
        var index = 0
        while (index < count) {
            total += values(index)
            index += 1
        }
        total
    }

    @static
    private def max(values: Array[Int], count: Int): Int = {
        var largest = values(0)
        var index = 1
        while (index < count) {
            if (values(index) > largest) {
                largest = values(index)
            }
            index += 1
        }
        largest
    }

    @static
    private def reverse(values: Array[Int], count: Int): Unit = {
        var left = 0
        var right = count - 1
        while (left < right) {
            val swap = values(left)
            values(left) = values(right)
            values(right) = swap
            left += 1
            right -= 1
        }
    }
}

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
        Serial.println(s"length: ${numbers.length}")
        Serial.println(s"first: ${numbers(0)}")
        Serial.println(s"last: ${numbers(numbers.length - 1)}")
        printSummary(numbers)

        reverseInPlace(numbers)
        Serial.println(s"after reverse, first: ${numbers(0)}")

        val voltages = new Array[Double](4)
        voltages(0) = 3.5
        voltages(1) = 5.0
        voltages(2) = 1.5
        voltages(3) = 2.0
        Serial.println(s"mean voltage: ${mean(voltages)}")

        val big = new Array[Long](2)
        big(0) = 3000000000L
        big(1) = big(0) * 2
        Serial.println(s"big[1]: ${big(1)}")

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
        Serial.println(s"grid[1][2]: ${grid(1)(2)}")
    }

    // An array knows its own length, so the helpers take no separate count parameter.
    @static
    private def printSummary(values: Array[Int]): Unit = {
        var total = 0
        var largest = values(0)
        var index = 0
        while (index < values.length) {
            total += values(index)
            if (values(index) > largest) {
                largest = values(index)
            }
            index += 1
        }
        Serial.println(s"sum: $total")
        Serial.println(s"max: $largest")
    }

    // Swaps from both ends toward the middle; the Scala library's reverse goes through a class the examples cannot depend on.
    @static
    private def reverseInPlace(values: Array[Int]): Unit = {
        var left = 0
        var right = values.length - 1
        while (left < right) {
            val swap = values(left)
            values(left) = values(right)
            values(right) = swap
            left += 1
            right -= 1
        }
    }

    @static
    private def mean(values: Array[Double]): Double = {
        var total = 0.0
        var index = 0
        while (index < values.length) {
            total += values(index)
            index += 1
        }
        total / values.length
    }
}

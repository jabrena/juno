package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import kotlin.math.abs

/** Demonstrates final classes with constructors and instance fields, data classes, enums, and arena allocation. */
object Objects {

    // A sealed hierarchy of singletons stands in for an enum: Kotlin enums pull in stdlib code the board cannot lower.
    sealed interface Direction {
        object North : Direction
        object East : Direction
        object South : Direction
        object West : Direction
    }

    data class Point(val x: Int, val y: Int) {
        // Identity checks rather than a when on the hierarchy: the sealed-type switch uses bytecode Juno does not lower.
        fun moved(direction: Direction, steps: Int): Point = when {
            direction === Direction.North -> Point(x, y + steps)
            direction === Direction.East -> Point(x + steps, y)
            direction === Direction.South -> Point(x, y - steps)
            else -> Point(x - steps, y)
        }

        fun distanceFromOrigin(): Int = abs(x) + abs(y)
    }

    class Counter(start: Int, private val step: Int) {
        var value: Int = start
            private set

        fun increment() {
            value += step
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Objects demo")

        val counter = Counter(10, 5)
        counter.increment()
        counter.increment()
        Serial.println("counter: ${counter.value}")

        val position = Point(0, 0)
            .moved(Direction.North, 4)
            .moved(Direction.East, 3)
            .moved(Direction.South, 6)
        Serial.println("x: ${position.x}")
        Serial.println("y: ${position.y}")
        Serial.println("distance: ${position.distanceFromOrigin()}")

        // Every Point is a fresh arena object; the conservative collector reclaims the unreachable ones,
        // so the total allocated here is far beyond the 8 KiB arena.
        var walker = Point(0, 0)
        repeat(2000) {
            walker = walker.moved(Direction.East, 1)
        }
        Serial.println("walker x: ${walker.x}")
    }
}

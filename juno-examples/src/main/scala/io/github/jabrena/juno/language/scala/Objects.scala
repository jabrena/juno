package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates final classes with constructors and instance fields, enum-like singletons, and arena allocation. */
final class Objects

object Objects {

    // A sealed hierarchy of singletons stands in for an enum: Scala enums go through scala.Enumeration, a Scala library class the examples cannot depend on.
    sealed trait Direction

    object North extends Direction
    object East extends Direction
    object South extends Direction
    object West extends Direction

    // A plain class rather than a case class: case classes generate equals, hashCode and toString through the Scala library.
    final class Point(val x: Int, val y: Int) {
        // Identity checks rather than a match on the hierarchy, as the Kotlin example does.
        def moved(direction: Direction, steps: Int): Point = {
            if (direction eq North) {
                new Point(x, y + steps)
            } else if (direction eq East) {
                new Point(x + steps, y)
            } else if (direction eq South) {
                new Point(x, y - steps)
            } else {
                new Point(x - steps, y)
            }
        }

        def distanceFromOrigin(): Int = Math.abs(x) + Math.abs(y)
    }

    final class Counter(start: Int, step: Int) {
        private var current = start

        def value: Int = current

        def increment(): Unit = {
            current += step
        }
    }

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Objects demo")

        val counter = new Counter(10, 5)
        counter.increment()
        counter.increment()
        Serial.println(s"counter: ${counter.value}")

        val position = new Point(0, 0)
          .moved(North, 4)
          .moved(East, 3)
          .moved(South, 6)
        Serial.println(s"x: ${position.x}")
        Serial.println(s"y: ${position.y}")
        Serial.println(s"distance: ${position.distanceFromOrigin()}")

        // Every Point is a fresh arena object; the conservative collector reclaims the unreachable ones,
        // so the total allocated here is far beyond the 8 KiB arena.
        var walker = new Point(0, 0)
        var step = 0
        while (step < 2000) {
            walker = walker.moved(East, 1)
            step += 1
        }
        Serial.println(s"walker x: ${walker.x}")
    }
}

package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import java.util.concurrent.StructuredTaskScope

/**
 * Structured concurrency: a StructuredTaskScope owns three subtasks that each count the primes in a slice of 2..299,
 * and the owner reads the partial counts only after the scope has joined them.
 *
 * The scope is closed in a finally block, so no subtask outlives it: join waits for every fork, and closing the scope
 * ends it. Each subtask writes only its own slot of `counts`, so the owner needs no locks to read them.
 */
@Board(Array(classOf[ArduinoUnoR4WiFi], classOf[ArduinoUnoQ]))
final class StructuredConcurrency

object StructuredConcurrency {
    @static private final val LIMIT = 300
    @static private final val SLICES = 3

    // A subtask cannot return an int, so each one stores its partial result in its own slot.
    @static private val counts = new Array[Int](SLICES)

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Serial.println(s"Counting primes below $LIMIT")

        countInScope()
        report()
    }

    /** Forks one subtask per slice inside a scope, and leaves the scope only once every subtask has finished. */
    @static
    private def countInScope(): Unit = {
        val slice = LIMIT / SLICES
        val scope = StructuredTaskScope.open[AnyRef]()
        try {
            val first: Runnable = () => countPrimes(0, 2, slice)
            val second: Runnable = () => countPrimes(1, slice, 2 * slice)
            val third: Runnable = () => countPrimes(2, 2 * slice, LIMIT)
            scope.fork(first)
            scope.fork(second)
            scope.fork(third)
            scope.join()
        } finally {
            scope.close()
        }
        Serial.println("scope closed")
    }

    /** The owner adds up the partial counts once the scope has been joined. */
    @static
    private def report(): Unit = {
        var total = 0
        var index = 0
        while (index < SLICES) {
            Serial.println(s"slice $index: ${counts(index)}")
            total += counts(index)
            index += 1
        }
        Serial.println(s"primes below $LIMIT: $total")
    }

    /** Counts the primes in `[from, to)` by trial division and stores the result in `counts[slot]`. */
    @static
    private def countPrimes(slot: Int, from: Int, to: Int): Unit = {
        var found = 0
        var candidate = from
        while (candidate < to) {
            if (isPrime(candidate)) {
                found += 1
            }
            candidate += 1
        }
        counts(slot) = found
    }

    @static
    private def isPrime(value: Int): Boolean = {
        if (value < 2) {
            return false
        }
        var divisor = 2
        while (divisor * divisor <= value) {
            if (value % divisor == 0) {
                return false
            }
            divisor += 1
        }
        true
    }
}

package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import java.util.concurrent.StructuredTaskScope

/**
 * Structured concurrency: a [StructuredTaskScope] owns three subtasks that each count the primes in a slice of 2..299,
 * and the owner reads the partial counts only after the scope has joined them.
 *
 * The scope is closed in a finally block, so no subtask outlives it: `join` waits for every fork, and closing the scope
 * ends it. Each subtask writes only its own slot of `counts`, so the owner needs no locks to read them.
 */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class)
object StructuredConcurrency {
    private const val LIMIT = 300
    private const val SLICES = 3

    // A subtask cannot return an int, so each one stores its partial result in its own slot.
    private val counts = IntArray(SLICES)

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Serial.println("Counting primes below $LIMIT")

        countInScope()
        report()
    }

    /** Forks one subtask per slice inside a scope, and leaves the scope only once every subtask has finished. */
    private fun countInScope() {
        val slice = LIMIT / SLICES
        val scope = StructuredTaskScope.open<Any?>()
        try {
            scope.fork<Any?>(Runnable { countPrimes(0, 2, slice) })
            scope.fork<Any?>(Runnable { countPrimes(1, slice, 2 * slice) })
            scope.fork<Any?>(Runnable { countPrimes(2, 2 * slice, LIMIT) })
            scope.join()
        } finally {
            scope.close()
        }
        Serial.println("scope closed")
    }

    /** The owner adds up the partial counts once the scope has been joined. */
    private fun report() {
        var total = 0
        counts.forEachIndexed { index, count ->
            Serial.println("slice $index: $count")
            total += count
        }
        Serial.println("primes below $LIMIT: $total")
    }

    /** Counts the primes in `[from, to)` by trial division and stores the result in `counts[slot]`. */
    private fun countPrimes(slot: Int, from: Int, to: Int) {
        var found = 0
        for (candidate in from until to) {
            if (isPrime(candidate)) {
                found++
            }
        }
        counts[slot] = found
    }

    private fun isPrime(value: Int): Boolean {
        if (value < 2) {
            return false
        }
        var divisor = 2
        while (divisor * divisor <= value) {
            if (value % divisor == 0) {
                return false
            }
            divisor++
        }
        return true
    }
}

package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/**
 * Virtual threads lowered onto Juno's preemptive task runtime: three virtual threads each count the primes in a
 * slice of 2..299, the owner joins all of them, and then adds up the partial counts.
 *
 * Virtual threads are preemptive on the board: each runs for 1 ms time slices and can be switched out in the middle
 * of a statement, exactly as on the JVM, so each thread writes only its own slot of `counts` and the owner reads
 * them after `join`. At most four threads, the owner included, run at once.
 */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class)
object VirtualThreads {
    private const val LIMIT = 300
    private const val SLICES = 3

    // Threads cannot return an int, so each one stores its partial result in its own slot.
    private val counts = IntArray(SLICES)

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Serial.println("Counting primes below $LIMIT")

        countInParallel()
        report()
    }

    /** Starts one virtual thread per slice of the range and waits for all of them. */
    private fun countInParallel() {
        val slice = LIMIT / SLICES

        val first = Thread.startVirtualThread { countPrimes(0, 2, slice) }
        val second = Thread.startVirtualThread { countPrimes(1, slice, 2 * slice) }
        val third = Thread.startVirtualThread { countPrimes(2, 2 * slice, LIMIT) }
        first.join()
        second.join()
        third.join()
    }

    /** The owner adds up the partial counts once every virtual thread has finished. */
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

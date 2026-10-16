package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/** Volatile publication, intrinsic monitor blocks, atomic counters, and Juno's restricted ReentrantLock subset. */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class)
object Synchronization {
    class Guard

    private val guard = Guard()
    private val lock = ReentrantLock()
    @Volatile private var ready = false
    private var publishedValue = 0
    private var synchronizedCounter = 0
    private var lockedCounter = 0
    private val atomicCounter = AtomicInteger()

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)

        volatilePublication()
        monitorAndLock()
    }

    /** A volatile flag publishes a value from one subtask to its parent. */
    private fun volatilePublication() {
        val scope = StructuredTaskScope.open<Any?>()
        try {
            scope.fork<Any?>(Runnable { publish() })
            while (!ready) {
                // Volatile is reloaded and this backedge lets the publisher run.
            }
            scope.join()
        } finally {
            scope.close()
        }
        Serial.print("published ")
        Serial.println(publishedValue)
    }

    /** Two subtasks increment shared counters under a monitor, under a lock, and with an atomic. */
    private fun monitorAndLock() {
        val scope = StructuredTaskScope.open<Any?>()
        try {
            scope.fork<Any?>(Runnable { count() })
            scope.fork<Any?>(Runnable { count() })
            scope.join()
        } finally {
            scope.close()
        }
        Serial.print("synchronized ")
        Serial.println(synchronizedCounter)
        Serial.print("locked ")
        Serial.println(lockedCounter)
        Serial.print("atomic ")
        Serial.println(atomicCounter.get())
    }

    private fun publish() {
        publishedValue = 42
        ready = true
    }

    private fun count() {
        repeat(50) {
            atomicCounter.incrementAndGet()

            synchronized(guard) {
                synchronizedCounter++
            }

            lock.lock()
            try {
                lockedCounter++
                if (lock.tryLock()) {
                    lock.unlock()
                }
            } finally {
                lock.unlock()
            }
        }
    }
}

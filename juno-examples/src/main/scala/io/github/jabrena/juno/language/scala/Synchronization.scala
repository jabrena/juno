package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/** Volatile publication, intrinsic monitor blocks, atomic counters, and Juno's restricted ReentrantLock subset. */
@Board(Array(classOf[ArduinoUnoR4WiFi], classOf[ArduinoUnoQ]))
final class Synchronization

object Synchronization {
    final class Guard

    @static private val guard = new Guard
    @static private val lock = new ReentrantLock()
    @static @volatile private var ready = false
    @static private var publishedValue = 0
    @static private var synchronizedCounter = 0
    @static private var lockedCounter = 0
    @static private val atomicCounter = new AtomicInteger()

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)

        volatilePublication()
        monitorAndLock()
    }

    /** A volatile flag publishes a value from one subtask to its parent. */
    @static
    private def volatilePublication(): Unit = {
        val scope = StructuredTaskScope.open[AnyRef]()
        try {
            val publisher: Runnable = () => publish()
            scope.fork(publisher)
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
    @static
    private def monitorAndLock(): Unit = {
        val scope = StructuredTaskScope.open[AnyRef]()
        try {
            val counter: Runnable = () => count()
            scope.fork(counter)
            scope.fork(counter)
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

    @static
    private def publish(): Unit = {
        publishedValue = 42
        ready = true
    }

    @static
    private def count(): Unit = {
        var i = 0
        while (i < 50) {
            atomicCounter.incrementAndGet()

            // The block ends with a value: a Unit block reads BoxedUnit.UNIT, which Juno rejects.
            guard.synchronized {
                synchronizedCounter += 1
                synchronizedCounter
            }

            lock.lock()
            try {
                lockedCounter += 1
                if (lock.tryLock()) {
                    lock.unlock()
                }
            } finally {
                lock.unlock()
            }
            i += 1
        }
    }
}

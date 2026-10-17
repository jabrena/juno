package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/**
 * Demonstrates throwing and catching exceptions: try/catch/finally, several catch clauses for one handler, catching
 * by a supertype, dividing by zero, nested handlers, a custom exception class with its own field, exceptions that
 * propagate across function calls, and a resource that is closed on every path.
 *
 * A `throw` is caught by the nearest matching handler, in the same function or in any caller, and `finally` blocks
 * run while it unwinds. An integer division by zero inside a `try` raises `ArithmeticException("/ by zero")`. An
 * exception that no handler catches prints `Exception in thread "main" <class>: <message>` and halts the board.
 */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class, ArduinoUnoR3WiFiRev2::class)
object Exceptions {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)

        Serial.println("Exceptions demo")

        validatingReadings()
        customException()
        catchingBySupertype()
        dividingByZero()
        nestedHandlers()
        acrossFunctions()
        useWithResources()

        Serial.println("Done")
    }

    /** Throw, catch two types with separate clauses, and a finally block that runs for every reading. */
    private fun validatingReadings() {
        Serial.println("Validating readings")
        for (reading in intArrayOf(12, -3, 250)) {
            try {
                if (reading < 0) {
                    throw IllegalArgumentException("negative reading")
                }
                if (reading > 100) {
                    throw IllegalStateException("sensor saturated")
                }
                Serial.println("accepted: $reading")
            } catch (e: IllegalArgumentException) {
                Serial.println("rejected: ${e.message}")
            } catch (e: IllegalStateException) {
                Serial.println("rejected: ${e.message}")
            } finally {
                Serial.println("checked one reading")
            }
        }
    }

    /** A custom exception class that carries its own field. */
    private fun customException() {
        Serial.println("Custom exception")
        try {
            throw SensorException("sensor offline", 2)
        } catch (e: SensorException) {
            Serial.println("${e.message} on sensor ${e.sensor}")
        }
    }

    class SensorException(message: String, val sensor: Int) : RuntimeException(message)

    /** A handler for a supertype also catches its subclasses. */
    private fun catchingBySupertype() {
        Serial.println("Catching by supertype")
        try {
            throw NumberFormatException("not a number")
        } catch (e: IllegalArgumentException) {
            Serial.println("caught: ${e.message}")
        }
    }

    private fun dividingByZero() {
        Serial.println("Dividing by zero")
        val total = 250
        var samples = 0
        try {
            // Integer division by zero raises ArithmeticException, just like on the JVM.
            val average = total / samples
            Serial.println(average)
        } catch (e: ArithmeticException) {
            Serial.println("caught: ${e.message}")
        }
    }

    private fun nestedHandlers() {
        Serial.println("Nested handlers")
        try {
            try {
                throw UnsupportedOperationException("not implemented")
            } finally {
                Serial.println("inner finally runs first")
            }
        } catch (e: UnsupportedOperationException) {
            Serial.println("outer catch: ${e.message}")
        }
    }

    /** The exception unwinds through the calling functions until a handler matches. */
    private fun acrossFunctions() {
        Serial.println("Across functions")
        try {
            // parse() throws two calls below this handler; the exception unwinds through sample().
            Serial.println("parsed ${sample(41)}")
            Serial.println("parsed ${sample(-1)}")
        } catch (e: IllegalArgumentException) {
            Serial.println("caught ${e.message}")
        }
        try {
            withFinally(-1)
        } catch (e: IllegalArgumentException) {
            Serial.println("caught ${e.message}")
        }
    }

    /** The resource is closed whether or not the body throws. */
    private fun useWithResources() {
        Serial.println("Try-with-resources")
        try {
            withResource(1, -1)
        } catch (e: IllegalArgumentException) {
            Serial.println("caught ${e.message}")
        }
        withResource(2, 5)
    }

    private fun parse(reading: Int): Int {
        if (reading < 0) {
            throw IllegalArgumentException("negative reading")
        }
        return reading
    }

    private fun sample(reading: Int): Int = parse(reading) + 1

    private fun withFinally(reading: Int) {
        try {
            sample(reading)
        } finally {
            Serial.println("cleanup")
        }
    }

    /** A resource whose close() is observable on the serial monitor. */
    class Channel(private val id: Int) : AutoCloseable {
        override fun close() {
            Serial.println("close $id")
        }
    }

    private fun withResource(id: Int, reading: Int) {
        // Written out as try/finally: Kotlin's `use` helper is not one of the calls Juno lowers.
        val channel = Channel(id)
        try {
            sample(reading)
        } finally {
            channel.close()
        }
    }
}

package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/**
 * Demonstrates throwing and catching exceptions: try/catch/finally, several catch cases for one handler, catching
 * by a supertype, dividing by zero, nested handlers, a custom exception class with its own field, exceptions that
 * propagate across function calls, and a resource that is closed on every path.
 *
 * A `throw` is caught by the nearest matching handler, in the same function or in any caller, and `finally` blocks
 * run while it unwinds. An integer division by zero inside a `try` raises `ArithmeticException("/ by zero")`. An
 * exception that no handler catches prints `Exception in thread "main" <class>: <message>` and halts the board.
 */
final class Exceptions

object Exceptions {

    @static
    def main(args: Array[String]): Unit = {
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

    /** Throw, catch two types with separate cases, and a finally block that runs for every reading. */
    @static
    private def validatingReadings(): Unit = {
        Serial.println("Validating readings")
        // Array(...) is avoided: it calls into the Scala library, which the examples cannot depend on.
        val readings = new Array[Int](3)
        readings(0) = 12
        readings(1) = -3
        readings(2) = 250
        var index = 0
        while (index < readings.length) {
            val reading = readings(index)
            try {
                if (reading < 0) {
                    throw new IllegalArgumentException("negative reading")
                }
                if (reading > 100) {
                    throw new IllegalStateException("sensor saturated")
                }
                Serial.println(s"accepted: $reading")
            } catch {
                case e: IllegalArgumentException => Serial.println(s"rejected: ${e.getMessage}")
                case e: IllegalStateException => Serial.println(s"rejected: ${e.getMessage}")
            } finally {
                Serial.println("checked one reading")
            }
            index += 1
        }
    }

    /** A custom exception class that carries its own field. */
    @static
    private def customException(): Unit = {
        Serial.println("Custom exception")
        try {
            throw new SensorException("sensor offline", 2)
        } catch {
            case e: SensorException => Serial.println(s"${e.getMessage} on sensor ${e.sensor}")
        }
    }

    /** A handler for a supertype also catches its subclasses. */
    @static
    private def catchingBySupertype(): Unit = {
        Serial.println("Catching by supertype")
        try {
            throw new NumberFormatException("not a number")
        } catch {
            case e: IllegalArgumentException => Serial.println(s"caught: ${e.getMessage}")
        }
    }

    @static
    private def dividingByZero(): Unit = {
        Serial.println("Dividing by zero")
        val total = 250
        val samples = 0
        try {
            // Integer division by zero raises ArithmeticException, just like on the JVM.
            val average = total / samples
            Serial.println(average)
        } catch {
            case e: ArithmeticException => Serial.println(s"caught: ${e.getMessage}")
        }
    }

    @static
    private def nestedHandlers(): Unit = {
        Serial.println("Nested handlers")
        try {
            try {
                throw new UnsupportedOperationException("not implemented")
            } finally {
                Serial.println("inner finally runs first")
            }
        } catch {
            case e: UnsupportedOperationException => Serial.println(s"outer catch: ${e.getMessage}")
        }
    }

    /** The exception unwinds through the calling functions until a handler matches. */
    @static
    private def acrossFunctions(): Unit = {
        Serial.println("Across functions")
        try {
            // parse() throws two calls below this handler; the exception unwinds through sample().
            Serial.println(s"parsed ${sample(41)}")
            Serial.println(s"parsed ${sample(-1)}")
        } catch {
            case e: IllegalArgumentException => Serial.println(s"caught ${e.getMessage}")
        }
        try {
            withFinally(-1)
        } catch {
            case e: IllegalArgumentException => Serial.println(s"caught ${e.getMessage}")
        }
    }

    /** The resource is closed whether or not the body throws. */
    @static
    private def useWithResources(): Unit = {
        Serial.println("Try-with-resources")
        try {
            withResource(1, -1)
        } catch {
            case e: IllegalArgumentException => Serial.println(s"caught ${e.getMessage}")
        }
        withResource(2, 5)
    }

    @static
    private def parse(reading: Int): Int = {
        if (reading < 0) {
            throw new IllegalArgumentException("negative reading")
        }
        reading
    }

    @static
    private def sample(reading: Int): Int = parse(reading) + 1

    @static
    private def withFinally(reading: Int): Unit = {
        try {
            sample(reading)
        } finally {
            Serial.println("cleanup")
        }
    }

    /** A resource whose close() is observable on the serial monitor. */
    final class Channel(id: Int) extends AutoCloseable {
        override def close(): Unit = {
            Serial.println(s"close $id")
        }
    }

    @static
    private def withResource(id: Int, reading: Int): Unit = {
        // Written out as try/finally, as in the Kotlin example.
        val channel = new Channel(id)
        try {
            sample(reading)
        } finally {
            channel.close()
        }
    }
}

/** A custom exception with a field, thrown and caught by Exceptions. */
final class SensorException(message: String, val sensor: Int) extends RuntimeException(message)

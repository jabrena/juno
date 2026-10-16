package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import java.math.BigDecimal
import java.math.MathContext
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.StructuredTaskScope
import kotlin.math.min

/**
 * JDK 25 [ScopedValue]s as a precision policy for parallel numeric work. The owner binds a [MathContext] once; three
 * subtasks approximate sqrt(2), pi and e with [BigDecimal], and each one stops iterating as soon as its next correction
 * is below 10^-precision and rounds with the same context, without receiving the precision as a parameter. A nested
 * binding can lower the precision for its own extent, but `min` against the outer value means it can never raise
 * it above what its owner allows.
 */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class)
object ScopedValuesPrecision {
    private const val MAX_DIGITS = 40
    // Array lengths must be compile-time constants on the board, so the count is a const rather than names.size.
    private const val CONSTANTS = 3

    private val precision: ScopedValue<MathContext> = ScopedValue.newInstance()
    private val stepCounts: ScopedValue<IntArray> = ScopedValue.newInstance()
    private val names = arrayOf("sqrt2", "pi", "e")

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)

        val steps = IntArray(CONSTANTS)
        ScopedValue
            .where(precision, MathContext(MAX_DIGITS))
            .where(stepCounts, steps)
            .run(Runnable { scenarios() })
    }

    private fun scenarios() {
        ownerBinding()
        nestedLowerPrecision()
        nestedCappedPrecision()
        backInOwner()
    }

    private fun ownerBinding() {
        report("owner binds ${digits()} digits")
    }

    /** A nested binding may lower the precision for its own extent. */
    private fun nestedLowerPrecision() {
        ScopedValue.where(precision, MathContext(min(5, digits())))
            .run(Runnable { report("nested asks for 5") })
    }

    /** Asking for more than the owner allows is capped by `min`. */
    private fun nestedCappedPrecision() {
        ScopedValue.where(precision, MathContext(min(60, digits())))
            .run(Runnable { report("nested asks for 60, capped") })
    }

    /** The outer binding is back as soon as the nested ones return. */
    private fun backInOwner() {
        report("back in the owner")
    }

    /** Forks one approximation per constant (a scope holds at most three subtasks), then prints and combines them. */
    private fun report(title: String) {
        Serial.println("== $title")
        val scope = StructuredTaskScope.open<Any?>()
        val values: Array<BigDecimal> = try {
            val first = scope.fork(Callable { sqrtTwo() })
            val second = scope.fork(Callable { pi() })
            val third = scope.fork(Callable { euler() })
            scope.join()
            arrayOf(first.get(), second.get(), third.get())
        } catch (interrupted: InterruptedException) {
            throw IllegalStateException("interrupted")
        } catch (interrupted: ExecutionException) {
            throw IllegalStateException("interrupted")
        } finally {
            scope.close()
        }
        val (sqrt2, pi, e) = values
        val context = precision.get()
        val steps = stepCounts.get()
        for (index in 0 until CONSTANTS) {
            Serial.print(names[index])
            Serial.print(" = ")
            Serial.print(values[index].round(context).toString())
            Serial.print(" (")
            Serial.print(steps[index])
            Serial.println(" steps)")
        }
        Serial.print("sqrt2^2 = ")
        Serial.println(sqrt2.multiply(sqrt2, context).toString())
        Serial.print("pi - e = ")
        Serial.println(pi.subtract(e, context).toString())
    }

    /** Newton's method on x^2 = 2. */
    private fun sqrtTwo(): BigDecimal {
        val context = precision.get()
        val two = BigDecimal(2)
        val tolerance = tolerance()
        var x = BigDecimal.ONE
        var steps = 0
        var delta: BigDecimal
        do {
            val next = x.add(two.divide(x, context), context).divide(two, context)
            delta = next.subtract(x).abs()
            x = next
            steps++
        } while (delta.compareTo(tolerance) >= 0)
        stepCounts.get()[0] = steps
        return x
    }

    /** Machin's formula: pi = 16 arctan(1/5) - 4 arctan(1/239). */
    private fun pi(): BigDecimal {
        val context = precision.get()
        val first = arctanInverse(5).multiply(BigDecimal.valueOf(16L), context)
        val second = arctanInverse(239).multiply(BigDecimal.valueOf(4L), context)
        return first.subtract(second, context)
    }

    /** arctan(1/n) = 1/n - 1/(3 n^3) + 1/(5 n^5) - ..., counting its terms in the shared step slot. */
    private fun arctanInverse(n: Int): BigDecimal {
        val context = precision.get()
        val tolerance = tolerance()
        val square = BigDecimal.valueOf(n.toLong() * n)
        var power = BigDecimal.ONE.divide(BigDecimal.valueOf(n.toLong()), context)
        var sum = power
        var k = 1
        while (power.compareTo(tolerance) >= 0) {
            power = power.divide(square, context)
            val term = power.divide(BigDecimal.valueOf(2L * k + 1), context)
            sum = if (k % 2 == 1) sum.subtract(term, context) else sum.add(term, context)
            k++
            stepCounts.get()[1]++
        }
        return sum
    }

    /** The series 1 + 1/1! + 1/2! + ... */
    private fun euler(): BigDecimal {
        val context = precision.get()
        val tolerance = tolerance()
        var sum = BigDecimal.ONE
        var term = BigDecimal.ONE
        var steps = 0
        do {
            steps++
            term = term.divide(BigDecimal.valueOf(steps.toLong()), context)
            sum = sum.add(term, context)
        } while (term.compareTo(tolerance) >= 0)
        stepCounts.get()[2] = steps
        return sum
    }

    private fun digits(): Int = precision.get().precision

    /** 10^-(digits + 2): two guard digits beyond the inherited precision. */
    private fun tolerance(): BigDecimal = BigDecimal.ONE.movePointLeft(digits() + 2)
}

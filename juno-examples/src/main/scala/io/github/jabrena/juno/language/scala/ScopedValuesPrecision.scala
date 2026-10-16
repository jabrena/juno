package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial
import java.math.BigDecimal
import java.math.MathContext
import java.util.concurrent.StructuredTaskScope

/**
 * JDK 25 ScopedValues as a precision policy for parallel numeric work. The owner binds a MathContext once; three
 * subtasks approximate sqrt(2), pi and e with BigDecimal, and each one stops iterating as soon as its next correction
 * is below 10^-precision and rounds with the same context, without receiving the precision as a parameter. A nested
 * binding can lower the precision for its own extent, but `Math.min` against the outer value means it can never raise
 * it above what its owner allows.
 */
@Board(Array(classOf[ArduinoUnoR4WiFi], classOf[ArduinoUnoQ]))
final class ScopedValuesPrecision

object ScopedValuesPrecision {
    @static private final val MAX_DIGITS = 40
    @static private final val CONSTANTS = 3

    @static private val PRECISION: ScopedValue[MathContext] = ScopedValue.newInstance()
    @static private val STEPS: ScopedValue[Array[Int]] = ScopedValue.newInstance()
    // One slot per subtask: a lambda may capture no local array, so the results live in a static field.
    @static private val RESULTS = new Array[BigDecimal](CONSTANTS)

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)

        val steps = new Array[Int](CONSTANTS)
        val scenarios: Runnable = () => runScenarios()
        ScopedValue
            .where(PRECISION, new MathContext(MAX_DIGITS))
            .where(STEPS, steps)
            .run(scenarios)
    }

    @static
    private def runScenarios(): Unit = {
        ownerBinding()
        nestedLowerPrecision()
        nestedCappedPrecision()
        backInOwner()
    }

    @static
    private def ownerBinding(): Unit = {
        report(s"owner binds ${digits()} digits")
    }

    /** A nested binding may lower the precision for its own extent. */
    @static
    private def nestedLowerPrecision(): Unit = {
        val nested: Runnable = () => report("nested asks for 5")
        ScopedValue.where(PRECISION, new MathContext(Math.min(5, digits()))).run(nested)
    }

    /** Asking for more than the owner allows is capped by `Math.min`. */
    @static
    private def nestedCappedPrecision(): Unit = {
        val nested: Runnable = () => report("nested asks for 60, capped")
        ScopedValue.where(PRECISION, new MathContext(Math.min(60, digits()))).run(nested)
    }

    /** The outer binding is back as soon as the nested ones return. */
    @static
    private def backInOwner(): Unit = {
        report("back in the owner")
    }

    /** Forks one approximation per constant (a scope holds at most three subtasks), then prints and combines them. */
    @static
    private def report(title: String): Unit = {
        Serial.println(s"== $title")
        val scope = StructuredTaskScope.open[AnyRef]()
        try {
            // Each subtask writes its own slot, as VirtualThreads does: a value-returning Callable would need a bridge
            // method that returns Object, which the board cannot lower. join() makes the writes visible to the owner.
            val sqrtTask: Runnable = () => RESULTS(0) = sqrtTwo()
            val piTask: Runnable = () => RESULTS(1) = pi()
            val eulerTask: Runnable = () => RESULTS(2) = euler()
            scope.fork(sqrtTask)
            scope.fork(piTask)
            scope.fork(eulerTask)
            scope.join()
        } catch {
            case _: InterruptedException => throw new IllegalStateException("interrupted")
        } finally {
            scope.close()
        }
        val sqrt2 = RESULTS(0)
        val piValue = RESULTS(1)
        val eulerValue = RESULTS(2)
        val context = PRECISION.get()
        val steps = STEPS.get()
        var index = 0
        while (index < CONSTANTS) {
            Serial.print(nameOf(index))
            Serial.print(" = ")
            Serial.print(RESULTS(index).round(context).toString)
            Serial.print(" (")
            Serial.print(steps(index))
            Serial.println(" steps)")
            index += 1
        }
        Serial.print("sqrt2^2 = ")
        Serial.println(sqrt2.multiply(sqrt2, context).toString)
        Serial.print("pi - e = ")
        Serial.println(piValue.subtract(eulerValue, context).toString)
    }

    @static
    private def nameOf(index: Int): String = {
        if (index == 0) {
            "sqrt2"
        } else if (index == 1) {
            "pi"
        } else {
            "e"
        }
    }

    /** Newton's method on x^2 = 2. */
    @static
    private def sqrtTwo(): BigDecimal = {
        val context = PRECISION.get()
        val two = new BigDecimal(2)
        val limit = tolerance()
        var x = BigDecimal.ONE
        var steps = 0
        var converged = false
        while (!converged) {
            val next = x.add(two.divide(x, context), context).divide(two, context)
            val delta = next.subtract(x).abs()
            x = next
            steps += 1
            converged = delta.compareTo(limit) < 0
        }
        STEPS.get()(0) = steps
        x
    }

    /** Machin's formula: pi = 16 arctan(1/5) - 4 arctan(1/239). */
    @static
    private def pi(): BigDecimal = {
        val context = PRECISION.get()
        val first = arctanInverse(5).multiply(BigDecimal.valueOf(16L), context)
        val second = arctanInverse(239).multiply(BigDecimal.valueOf(4L), context)
        first.subtract(second, context)
    }

    /** arctan(1/n) = 1/n - 1/(3 n^3) + 1/(5 n^5) - ..., counting its terms in the shared step slot. */
    @static
    private def arctanInverse(n: Int): BigDecimal = {
        val context = PRECISION.get()
        val limit = tolerance()
        val square = BigDecimal.valueOf(n.toLong * n)
        var power = BigDecimal.ONE.divide(BigDecimal.valueOf(n.toLong), context)
        var sum = power
        var k = 1
        while (power.compareTo(limit) >= 0) {
            power = power.divide(square, context)
            val term = power.divide(BigDecimal.valueOf(2L * k + 1), context)
            sum = if (k % 2 == 1) sum.subtract(term, context) else sum.add(term, context)
            k += 1
            STEPS.get()(1) = STEPS.get()(1) + 1
        }
        sum
    }

    /** The series 1 + 1/1! + 1/2! + ... */
    @static
    private def euler(): BigDecimal = {
        val context = PRECISION.get()
        val limit = tolerance()
        var sum = BigDecimal.ONE
        var term = BigDecimal.ONE
        var steps = 0
        var converged = false
        while (!converged) {
            steps += 1
            term = term.divide(BigDecimal.valueOf(steps.toLong), context)
            sum = sum.add(term, context)
            converged = term.compareTo(limit) < 0
        }
        STEPS.get()(2) = steps
        sum
    }

    @static
    private def digits(): Int = PRECISION.get().getPrecision

    /** 10^-(digits + 2): two guard digits beyond the inherited precision. */
    @static
    private def tolerance(): BigDecimal = BigDecimal.ONE.movePointLeft(digits() + 2)
}

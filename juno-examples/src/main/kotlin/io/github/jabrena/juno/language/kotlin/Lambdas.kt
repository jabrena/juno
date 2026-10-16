package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Non-capturing and capturing lambdas, plus bound function references. */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class)
object Lambdas {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)

        val square = IntOperation { value -> value * value }

        val capturedAmount = 2
        val captured = IntOperation { value -> value + capturedAmount }

        val instanceReference = IntOperation { value -> twice(value) }
        val offset = Offset(3)
        val boundReference = IntOperation { value -> offset.add(value) }

        val result = apply(square, 2) +
            apply(captured, 1) +
            apply(instanceReference, 2) +
            apply(boundReference, 3)

        val printResult = Runnable {
            Serial.println("Lambda result: $result")
        }
        printResult.run()
    }

    fun interface IntOperation {
        fun apply(value: Int): Int
    }

    class Offset(private val amount: Int) {
        fun add(value: Int): Int = value + amount
    }

    private fun twice(value: Int): Int = value * 2

    private fun apply(operation: IntOperation, value: Int): Int = operation.apply(value)
}

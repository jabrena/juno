package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Non-capturing and capturing lambdas, plus bound and unbound function references. */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class)
object Lambdas {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)

        val square = IntOperation { value -> value * value }

        val capturedAmount = 2
        val captured = IntOperation { value -> value + capturedAmount }

        val instanceReference = IntOperation(this::twice)
        val offset = Offset(3)
        val boundReference = IntOperation(offset::add)

        val result = square(2) + captured(1) + instanceReference(2) + boundReference(3)

        Runnable { Serial.println("Lambda result: $result") }.run()
    }

    fun interface IntOperation {
        operator fun invoke(value: Int): Int
    }

    class Offset(private val amount: Int) {
        fun add(value: Int): Int = value + amount
    }

    private fun twice(value: Int): Int = value * 2
}

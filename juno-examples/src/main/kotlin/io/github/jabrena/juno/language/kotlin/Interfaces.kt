package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.Gpio
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates closed-world interface dispatch with two reachable implementations. */
@Board(ArduinoUnoR4WiFi::class, ArduinoUnoQ::class)
object Interfaces {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)

        val scale = choose(Gpio.analogRead(0) > 512)
        val result = scale.apply(100)
        while (true) {
            Serial.print("Interface dispatch result: ")
            Serial.println(result)
            Delay.millis(1000)
        }
    }

    interface Scale {
        fun apply(value: Int): Int
    }

    class Half : Scale {
        override fun apply(value: Int): Int = value / 2
    }

    class Double : Scale {
        override fun apply(value: Int): Int = value * 2
    }

    private fun choose(highInput: Boolean): Scale = if (highInput) Half() else Double()
}

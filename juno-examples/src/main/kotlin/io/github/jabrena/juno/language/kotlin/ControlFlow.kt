package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates sequence, bifurcation, and iteration control-flow structures. */
object ControlFlow {

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Control flow demo")

        Serial.println("Sequence")
        val initialValue = 6
        val doubledValue = initialValue * 2
        val finalValue = doubledValue + 3
        Serial.println("initial value: " + initialValue)
        Serial.println("doubled value: " + doubledValue)
        Serial.println("final value: " + finalValue)

        Serial.println("Bifurcation")
        if (finalValue >= 10) {
            Serial.println("final value is at least 10")
        } else {
            Serial.println("final value is below 10")
        }

        val remainder = finalValue % 3
        when (remainder) {
            0 -> Serial.println("divisible by 3")
            1 -> Serial.println("remainder is 1")
            else -> Serial.println("remainder is 2")
        }

        Serial.println("Iteration with for")
        for (index in 1..3) {
            Serial.println("for index: " + index)
        }

        Serial.println("Iteration with while")
        var countdown = 3
        while (countdown > 0) {
            Serial.println("countdown: " + countdown)
            countdown--
        }

        Serial.println("Iteration with do-while")
        var attempt = 1
        do {
            Serial.println("attempt: " + attempt)
            attempt++
        } while (attempt <= 2)

        Serial.println("Control flow complete")
    }
}

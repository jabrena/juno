package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates sequence, bifurcation, and iteration control-flow structures. */
fun main() {
    Serial.begin(BaudRate.BAUD_115200)
    Delay.millis(2000)

    Serial.println("Control flow demo")

    Serial.println("Sequence")
    val initialValue = 6
    val doubledValue = initialValue * 2
    val finalValue = doubledValue + 3
    Serial.println("initial value: $initialValue")
    Serial.println("doubled value: $doubledValue")
    Serial.println("final value: $finalValue")

    Serial.println("Bifurcation")
    Serial.println(if (finalValue >= 10) "final value is at least 10" else "final value is below 10")

    Serial.println(
        when (finalValue % 3) {
            0 -> "divisible by 3"
            1 -> "remainder is 1"
            else -> "remainder is 2"
        },
    )

    Serial.println("Iteration with for")
    for (index in 1..3) {
        Serial.println("for index: $index")
    }

    Serial.println("Iteration with while")
    var countdown = 3
    while (countdown > 0) {
        Serial.println("countdown: $countdown")
        countdown--
    }

    Serial.println("Iteration with do-while")
    var attempt = 1
    do {
        Serial.println("attempt: $attempt")
        attempt++
    } while (attempt <= 2)

    Serial.println("Control flow complete")
}

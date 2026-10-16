package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

fun main() {
    // Serial.println("Hello, World!")
    Serial.begin(BaudRate.BAUD_115200)
    Serial.println("Hello, World!")
}
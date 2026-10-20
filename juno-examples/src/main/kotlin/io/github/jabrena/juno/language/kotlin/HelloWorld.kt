package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.hardware.serial.BaudRate
import io.github.jabrena.juno.api.hardware.serial.Serial

fun main() {
    Serial.begin(BaudRate.BAUD_115200)
    Serial.println("Hello, World!")
}
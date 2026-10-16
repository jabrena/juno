package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates strings: literals, length, charAt, equals, number conversion, concatenation, and a StringBuilder. */
object Strings {
    private const val NAME = "Juno"
    private const val COUNT = 42

    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Strings demo")

        inspecting()
        comparing()
        converting()
        concatenating()
        reversing()
    }

    private fun inspecting() {
        Serial.println("name: " + NAME)
        Serial.println("length: " + NAME.length)
        Serial.println("first: " + NAME[0])
        Serial.println("last: " + NAME[NAME.length - 1])
    }

    private fun comparing() {
        // equals() is called explicitly: Kotlin's == on objects goes through a stdlib helper the board cannot lower.
        Serial.println("equals Juno: " + NAME.equals("Juno"))
        Serial.println("equals Uno: " + NAME.equals("Uno"))
    }

    private fun converting() {
        val text = COUNT.toString()
        Serial.println("valueOf: " + text + " has " + text.length + " digits")
    }

    private fun concatenating() {
        Serial.println("greeting: " + "Hello, " + NAME + "! count=" + COUNT)
    }

    private fun reversing() {
        Serial.println("reversed: " + reverse(NAME))
    }

    private fun reverse(value: String): String {
        val builder = StringBuilder(value.length)
        for (index in value.length - 1 downTo 0) {
            builder.append(value[index])
        }
        return builder.toString()
    }
}

package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates strings: literals, length, charAt, equals, number conversion, concatenation, and a StringBuilder. */
final class Strings

object Strings {
    private final val NAME = "Juno"
    private final val COUNT = 42

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Delay.millis(2000)

        Serial.println("Strings demo")

        inspecting()
        comparing()
        converting()
        concatenating()
        reversing()
    }

    @static
    private def inspecting(): Unit = {
        Serial.println("name: " + NAME)
        Serial.println("length: " + NAME.length)
        // charAt is called explicitly: Scala apply on a String goes through StringOps, a Scala library class the examples cannot depend on.
        Serial.println("first: " + NAME.charAt(0))
        Serial.println("last: " + NAME.charAt(NAME.length - 1))
    }

    @static
    private def comparing(): Unit = {
        // equals() is called explicitly: Scala == on objects goes through a Scala library helper the examples cannot depend on.
        Serial.println("equals Juno: " + NAME.equals("Juno"))
        Serial.println("equals Uno: " + NAME.equals("Uno"))
    }

    @static
    private def converting(): Unit = {
        val text = String.valueOf(COUNT)
        Serial.println("valueOf: " + text + " has " + text.length + " digits")
    }

    @static
    private def concatenating(): Unit = {
        Serial.println("greeting: " + "Hello, " + NAME + "! count=" + COUNT)
    }

    @static
    private def reversing(): Unit = {
        Serial.println("reversed: " + reverse(NAME))
    }

    // java.lang.StringBuilder is named in full: Scala's default StringBuilder is a collection type.
    @static
    private def reverse(value: String): String = {
        val builder = new java.lang.StringBuilder(value.length)
        var index = value.length - 1
        while (index >= 0) {
            builder.append(value.charAt(index))
            index -= 1
        }
        builder.toString
    }
}

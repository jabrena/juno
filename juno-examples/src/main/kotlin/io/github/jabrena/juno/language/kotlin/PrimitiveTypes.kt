package io.github.jabrena.juno.language.kotlin

import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Prints the size and range of every primitive type; the compiler folds each line into one string constant. */
object PrimitiveTypes {
    @JvmStatic
    fun main(args: Array<String>) {
        Serial.begin(BaudRate.BAUD_115200)
        Serial.println("Primitive types demo")
        Serial.println("boolean: values: " + true + " or " + false)
        Serial.println("byte: " + Byte.SIZE_BYTES + " bytes, range: " + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE)
        Serial.println("short: " + Short.SIZE_BYTES + " bytes, range: " + Short.MIN_VALUE + " to " + Short.MAX_VALUE)
        Serial.println("char: " + Char.SIZE_BYTES + " bytes, range: " + Char.MIN_VALUE.code + " to " + Char.MAX_VALUE.code)
        Serial.println("int: " + Int.SIZE_BYTES + " bytes, range: " + Int.MIN_VALUE + " to " + Int.MAX_VALUE)
        Serial.println("long: " + Long.SIZE_BYTES + " bytes, range: " + Long.MIN_VALUE + " to " + Long.MAX_VALUE)
        Serial.println("float: " + Float.SIZE_BYTES + " bytes, range: " + Float.MIN_VALUE + " to " + Float.MAX_VALUE)
        Serial.println("double: " + Double.SIZE_BYTES + " bytes, range: " + Double.MIN_VALUE + " to " + Double.MAX_VALUE)
    }
}

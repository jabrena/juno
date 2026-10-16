package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Prints the size and range of every primitive type; the compiler folds each line into one string constant. */
final class PrimitiveTypes

object PrimitiveTypes {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Serial.println("Primitive types demo")
        Serial.println("boolean: values: " + true + " or " + false)
        Serial.println("byte: " + java.lang.Byte.BYTES + " bytes, range: " + java.lang.Byte.MIN_VALUE + " to " + java.lang.Byte.MAX_VALUE)
        Serial.println("short: " + java.lang.Short.BYTES + " bytes, range: " + java.lang.Short.MIN_VALUE + " to " + java.lang.Short.MAX_VALUE)
        Serial.println("char: " + java.lang.Character.BYTES + " bytes, range: " + java.lang.Character.MIN_VALUE.toInt + " to " + java.lang.Character.MAX_VALUE.toInt)
        Serial.println("int: " + java.lang.Integer.BYTES + " bytes, range: " + java.lang.Integer.MIN_VALUE + " to " + java.lang.Integer.MAX_VALUE)
        Serial.println("long: " + java.lang.Long.BYTES + " bytes, range: " + java.lang.Long.MIN_VALUE + " to " + java.lang.Long.MAX_VALUE)
        Serial.println("float: " + java.lang.Float.BYTES + " bytes, range: " + java.lang.Float.MIN_VALUE + " to " + java.lang.Float.MAX_VALUE)
        Serial.println("double: " + java.lang.Double.BYTES + " bytes, range: " + java.lang.Double.MIN_VALUE + " to " + java.lang.Double.MAX_VALUE)
    }
}

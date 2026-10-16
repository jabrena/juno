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
        Serial.println("boolean: values: true or false")
        Serial.println(s"byte: ${java.lang.Byte.BYTES} bytes, range: ${Byte.MinValue} to ${Byte.MaxValue}")
        Serial.println(s"short: ${java.lang.Short.BYTES} bytes, range: ${Short.MinValue} to ${Short.MaxValue}")
        Serial.println(s"char: ${java.lang.Character.BYTES} bytes, range: ${Char.MinValue.toInt} to ${Char.MaxValue.toInt}")
        Serial.println(s"int: ${java.lang.Integer.BYTES} bytes, range: ${Int.MinValue} to ${Int.MaxValue}")
        Serial.println(s"long: ${java.lang.Long.BYTES} bytes, range: ${Long.MinValue} to ${Long.MaxValue}")
        Serial.println(s"float: ${java.lang.Float.BYTES} bytes, range: ${Float.MinPositiveValue} to ${Float.MaxValue}")
        Serial.println(s"double: ${java.lang.Double.BYTES} bytes, range: ${Double.MinPositiveValue} to ${Double.MaxValue}")
    }
}

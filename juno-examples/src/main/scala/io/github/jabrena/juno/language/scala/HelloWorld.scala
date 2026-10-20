package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.hardware.serial.BaudRate
import io.github.jabrena.juno.hardware.serial.Serial

final class HelloWorld

object HelloWorld {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Serial.println("Hello, World!")
    }
}

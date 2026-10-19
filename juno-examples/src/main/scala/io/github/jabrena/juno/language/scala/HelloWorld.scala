package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.api.hardware.io.serial.BaudRate
import io.github.jabrena.juno.api.hardware.io.serial.Serial

final class HelloWorld

object HelloWorld {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)
        Serial.println("Hello, World!")
    }
}

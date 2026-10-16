package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.Delay
import io.github.jabrena.juno.api.io.Gpio
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Demonstrates closed-world interface dispatch with two reachable implementations. */
@Board(Array(classOf[ArduinoUnoR4WiFi], classOf[ArduinoUnoQ]))
final class Interfaces

object Interfaces {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)

        val scale = choose(Gpio.analogRead(0) > 512)
        val result = scale.apply(100)
        while (true) {
            Serial.print("Interface dispatch result: ")
            Serial.println(result)
            Delay.millis(1000)
        }
    }

    trait Scale {
        def apply(value: Int): Int
    }

    final class Half extends Scale {
        override def apply(value: Int): Int = value / 2
    }

    // Named Doubled rather than Double: a class of that name would shadow scala.Double inside this object.
    final class Doubled extends Scale {
        override def apply(value: Int): Int = value * 2
    }

    @static
    private def choose(highInput: Boolean): Scale = if (highInput) new Half() else new Doubled()
}

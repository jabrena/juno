package io.github.jabrena.juno.language.scala

import scala.annotation.static

import io.github.jabrena.juno.annotations.ArduinoUnoQ
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
import io.github.jabrena.juno.annotations.Board
import io.github.jabrena.juno.api.io.serial.BaudRate
import io.github.jabrena.juno.api.io.serial.Serial

/** Non-capturing and capturing lambdas, plus bound method references. */
@Board(Array(classOf[ArduinoUnoR4WiFi], classOf[ArduinoUnoQ]))
final class Lambdas

object Lambdas {

    @static
    def main(args: Array[String]): Unit = {
        Serial.begin(BaudRate.BAUD_115200)

        val square: IntOperation = (value: Int) => value * value

        val capturedAmount = 2
        val captured: IntOperation = (value: Int) => value + capturedAmount

        val instanceReference: IntOperation = (value: Int) => twice(value)
        val offset = new Offset(3)
        val boundReference: IntOperation = (value: Int) => offset.add(value)

        // Calling an IntOperation directly works through its apply method, as Scala's function-call syntax does.
        val result = square(2) + captured(1) + instanceReference(2) + boundReference(3)

        val printResult: Runnable = () => Serial.println(s"Lambda result: $result")
        printResult.run()
    }

    trait IntOperation {
        def apply(value: Int): Int
    }

    final class Offset(amount: Int) {
        def add(value: Int): Int = value + amount
    }

    @static
    private def twice(value: Int): Int = value * 2
}

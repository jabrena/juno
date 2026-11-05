package io.github.jabrena.juno.language.groovy

import groovy.transform.CompileStatic

import io.github.jabrena.juno.hardware.serial.BaudRate
import io.github.jabrena.juno.hardware.serial.Serial

/**
 * {@link CompileStatic} is required: without it Groovy adds a dynamic metaclass bootstrap that uses class literals,
 * which Juno rejects.
 */
@CompileStatic
class HelloWorld {

    static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200)
        Serial.println("Hello, World!")
    }
}

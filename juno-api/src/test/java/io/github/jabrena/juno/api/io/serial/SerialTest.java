package io.github.jabrena.juno.api.io.serial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SerialTest {

    @BeforeEach
    void openAnEmptyPort() {
        Serial.reset();
    }

    @Test
    void beginOpensThePortAtTheRequestedRate() {
        Serial.begin(BaudRate.BAUD_115200);
        assertThat(Serial.baudRate).isEqualTo(115_200);

        Serial.begin(9_600);
        assertThat(Serial.baudRate).isEqualTo(9_600);
    }

    @Test
    void booleansPrintAsWords() {
        Serial.print(true);
        Serial.println(false);

        assertThat(Serial.output).containsExactly("true", "false\n");
    }

    @Test
    void numbersPrintWithOrWithoutALineEnding() {
        Serial.print(42);
        Serial.println(7L);
        Serial.print(1.5f);
        Serial.println(2.5d);

        assertThat(Serial.output).containsExactly("42", "7\n", "1.5", "2.5\n");
    }

    @Test
    void stringsPrintWithOrWithoutALineEnding() {
        Serial.print("hi");
        Serial.println("there");

        assertThat(Serial.output).containsExactly("hi", "there\n");
    }
}

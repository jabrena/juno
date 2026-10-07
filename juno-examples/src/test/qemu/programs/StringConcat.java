package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Runtime oracle for javac's StringConcatFactory bootstrap across every supported operand kind. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public class StringConcat {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        boolean active = true;
        int count = -12;
        long total = 123456789L;
        float ratio = 1.5f;
        double precise = 2.5;
        double zero = 0.0;
        char marker = '!';
        String state = null;

        Serial.println("active=" + active);
        Serial.println("count=" + count);
        Serial.println("total=" + total);
        Serial.println("ratio=" + ratio);
        Serial.println("precise=" + precise);
        Serial.println("" + zero + zero + zero + zero + zero + zero + zero + zero);
        Serial.println("marker=" + marker);
        Serial.println("state=" + state);
    }
}

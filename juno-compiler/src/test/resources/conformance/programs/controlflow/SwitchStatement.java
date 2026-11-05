package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Switch statements: dense and sparse labels, extreme values, default placement, fall-through, no match, and narrow selectors. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class SwitchStatement {
    private SwitchStatement() {
    }

    static void dense(int x) {
        switch (x) {
            case 0:
                Serial.println(100);
                break;
            case 1:
                Serial.println(101);
                break;
            default:
                Serial.println(-1);
                break;
            case 2:
                Serial.println(102);
                break;
            case 3:
                Serial.println(103);
                break;
            case 4:
                Serial.println(104);
                break;
        }
    }

    static void sparse(int x) {
        switch (x) {
            case -1000000:
                Serial.println(1);
                break;
            case 7:
                Serial.println(2);
                break;
            case 1000000:
                Serial.println(3);
                break;
            case Integer.MIN_VALUE:
                Serial.println(4);
                break;
            case Integer.MAX_VALUE:
                Serial.println(5);
                break;
            default:
                Serial.println(0);
        }
    }

    static void defaultFirst(int x) {
        switch (x) {
            default:
                Serial.println(-9);
                break;
            case 1:
                Serial.println(1);
                break;
        }
    }

    static void fallThrough(int x) {
        switch (x) {
            case 1:
                Serial.println(1);
            case 2:
                Serial.println(2);
                break;
            case 3:
                Serial.println(3);
        }
    }

    static void narrow(char ch, byte b, short s) {
        switch (ch) {
            case 'a':
                Serial.println(10);
                break;
            default:
                Serial.println(11);
        }
        switch (b) {
            case -2:
                Serial.println(20);
                break;
            default:
                Serial.println(21);
        }
        switch (s) {
            case 300:
                Serial.println(30);
                break;
            default:
                Serial.println(31);
        }
    }

    public static void main(String[] args) {
        for (int x = -1; x <= 5; x++) {
            dense(x);
        }
        sparse(-1000000);
        sparse(7);
        sparse(Integer.MIN_VALUE);
        sparse(Integer.MAX_VALUE);
        sparse(8);
        defaultFirst(1);
        defaultFirst(4);
        fallThrough(1);
        fallThrough(2);
        fallThrough(3);
        fallThrough(9);
        narrow('a', (byte) -2, (short) 300);
        narrow('z', (byte) 5, (short) 1);
        int noMatch = 99;
        switch (noMatch) {
            case 1:
                Serial.println(-1);
                break;
        }
        Serial.println(42);
    }
}

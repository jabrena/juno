package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.Serial;

/** int arithmetic, shifts, bit operations, branches and loops. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class IntMath {
    static int collatz(int start) {
        int steps = 0;
        int value = start;
        while (value != 1) {
            value = (value & 1) == 0 ? value >> 1 : 3 * value + 1;
            steps++;
        }
        return steps;
    }

    static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    static int classify(int value) {
        switch (value % 5) {
            case 0:
                return 100;
            case 1:
                return 200;
            case 3:
                return 300;
            default:
                return -1;
        }
    }

    public static void main(String[] args) {
        Serial.println(7 / 2);
        Serial.println(-7 / 2);
        Serial.println(7 % -3);
        Serial.println(-7 % 3);
        Serial.println(Integer.MAX_VALUE + 1);
        Serial.println(Integer.MIN_VALUE / -1);
        Serial.println(0x7fffffff * 3);
        Serial.println(1 << 33);
        Serial.println(-256 >> 4);
        Serial.println(-256 >>> 28);
        Serial.println(0xf0f0 ^ 0x0ff0);
        Serial.println(~12345);
        Serial.println(collatz(27));
        Serial.println(gcd(1071, 462));
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += classify(i) * i;
        }
        Serial.println(sum);
        Serial.println(Math.abs(-42) + Math.max(3, 9) + Math.min(3, 9));
    }
}

package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.Serial;

/** exceptions crossing methods: custom types, nested finally, rethrow and try-with-resources. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Unwinding {
    static final class SensorException extends RuntimeException {
        final int code;

        SensorException(String message, int code) {
            super(message);
            this.code = code;
        }
    }

    static final class Lock implements AutoCloseable {
        private final int id;

        Lock(int id) {
            this.id = id;
            Serial.print("lock ");
            Serial.println(id);
        }

        @Override
        public void close() {
            Serial.print("unlock ");
            Serial.println(id);
        }
    }

    static int read(int channel) {
        if (channel > 2) {
            throw new SensorException("bad channel", channel);
        }
        if (channel < 0) {
            throw new IllegalArgumentException("negative channel");
        }
        return channel * 10;
    }

    static int sampleAll(int count) {
        int total = 0;
        for (int channel = 0; channel < count; channel++) {
            total += read(channel);
        }
        return total;
    }

    static int guarded(int count) {
        try (Lock lock = new Lock(count)) {
            return sampleAll(count);
        } finally {
            Serial.println("guarded finally");
        }
    }

    static int outer(int count) {
        try {
            return guarded(count);
        } catch (SensorException e) {
            Serial.print("sensor ");
            Serial.println(e.code);
            return -e.code;
        }
    }

    static int ratio(int total, int count) {
        return total / count;
    }

    static long wideRatio(long total, long count) {
        return total % count;
    }

    public static void main(String[] args) {
        try {
            Serial.println(ratio(100, 7));
            Serial.println(ratio(1, 0));
            Serial.println("unreachable");
        } catch (ArithmeticException e) {
            Serial.println(e.getMessage());
        }
        try {
            Serial.println(wideRatio(10_000_000_000L, 7L));
            Serial.println(wideRatio(5L, 0L));
        } catch (RuntimeException e) {
            Serial.println(e.getMessage());
        }
        Serial.println(outer(2));
        Serial.println(outer(5));
        try {
            sampleAll(-1);
            Serial.println("unreachable");
        } catch (IllegalArgumentException e) {
            Serial.println(e.getMessage());
        }
        try {
            try {
                read(9);
            } finally {
                Serial.println("inner finally");
            }
        } catch (RuntimeException e) {
            Serial.println(e.getMessage());
        }
        for (int i = 0; i < 3; i++) {
            try {
                Serial.println(read(i * 2));
            } catch (SensorException e) {
                Serial.println(e.code);
            }
        }
    }
}

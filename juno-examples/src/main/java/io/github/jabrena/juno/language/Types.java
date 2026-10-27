package io.github.jabrena.juno.language;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A catalogue of the Java value types Juno can lower: primitives, primitive arrays, strings, enums, records, final
 * classes, interfaces, UUIDs (including hardware-backed {@link UUID#randomUUID()}), date-time values and the
 * supported arbitrary-precision number types. The classic UNO R3 is included as a compiler target, although its 1 KiB
 * arena and 2 KiB SRAM require smaller programs than this complete catalogue.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoNanoEsp32Board.class, ArduinoUnoR3Board.class,
        ArduinoUnoR3WiFiRev2Board.class, M5StackCardputerBoard.class})
public final class Types {

    private enum Kind {
        PRIMITIVE,
        ARRAY,
        OBJECT
    }

    private record Point(int x, int y) {
    }

    private interface Measurable {
        int measure();
    }

    private static final class Box implements Measurable {
        private final int value;

        private Box(int value) {
            this.value = value;
        }

        @Override
        public int measure() {
            return value;
        }
    }

    private Types() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Serial.println("Types demo");
        primitives();
        arrays();
        objects();
        libraryTypes();
    }

    private static void primitives() {
        boolean enabled = true;
        byte small = 7;
        short medium = 32000;
        char letter = 'J';
        int count = 42;
        long distance = 3_000_000_000L;
        float voltage = 3.3f;
        double temperature = 21.5;

        Serial.println("boolean: " + enabled);
        Serial.println("byte: " + small);
        Serial.println("short: " + medium);
        Serial.println("char code: " + (int) letter);
        Serial.println("int: " + count);
        Serial.println("long: " + distance);
        Serial.println("float: " + voltage);
        Serial.println("double: " + temperature);
    }

    private static void arrays() {
        boolean[] booleans = new boolean[2];
        byte[] bytes = new byte[2];
        char[] chars = new char[2];
        short[] shorts = new short[2];
        int[] integers = new int[2];
        long[] longs = new long[2];
        float[] floats = new float[2];
        double[] doubles = new double[2];
        int[][] matrix = new int[2][2];

        booleans[0] = true;
        bytes[0] = 8;
        chars[0] = 'A';
        shorts[0] = 16;
        integers[0] = 32;
        longs[0] = 64L;
        floats[0] = 1.5f;
        doubles[0] = 2.5;
        matrix[1][1] = integers[0];

        Serial.println("arrays: boolean, byte, char, short, int, long, float, double");
        Serial.println("array values: " + booleans[0] + ", " + bytes[0] + ", " + chars[0] + ", " + shorts[0]
                + ", " + integers[0] + ", " + longs[0] + ", " + floats[0] + ", " + doubles[0]);
        Serial.println("matrix[1][1]: " + matrix[1][1]);
    }

    private static void objects() {
        Kind kind = Kind.OBJECT;
        Point point = new Point(3, 4);
        Measurable measurable = new Box(point.x() + point.y());
        String text = "Juno";
        StringBuilder builder = new StringBuilder(16);
        builder.append(text).append(" types");

        Serial.println("enum ordinal: " + kind.ordinal());
        Serial.println("record point: " + point.x() + "," + point.y());
        Serial.println("interface result: " + measurable.measure());
        Serial.println("String: " + text);
        Serial.println("StringBuilder: " + builder.toString());
    }

    private static void libraryTypes() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID randomUuid = UUID.randomUUID();
        BigInteger integer = BigInteger.valueOf(123456789L);
        MathContext context = new MathContext(8, RoundingMode.HALF_UP);
        BigDecimal decimal = new BigDecimal("12.50").multiply(BigDecimal.valueOf(2), context);
        LocalDateTime dateTime = LocalDateTime.MIN;

        Serial.println("UUID: " + uuid.toString());
        Serial.println("random UUID: " + randomUuid.toString());
        Serial.println("BigInteger: " + integer.toString());
        Serial.println("BigDecimal: " + decimal.toPlainString());
        Serial.println("MathContext precision: " + context.getPrecision());
        Serial.println("LocalDateTime year: " + dateTime.getYear());
    }
}

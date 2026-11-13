package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads the Seeed <a href="https://wiki.seeedstudio.com/Grove-GPS-Air530/">Grove GPS Air530</a>
 * over its one-way UART output. The module emits NMEA 0183 sentences at 9,600 baud by default;
 * connect its TX wire to the selected Grove signal pin, normally {@code D2} on a
 * {@link io.github.jabrena.juno.hardware.device.grove.GroveBaseShield}.
 *
 * <p>The reader uses a software UART, so it does not consume the board's USB serial console or
 * require an Arduino library. The sentence and position arrays belong to the caller and every
 * operation is allocation-free after construction.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class GroveGpsAir530 {
    public static final int DEFAULT_BAUD = 9_600;
    public static final int SENTENCE_CAPACITY = 96;
    public static final int POSITION_LENGTH = 5;
    public static final int LATITUDE_MICRODEGREES = 0;
    public static final int LONGITUDE_MICRODEGREES = 1;
    public static final int ALTITUDE_CENTIMETRES = 2;
    public static final int SATELLITES = 3;
    public static final int FIX_QUALITY = 4;

    private static final int BIT_MICROS = 1_000_000 / DEFAULT_BAUD;
    private static final int NO_DATA = -1;
    private static final int BAD_FRAME = -2;

    private final Gpio gpio;
    private final Clock clock;
    private final int receivePin;

    /** Creates a reader on the UNO's own GPIO and clock. */
    public GroveGpsAir530(int receivePin) {
        this(MCU.getGpio(), MCU.getClock(), receivePin);
    }

    /** Creates a reader whose GPIO and clock can be replaced by mocks in a unit test. */
    public GroveGpsAir530(Gpio gpio, Clock clock, int receivePin) {
        if (receivePin < 0) {
            throw new IllegalArgumentException("GPS receive pin must be non-negative");
        }
        this.gpio = gpio;
        this.clock = clock;
        this.receivePin = receivePin;
    }

    /** Configures the GPS TX pin as an idle-high software-UART input. Call once at startup. */
    public void begin() {
        gpio.pinMode(receivePin, gpio.getInputPullup());
    }

    /**
     * Reads the next complete NMEA sentence, without its line ending, into {@code sentence}.
     * Bytes before {@code '$'} are discarded so a call can start in the middle of the continuous
     * GPS stream.
     *
     * @param sentence caller-owned sentence buffer
     * @param sentenceLength writable capacity in {@code sentence}
     * @param timeoutMillis maximum time to wait for the next sentence start
     * @return sentence length, {@code 0} when no sentence started before the timeout, or {@code -1}
     *         when the sentence did not fit
     */
    public int readSentence(byte[] sentence, int sentenceLength, int timeoutMillis) {
        checkLength(sentence, sentenceLength, "sentenceLength");
        int timeout = timeoutMicros(timeoutMillis);
        int deadline = timeout == 0 ? 0 : clock.micros() + timeout;
        int value;
        do {
            value = readByteUntil(deadline);
            if (value == NO_DATA) {
                return 0;
            }
        } while (value != '$');

        int length = 1;
        sentence[0] = '$';
        boolean overflow = false;
        while (true) {
            value = readByteUntil(0);
            if (value < 0) {
                return value == NO_DATA ? 0 : -1;
            }
            if (value == '\n') {
                return overflow ? -1 : length;
            }
            if (value == '\r') {
                continue;
            }
            if (length < sentenceLength) {
                sentence[length++] = (byte) value;
            } else {
                overflow = true;
            }
        }
    }

    /**
     * Reads sentences until a checksum-valid GGA fix is received and writes its values to
     * {@code position}. Latitude and longitude are signed millionths of a degree, altitude is in
     * centimetres, and the last two entries contain the satellite count and NMEA fix quality.
     *
     * <p>The caller reuses both arrays between calls. The position is left unchanged when the
     * timeout expires or no valid fix was received.
     */
    public boolean readPosition(byte[] sentence, int sentenceLength, int[] position, int positionLength,
            int timeoutMillis) {
        checkLength(position, positionLength, "positionLength");
        int started = clock.millis();
        while (timeoutMillis <= 0 || clock.millis() - started < timeoutMillis) {
            int remaining = timeoutMillis <= 0 ? 0 : timeoutMillis - (clock.millis() - started);
            int length = readSentence(sentence, sentenceLength, remaining);
            if (length > 0 && parseGga(sentence, length, position, positionLength)) {
                return true;
            }
            if (timeoutMillis > 0 && clock.millis() - started >= timeoutMillis) {
                return false;
            }
        }
        return false;
    }

    /** Decodes one checksum-valid GGA sentence into a caller-owned five-int position array. */
    public boolean parseGga(byte[] sentence, int sentenceLength, int[] position, int positionLength) {
        checkLength(sentence, sentenceLength, "sentenceLength");
        checkLength(position, positionLength, "positionLength");
        int checksum = checksumEnd(sentence, sentenceLength);
        if (checksum < 0 || !isGga(sentence, checksum)) {
            return false;
        }
        int fieldsStart = 7;
        int latitudeStart = fieldStart(sentence, checksum, fieldsStart, 1);
        int latitudeEnd = fieldEnd(sentence, checksum, fieldsStart, 1);
        int northSouthStart = fieldStart(sentence, checksum, fieldsStart, 2);
        int longitudeStart = fieldStart(sentence, checksum, fieldsStart, 3);
        int longitudeEnd = fieldEnd(sentence, checksum, fieldsStart, 3);
        int eastWestStart = fieldStart(sentence, checksum, fieldsStart, 4);
        int fixStart = fieldStart(sentence, checksum, fieldsStart, 5);
        int satellitesStart = fieldStart(sentence, checksum, fieldsStart, 6);
        int altitudeStart = fieldStart(sentence, checksum, fieldsStart, 8);
        if (latitudeStart < 0 || latitudeEnd < 0 || northSouthStart < 0 || longitudeStart < 0
                || longitudeEnd < 0 || eastWestStart < 0 || fixStart < 0 || satellitesStart < 0 || altitudeStart < 0) {
            return false;
        }
        int fix = parseInteger(sentence, fixStart, fieldEnd(sentence, checksum, fieldsStart, 5));
        int latitude = coordinateMicrodegrees(sentence, latitudeStart, latitudeEnd);
        int longitude = coordinateMicrodegrees(sentence, longitudeStart, longitudeEnd);
        int satellites = parseInteger(sentence, satellitesStart, fieldEnd(sentence, checksum, fieldsStart, 6));
        int altitude = decimalScaled(sentence, altitudeStart, fieldEnd(sentence, checksum, fieldsStart, 8), 100);
        if (fix <= 0 || latitude == Integer.MIN_VALUE || longitude == Integer.MIN_VALUE
                || satellites < 0 || altitude == Integer.MIN_VALUE
                || (sentence[northSouthStart] != 'N' && sentence[northSouthStart] != 'S')
                || (sentence[eastWestStart] != 'E' && sentence[eastWestStart] != 'W')) {
            return false;
        }
        if (sentence[northSouthStart] == 'S') {
            latitude = -latitude;
        }
        if (sentence[eastWestStart] == 'W') {
            longitude = -longitude;
        }
        position[LATITUDE_MICRODEGREES] = latitude;
        position[LONGITUDE_MICRODEGREES] = longitude;
        position[ALTITUDE_CENTIMETRES] = altitude;
        position[SATELLITES] = satellites;
        position[FIX_QUALITY] = fix;
        return true;
    }

    private int readByteUntil(int deadline) {
        while (gpio.digitalRead(receivePin)) {
            if (deadline != 0 && clock.micros() - deadline >= 0) {
                return NO_DATA;
            }
        }
        Delay.micros(BIT_MICROS + BIT_MICROS / 2);
        int value = 0;
        for (int bit = 0; bit < 8; bit++) {
            if (gpio.digitalRead(receivePin)) {
                value |= 1 << bit;
            }
            Delay.micros(BIT_MICROS);
        }
        if (!gpio.digitalRead(receivePin)) {
            return BAD_FRAME;
        }
        return value;
    }

    private static boolean isGga(byte[] sentence, int checksum) {
        return checksum >= 7 && sentence[0] == '$' && sentence[3] == 'G' && sentence[4] == 'G'
                && sentence[5] == 'A' && sentence[6] == ',';
    }

    private static int checksumEnd(byte[] sentence, int length) {
        if (length < 9 || sentence[0] != '$') {
            return -1;
        }
        int star = length - 3;
        if (sentence[star] != '*') {
            return -1;
        }
        int expected = hex(sentence[star + 1]) * 16 + hex(sentence[star + 2]);
        if (expected < 0) {
            return -1;
        }
        int actual = 0;
        for (int index = 1; index < star; index++) {
            actual ^= sentence[index] & 0xFF;
        }
        return actual == expected ? star : -1;
    }

    private static int fieldStart(byte[] sentence, int end, int firstField, int field) {
        int start = firstField;
        for (int index = 0; index < field; index++) {
            while (start < end && sentence[start] != ',') {
                start++;
            }
            start++;
        }
        return start < end ? start : -1;
    }

    private static int fieldEnd(byte[] sentence, int end, int firstField, int field) {
        int start = fieldStart(sentence, end, firstField, field);
        if (start < 0) {
            return -1;
        }
        for (int index = start; index < end; index++) {
            if (sentence[index] == ',') {
                return index;
            }
        }
        return end;
    }

    private static int parseInteger(byte[] value, int start, int end) {
        if (start < 0 || end <= start) {
            return -1;
        }
        int result = 0;
        for (int index = start; index < end; index++) {
            int digit = value[index] - '0';
            if (digit < 0 || digit > 9) {
                return -1;
            }
            result = result * 10 + digit;
        }
        return result;
    }

    private static int coordinateMicrodegrees(byte[] value, int start, int end) {
        int dot = start;
        while (dot < end && value[dot] != '.') {
            dot++;
        }
        int degreeDigits = dot - start - 2;
        if (degreeDigits <= 0) {
            return Integer.MIN_VALUE;
        }
        int degrees = parseInteger(value, start, start + degreeDigits);
        int minutes = decimalScaled(value, start + degreeDigits, end, 1_000_000);
        if (degrees < 0 || minutes < 0 || minutes >= 60_000_000) {
            return Integer.MIN_VALUE;
        }
        return degrees * 1_000_000 + (minutes + 30) / 60;
    }

    private static int decimalScaled(byte[] value, int start, int end, int scale) {
        if (start >= end) {
            return Integer.MIN_VALUE;
        }
        boolean negative = value[start] == '-';
        if (negative) {
            start++;
        }
        int wholeEnd = start;
        while (wholeEnd < end && value[wholeEnd] != '.') {
            wholeEnd++;
        }
        int whole = parseInteger(value, start, wholeEnd);
        if (whole < 0) {
            return Integer.MIN_VALUE;
        }
        int result = whole * scale;
        if (wholeEnd < end) {
            int place = scale / 10;
            for (int index = wholeEnd + 1; index < end && place > 0; index++) {
                int digit = value[index] - '0';
                if (digit < 0 || digit > 9) {
                    return Integer.MIN_VALUE;
                }
                result += digit * place;
                place /= 10;
            }
        }
        return negative ? -result : result;
    }

    private static int hex(byte value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        return -1;
    }

    private static int timeoutMicros(int timeoutMillis) {
        if (timeoutMillis <= 0) {
            return 0;
        }
        return timeoutMillis > Integer.MAX_VALUE / 1_000 ? Integer.MAX_VALUE : timeoutMillis * 1_000;
    }

    private static void checkLength(byte[] buffer, int length, String name) {
        if (length < 1 || length > buffer.length) {
            throw new IllegalArgumentException(name + " must be positive and fit its buffer");
        }
    }

    private static void checkLength(int[] buffer, int length, String name) {
        if (length < POSITION_LENGTH || length > buffer.length) {
            throw new IllegalArgumentException(name + " must be at least 5 and fit its buffer");
        }
    }
}

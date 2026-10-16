package io.github.jabrena.juno.usecases.supabase;

/**
 * Writes one {@code sensor-data} row as a JSON object into a caller-owned buffer, for example
 * {@code {"temp1":23.5,"temp2":24.0,"temp3":22.8,"ts":"2026-10-16T12:00:00Z"}}. Temperatures are degrees Celsius,
 * written rounded to one decimal; {@link #NO_READING}, or any value below absolute zero, is written as JSON
 * {@code null}. The timestamp is UTC. The instance is created once at startup and every {@link #write} reuses the same
 * buffer.
 */
final class SensorDataRow {
    /** Marks a temperature whose sensor did not produce a valid reading. */
    static final float NO_READING = -1000.0f;

    private static final float ABSOLUTE_ZERO_CELSIUS = -273.15f;

    private final byte[] buffer;
    private final int capacity;
    private int length;

    SensorDataRow(byte[] buffer, int capacity) {
        this.buffer = buffer;
        this.capacity = capacity;
    }

    /** The buffer {@link #write} fills. */
    byte[] buffer() {
        return buffer;
    }

    /** Writes the row and returns its length, or {@code -1} when it does not fit the buffer. */
    int write(float temp1, float temp2, float temp3,
            int year, int month, int day, int hour, int minute, int second) {
        length = 0;
        appendAscii("{\"temp1\":");
        appendCelsius(temp1);
        appendAscii(",\"temp2\":");
        appendCelsius(temp2);
        appendAscii(",\"temp3\":");
        appendCelsius(temp3);
        appendAscii(",\"ts\":\"");
        appendDigits(year, 4);
        appendByte('-');
        appendDigits(month, 2);
        appendByte('-');
        appendDigits(day, 2);
        appendByte('T');
        appendDigits(hour, 2);
        appendByte(':');
        appendDigits(minute, 2);
        appendByte(':');
        appendDigits(second, 2);
        appendAscii("Z\"}");
        return length;
    }

    private void appendCelsius(float celsius) {
        if (celsius < ABSOLUTE_ZERO_CELSIUS) {
            appendAscii("null");
            return;
        }
        boolean negative = celsius < 0.0f;
        int tenths = (int) ((negative ? -celsius : celsius) * 10.0f + 0.5f);
        if (negative && tenths > 0) {
            appendByte('-');
        }
        int whole = tenths / 10;
        int divisor = 1;
        while (whole / divisor >= 10) {
            divisor = divisor * 10;
        }
        while (divisor > 0) {
            appendByte('0' + whole / divisor % 10);
            divisor = divisor / 10;
        }
        appendByte('.');
        appendByte('0' + tenths % 10);
    }

    private void appendDigits(int value, int width) {
        int divisor = 1;
        int index = 1;
        while (index < width) {
            divisor = divisor * 10;
            index = index + 1;
        }
        while (divisor > 0) {
            appendByte('0' + value / divisor % 10);
            divisor = divisor / 10;
        }
    }

    private void appendAscii(String value) {
        int count = value.length();
        int index = 0;
        while (index < count) {
            appendByte(value.charAt(index));
            index = index + 1;
        }
    }

    private void appendByte(int value) {
        if (length < 0) {
            return;
        }
        if (length >= capacity) {
            length = -1;
            return;
        }
        buffer[length] = (byte) value;
        length = length + 1;
    }
}

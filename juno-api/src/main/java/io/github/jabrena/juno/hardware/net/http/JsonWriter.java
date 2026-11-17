package io.github.jabrena.juno.hardware.net.http;

/**
 * SPIKE (#285): writes JSON into a caller-owned {@code byte[]}, placing commas and escaping strings, so request and
 * response bodies need no hand-written quotes and no {@code StringBuilder}. Create one writer at startup and
 * {@link #reset()} it per body: writing allocates nothing.
 *
 * <p>Output that does not fit the buffer is dropped and {@link #overflowed()} turns true. Nesting deeper than
 * {@value #MAX_DEPTH} levels, or a name outside an object, also marks the writer overflowed instead of throwing.
 */
public final class JsonWriter {
    private static final int MAX_DEPTH = 31;

    private final byte[] out;
    private int length;
    private int depth;
    /** Bit {@code d} set: the container at depth {@code d} already holds an item, so the next one needs a comma. */
    private int hasItem;
    /** Bit {@code d} set: the container at depth {@code d} is an object. */
    private int inObject;
    private boolean afterName;
    private boolean overflow;

    public JsonWriter(byte[] out) {
        this.out = out;
    }

    /** Empties the writer so the next body starts at the beginning of the buffer. */
    public JsonWriter reset() {
        length = 0;
        depth = 0;
        hasItem = 0;
        inObject = 0;
        afterName = false;
        overflow = false;
        return this;
    }

    public JsonWriter beginObject() {
        return open('{', true);
    }

    public JsonWriter endObject() {
        return close('}');
    }

    public JsonWriter beginArray() {
        return open('[', false);
    }

    public JsonWriter endArray() {
        return close(']');
    }

    /** The member name for the next value; only valid directly inside an object. */
    public JsonWriter name(String name) {
        if (depth == 0 || (inObject & (1 << depth)) == 0 || afterName) {
            overflow = true;
            return this;
        }
        separator();
        string(name);
        put(':');
        afterName = true;
        return this;
    }

    public JsonWriter value(String value) {
        separator();
        string(value);
        return this;
    }

    public JsonWriter value(int value) {
        return value((long) value);
    }

    public JsonWriter value(long value) {
        separator();
        if (value < 0) {
            put('-');
        }
        long magnitude = value;
        long divisor = 1;
        while (magnitude / divisor > 9 || magnitude / divisor < -9) {
            divisor *= 10;
        }
        while (divisor > 0) {
            long digit = magnitude / divisor % 10;
            put((char) ('0' + (digit < 0 ? -digit : digit)));
            divisor /= 10;
        }
        return this;
    }

    public JsonWriter value(boolean value) {
        separator();
        return word(value ? "true" : "false");
    }

    public JsonWriter nullValue() {
        separator();
        return word("null");
    }

    /** The number of JSON bytes written to the buffer. */
    public int length() {
        return length;
    }

    /** Whether some output was dropped or the calls did not form valid JSON. */
    public boolean overflowed() {
        return overflow;
    }

    private JsonWriter open(char bracket, boolean object) {
        separator();
        put(bracket);
        if (depth == MAX_DEPTH) {
            overflow = true;
            return this;
        }
        depth++;
        hasItem &= ~(1 << depth);
        inObject = object ? inObject | (1 << depth) : inObject & ~(1 << depth);
        return this;
    }

    private JsonWriter close(char bracket) {
        if (depth == 0 || afterName) {
            overflow = true;
            return this;
        }
        put(bracket);
        depth--;
        return this;
    }

    /** Writes the comma between items, unless the value follows its member name. */
    private void separator() {
        if (afterName) {
            afterName = false;
            return;
        }
        if ((hasItem & (1 << depth)) != 0) {
            put(',');
        }
        hasItem |= 1 << depth;
    }

    private void string(String text) {
        put('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                put('\\');
                put(c);
            } else if (c < 0x20) {
                put('\\');
                put('u');
                put('0');
                put('0');
                put(hex(c >> 4));
                put(hex(c & 0xF));
            } else {
                put(c);
            }
        }
        put('"');
    }

    private JsonWriter word(String word) {
        for (int i = 0; i < word.length(); i++) {
            put(word.charAt(i));
        }
        return this;
    }

    private static char hex(int nibble) {
        return (char) (nibble < 10 ? '0' + nibble : 'a' + nibble - 10);
    }

    private void put(char c) {
        if (length < out.length) {
            out[length] = (byte) c;
            length++;
        } else {
            overflow = true;
        }
    }
}

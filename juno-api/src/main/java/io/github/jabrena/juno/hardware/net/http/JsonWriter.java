package io.github.jabrena.juno.hardware.net.http;

/**
 * Writes JSON into a caller-owned {@code byte[]}, placing commas and escaping strings, so request and response bodies
 * need no hand-written quotes. Create one writer at startup and {@link #reset()} it per body: writing allocates
 * nothing. {@link #toString()} copies the body into a {@code String} for APIs that take one, such as
 * {@link HttpServer#respond(int, String, String)} or {@code HttpRequest.BodyPublishers.ofString}.
 *
 * <pre>{@code
 * JsonWriter json = new JsonWriter(new byte[64]);
 * json.reset().beginObject().name("uptime_ms").value(clock.millis()).name("ok").value(true).endObject();
 * HttpServer.respond(HttpStatus.OK, "application/json", json.toString());
 * }</pre>
 *
 * <p>Output that does not fit the buffer is dropped and {@link #overflowed()} turns true. Nesting deeper than
 * {@value #MAX_DEPTH} levels, a name outside an object, an unbalanced close, or a NaN or infinite {@code double}
 * (JSON has no literal for them) also mark the writer overflowed instead of throwing.
 */
public final class JsonWriter {
    private static final int MAX_DEPTH = 31;

    private final byte[] out;
    /** Scratch for the digits of a number, so writing one allocates nothing. */
    private final byte[] digits = new byte[20];
    private int length;
    private int depth;
    /** Bit {@code d} set: the container at depth {@code d} already holds an item, so the next one needs a comma. */
    private int hasItem;
    /** Bit {@code d} set: the container at depth {@code d} is an object. */
    private int inObject;
    private boolean afterName;
    private boolean overflow;

    /** A writer that fills {@code out} from its first byte; allocate it once and {@link #reset()} it per body. */
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

    /** Opens an object with an opening brace, preceded by a comma when needed. */
    public JsonWriter beginObject() {
        return open('{', true);
    }

    /** Closes the innermost object with a closing brace. */
    public JsonWriter endObject() {
        return close('}');
    }

    /** Opens an array: {@code '['}, preceded by a comma when needed. */
    public JsonWriter beginArray() {
        return open('[', false);
    }

    /** Closes the innermost array with {@code ']'}. */
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
        put('"');
        for (int i = 0; i < name.length(); i++) {
            escaped(name.charAt(i));
        }
        put('"');
        put(':');
        afterName = true;
        return this;
    }

    /** A JSON string, with quotes, backslashes and control characters escaped. */
    public JsonWriter value(String value) {
        separator();
        put('"');
        for (int i = 0; i < value.length(); i++) {
            escaped(value.charAt(i));
        }
        put('"');
        return this;
    }

    /** The first {@code count} characters of {@code text} as a JSON string, escaped like {@link #value(String)}. */
    public JsonWriter value(char[] text, int count) {
        separator();
        put('"');
        for (int i = 0; i < count; i++) {
            escaped(text[i]);
        }
        put('"');
        return this;
    }

    /** A JSON number written in decimal. */
    public JsonWriter value(int value) {
        return value((long) value);
    }

    /** A JSON number written in decimal, {@link Long#MIN_VALUE} included. */
    public JsonWriter value(long value) {
        separator();
        integer(value);
        return this;
    }

    /**
     * A JSON number with up to 15 significant digits, written as {@link Double#toString(double)} lays it out:
     * {@code 21.4}, {@code 100.0}, {@code 0.001}, {@code 1.5E300}. A NaN or infinite value writes nothing and marks the
     * writer overflowed.
     */
    public JsonWriter value(double value) {
        if (!(value >= -Double.MAX_VALUE && value <= Double.MAX_VALUE)) { // NaN and the infinities
            overflow = true;
            return this;
        }
        separator();
        double magnitude = value;
        if (value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)) {
            put('-');
            magnitude = -value;
        }
        if (magnitude == 0.0) {
            return word("0.0");
        }
        JsonDoubles.write(this, magnitude, digits);
        return this;
    }

    /** The JSON literal {@code true} or {@code false}. */
    public JsonWriter value(boolean value) {
        separator();
        return word(value ? "true" : "false");
    }

    /** The JSON literal {@code null}. */
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

    /** The bytes written so far as a new arena {@code String}, for APIs that take a body as text. */
    @Override
    public String toString() {
        StringBuilder copy = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            copy.append((char) (out[i] & 0xFF));
        }
        return copy.toString();
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

    /** One string character, with JSON's short escapes and <code>&#92;u00XX</code> for other control characters. */
    private void escaped(char c) {
        if (c == '"' || c == '\\') {
            put('\\');
            put(c);
        } else if (c >= 0x20) {
            put(c);
        } else if (c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\f') {
            put('\\');
            put(shortEscape(c));
        } else {
            put('\\');
            put('u');
            put('0');
            put('0');
            put(hex(c >> 4));
            put(hex(c & 0xF));
        }
    }

    private static char shortEscape(char c) {
        if (c == '\n') {
            return 'n';
        }
        if (c == '\r') {
            return 'r';
        }
        if (c == '\t') {
            return 't';
        }
        return c == '\b' ? 'b' : 'f';
    }

    /** {@code value} in decimal, through {@link #digits} from the last digit back, so no divisor varies. */
    private void integer(long value) {
        if (value < 0) {
            put('-');
        }
        int count = 0;
        long rest = value;
        do {
            long digit = rest % 10;
            digits[count] = (byte) (digit < 0 ? -digit : digit);
            count++;
            rest /= 10;
        } while (rest != 0);
        while (count > 0) {
            count--;
            put(digit(count));
        }
    }

    private char digit(int index) {
        return (char) ('0' + digits[index]);
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

    /** Appends one byte, or marks the writer overflowed when the buffer is full. */
    void put(char c) {
        if (length < out.length) {
            out[length] = (byte) c;
            length++;
        } else {
            overflow = true;
        }
    }
}

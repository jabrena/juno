package io.github.jabrena.juno.usecases.supabase.commons;

/** Builds a bounded Supabase REST path and query without allocating. */
final class SupabasePathBuilder {
    private final byte[] buffer;
    private final int capacity;
    private int length;
    private boolean valid;

    SupabasePathBuilder(byte[] buffer, int capacity) {
        this.buffer = buffer;
        this.capacity = capacity;
    }

    void reset(boolean initiallyValid) {
        length = 0;
        valid = initiallyValid;
    }

    boolean valid() {
        return valid;
    }

    byte[] buffer() {
        return buffer;
    }

    int length() {
        return length;
    }

    void appendTable(String table) {
        int tableLength = table.length();
        if (tableLength == 0) {
            valid = false;
            return;
        }
        int index = 0;
        while (index < tableLength && valid) {
            int character = table.charAt(index);
            if (!SupabaseSyntax.identifierCharacter(character) && character != '-') {
                valid = false;
            } else {
                appendByte(character);
            }
            index = index + 1;
        }
    }

    void appendParameterName(String name, boolean queryStarted) {
        appendByte(queryStarted ? '&' : '?');
        appendIdentifier(name);
        appendByte('=');
    }

    void appendEncoded(String value) {
        int valueLength = value.length();
        int index = 0;
        while (index < valueLength && valid) {
            int character = value.charAt(index);
            appendEncodedCharacter(character);
            index = index + 1;
        }
    }

    void appendEncoded(byte[] value, int valueLength) {
        int index = 0;
        while (index < valueLength && valid) {
            appendEncodedCharacter(value[index] & 255);
            index = index + 1;
        }
    }

    void appendPositiveInt(int value) {
        int divisor = 1;
        while (value / divisor >= 10 && divisor <= 100_000_000) {
            divisor = divisor * 10;
        }
        while (divisor > 0) {
            appendByte('0' + value / divisor % 10);
            divisor = divisor / 10;
        }
    }

    void appendAscii(String value) {
        int valueLength = value.length();
        int index = 0;
        while (index < valueLength && valid) {
            appendByte(value.charAt(index));
            index = index + 1;
        }
    }

    private void appendIdentifier(String identifier) {
        int identifierLength = identifier.length();
        if (identifierLength == 0) {
            valid = false;
            return;
        }
        int index = 0;
        while (index < identifierLength && valid) {
            int character = identifier.charAt(index);
            if (!SupabaseSyntax.identifierCharacter(character)) {
                valid = false;
            } else {
                appendByte(character);
            }
            index = index + 1;
        }
    }

    private void appendEncodedCharacter(int character) {
        if (character > 127) {
            valid = false;
        } else if (SupabaseSyntax.unreserved(character)) {
            appendByte(character);
        } else {
            appendByte('%');
            appendByte(SupabaseSyntax.hex(character >> 4));
            appendByte(SupabaseSyntax.hex(character & 15));
        }
    }

    private void appendByte(int value) {
        if (length >= capacity) {
            valid = false;
            return;
        }
        buffer[length] = (byte) value;
        length = length + 1;
    }
}

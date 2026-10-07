package io.github.jabrena.juno.api.net.weather;

/** Byte-string helpers for the text {@link WeatherTFT} receives over the network. */
final class AsciiText {
    private AsciiText() {
    }

    /** Transliterates UTF-8 Latin-1 letters (e.g. "ó") to ASCII in place, returning the new length. */
    static int toAscii(byte[] text, int length) {
        int out = 0;
        int i = 0;
        while (i < length) {
            int value = text[i] & 0xFF;
            if (value == 0xC3 && i + 1 < length) {
                text[out] = (byte) latin1ToAscii(text[i + 1] & 0xFF);
                i = i + 2;
            } else if (value >= 0x80) {
                text[out] = '?';
                i = i + 1;
                while (i < length && (text[i] & 0xC0) == 0x80) {
                    i = i + 1;
                }
            } else {
                text[out] = (byte) value;
                i = i + 1;
            }
            out = out + 1;
        }
        return out;
    }

    // Second byte of a UTF-8 "À".."ÿ" letter (0xC3 0x80..0xBF) to its unaccented ASCII letter.
    private static int latin1ToAscii(int value) {
        int letter = value & 0x1F;
        int base = 'A';
        if (value >= 0xA0) {
            base = 'a';
        }
        if (letter <= 0x05) {
            return base;
        }
        if (letter == 0x07) {
            return base + ('c' - 'a');
        }
        if (letter >= 0x08 && letter <= 0x0B) {
            return base + ('e' - 'a');
        }
        if (letter >= 0x0C && letter <= 0x0F) {
            return base + ('i' - 'a');
        }
        if (letter == 0x11) {
            return base + ('n' - 'a');
        }
        if (letter >= 0x12 && letter <= 0x16 || letter == 0x18) {
            return base + ('o' - 'a');
        }
        if (letter >= 0x19 && letter <= 0x1C) {
            return base + ('u' - 'a');
        }
        if (letter == 0x1D || letter == 0x1F) {
            return base + ('y' - 'a');
        }
        return '?';
    }

    static int length(byte[] text) {
        int length = 0;
        while (text[length] != 0) {
            length = length + 1;
        }
        return length;
    }
}

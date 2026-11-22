package io.github.jabrena.juno.jdk.util;

/** Bounded board implementation of the JDK MIME Base64 decoder used for a PEM private key. */
public final class Base64 {
    private Base64() {
    }

    public static Decoder getMimeDecoder() {
        return new Decoder();
    }

    public static final class Decoder {
        private int decodedLength;
        private Decoder() {
        }

        public byte[] decode(String source) {
            int digits = 0;
            int padding = 0;
            for (int index = 0; index < source.length(); index++) {
                char value = source.charAt(index);
                if (digit(value) >= 0) {
                    digits++;
                } else if (value == '=') {
                    padding++;
                }
            }
            if ((digits + padding) % 4 != 0 || padding > 2) {
                throw new IllegalArgumentException("Invalid Base64 private key");
            }
            byte[] decoded = new byte[2048];
            int bits = 0;
            int count = 0;
            int written = 0;
            int expected = (digits + padding) / 4 * 3 - padding;
            for (int index = 0; index < source.length(); index++) {
                int value = digit(source.charAt(index));
                if (value < 0) continue;
                bits = (bits << 6) | value;
                count += 6;
                if (count >= 8) {
                    count -= 8;
                    if (written < expected) decoded[written++] = (byte) (bits >> count);
                }
            }
            decodedLength = written;
            return decoded;
        }

        public int decodedLength() {
            return decodedLength;
        }

        private static int digit(char value) {
            if (value >= 'A' && value <= 'Z') return value - 'A';
            if (value >= 'a' && value <= 'z') return value - 'a' + 26;
            if (value >= '0' && value <= '9') return value - '0' + 52;
            if (value == '+') return 62;
            if (value == '/') return 63;
            return -1;
        }
    }
}

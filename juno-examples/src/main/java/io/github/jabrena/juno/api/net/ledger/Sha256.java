package io.github.jabrena.juno.api.net.ledger;

/**
 * SHA-256 (FIPS 180-4) written against the subset Juno compiles: int arithmetic, shifts and
 * caller-owned arrays, with the round constants kept in flash as a read-only table. It hashes one
 * message held entirely in a buffer, which is all a ledger block needs.
 */
final class Sha256 {
    static final int DIGEST_SIZE = 32;
    /** Size of the caller-owned {@code byte[]} block buffer. */
    static final int BLOCK_SIZE = 64;
    /** Size of the caller-owned {@code int[]} message-schedule buffer. */
    static final int SCHEDULE_SIZE = 64;
    /** Size of the caller-owned {@code int[]} state buffer. */
    static final int STATE_SIZE = 8;

    private static final int[] K = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    };

    private Sha256() {
    }

    /**
     * Writes the SHA-256 digest of {@code message[0..length)} into {@code digest[0..32)}. {@code block},
     * {@code schedule} and {@code state} are scratch buffers of {@link #BLOCK_SIZE}, {@link #SCHEDULE_SIZE}
     * and {@link #STATE_SIZE} entries.
     */
    static void digest(byte[] message, int length, byte[] digest, byte[] block, int[] schedule, int[] state) {
        state[0] = 0x6a09e667;
        state[1] = 0xbb67ae85;
        state[2] = 0x3c6ef372;
        state[3] = 0xa54ff53a;
        state[4] = 0x510e527f;
        state[5] = 0x9b05688c;
        state[6] = 0x1f83d9ab;
        state[7] = 0x5be0cd19;

        int offset = 0;
        while (length - offset >= BLOCK_SIZE) {
            for (int i = 0; i < BLOCK_SIZE; i++) {
                block[i] = message[offset + i];
            }
            compress(block, schedule, state);
            offset = offset + BLOCK_SIZE;
        }

        int tail = length - offset;
        for (int i = 0; i < BLOCK_SIZE; i++) {
            block[i] = i < tail ? message[offset + i] : 0;
        }
        block[tail] = (byte) 0x80;
        if (tail >= BLOCK_SIZE - 8) {
            compress(block, schedule, state);
            for (int i = 0; i < BLOCK_SIZE; i++) {
                block[i] = 0;
            }
        }
        long bits = (long) length * 8;
        for (int i = 0; i < 8; i++) {
            block[BLOCK_SIZE - 1 - i] = (byte) (bits >>> (8 * i));
        }
        compress(block, schedule, state);

        for (int i = 0; i < STATE_SIZE; i++) {
            FactorProtocol.writeInt(digest, i * 4, state[i]);
        }
    }

    private static void compress(byte[] block, int[] w, int[] state) {
        for (int i = 0; i < 16; i++) {
            w[i] = FactorProtocol.readInt(block, i * 4);
        }
        for (int i = 16; i < 64; i++) {
            int w15 = w[i - 15];
            int w2 = w[i - 2];
            int s0 = rotate(w15, 7) ^ rotate(w15, 18) ^ (w15 >>> 3);
            int s1 = rotate(w2, 17) ^ rotate(w2, 19) ^ (w2 >>> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }

        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int e = state[4];
        int f = state[5];
        int g = state[6];
        int h = state[7];
        for (int i = 0; i < 64; i++) {
            int sum1 = rotate(e, 6) ^ rotate(e, 11) ^ rotate(e, 25);
            int choose = (e & f) ^ (~e & g);
            int t1 = h + sum1 + choose + K[i] + w[i];
            int sum0 = rotate(a, 2) ^ rotate(a, 13) ^ rotate(a, 22);
            int majority = (a & b) ^ (a & c) ^ (b & c);
            int t2 = sum0 + majority;
            h = g;
            g = f;
            f = e;
            e = d + t1;
            d = c;
            c = b;
            b = a;
            a = t1 + t2;
        }
        state[0] = state[0] + a;
        state[1] = state[1] + b;
        state[2] = state[2] + c;
        state[3] = state[3] + d;
        state[4] = state[4] + e;
        state[5] = state[5] + f;
        state[6] = state[6] + g;
        state[7] = state[7] + h;
    }

    private static int rotate(int value, int distance) {
        return (value >>> distance) | (value << (32 - distance));
    }
}

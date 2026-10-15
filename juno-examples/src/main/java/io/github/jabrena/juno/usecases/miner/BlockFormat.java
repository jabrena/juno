package io.github.jabrena.juno.usecases.miner;

import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.games.blockchain.Factorizer;
import io.github.jabrena.juno.games.blockchain.Sha256;

/**
 * Byte layout of a {@link MinerGame} block: Bitcoin's 80-byte header followed by one payload.
 *
 * <pre>
 * header   0  version(4)
 *          4  previous block hash(32)
 *         36  payload hash(32)      SHA-256 of the payload: the Merkle root of a one-entry block
 *         68  time(4)               seconds of network time
 *         72  bits(4)               required leading zero bits of the header hash
 *         76  nonce(4)
 * payload 80  miner id(4)
 *         84  n(8)                  the bounty: derived from the previous hash; the miner chooses only its bit length
 *         92  factor count(1)
 *         93  (prime(8), exponent(1)) x count
 * </pre>
 *
 * All fields are big-endian. Unlike Bitcoin, the proof-of-work is a single SHA-256 rather than two.
 */
public final class BlockFormat {
    static final int VERSION = 1;
    static final int HEADER_SIZE = 80;
    static final int PREVIOUS = 4;
    static final int PAYLOAD_HASH = 36;
    static final int TIME = 68;
    static final int BITS = 72;
    static final int NONCE = 76;

    static final int PAYLOAD = HEADER_SIZE;
    public static final int MINER = PAYLOAD;
    public static final int NUMBER = PAYLOAD + 4;
    public static final int COUNT = PAYLOAD + 12;
    public static final int FACTORS = PAYLOAD + 13;
    public static final int MAX_SIZE = FACTORS + Factorizer.MAX_FACTORS * 9;

    /** Bit length of the bounty MinerGame mines, the number each of its blocks has to factor. */
    static final int BOUNTY_BITS = 40;
    /** The shortest bounty a block may carry: a 1-bit number has no prime factors. */
    public static final int MIN_BOUNTY_BITS = 2;
    /** The longest bounty a block may carry, the limit of {@code Factorizer}. */
    public static final int MAX_BOUNTY_BITS = 62;

    private BlockFormat() {
    }

    /** Size of the block at {@code offset}; only meaningful once {@link #wellFormed} holds. */
    static int size(byte[] buffer, int offset) {
        return FACTORS + Bytes.unsigned(buffer[offset + COUNT]) * 9;
    }

    static boolean wellFormed(byte[] buffer, int offset) {
        return Bytes.readInt(buffer, offset) == VERSION
                && Bytes.unsigned(buffer[offset + COUNT]) <= Factorizer.MAX_FACTORS;
    }

    /** The {@link #BOUNTY_BITS}-bit number a block whose parent hash is at {@code buffer[offset..offset + 32)} factors. */
    static long bounty(byte[] buffer, int offset) {
        return bounty(buffer, offset, BOUNTY_BITS);
    }

    /**
     * The {@code length}-bit number a block whose parent hash is at {@code buffer[offset..offset + 32)} factors: the
     * hash's top bits with the highest one set, so the number has exactly {@code length} bits.
     */
    static long bounty(byte[] buffer, int offset, int length) {
        return (Bytes.readLong(buffer, offset) >>> (64 - length)) | (1L << (length - 1));
    }

    /** How many bits {@code number} needs, 0 for 0: the bit length a bounty was derived with. */
    public static int bitLength(long number) {
        int length = 0;
        long rest = number;
        while (rest != 0) {
            rest = rest >>> 1;
            length++;
        }
        return length;
    }

    /** Whether the 32-byte hash at {@code hash[0..32)} starts with at least {@code bits} zero bits. */
    static boolean meetsTarget(byte[] hash, int bits) {
        int zeros = 0;
        for (int i = 0; i < 32; i++) {
            int value = hash[i] & 0xff;
            if (value != 0) {
                while (value < 0x80) {
                    value = value << 1;
                    zeros++;
                }
                return zeros >= bits;
            }
            zeros = zeros + 8;
        }
        return true;
    }

    static boolean sameHash(byte[] a, int aOffset, byte[] b, int bOffset) {
        for (int i = 0; i < Sha256.DIGEST_SIZE; i++) {
            if (a[aOffset + i] != b[bOffset + i]) {
                return false;
            }
        }
        return true;
    }

    static void copy(byte[] from, int fromOffset, byte[] to, int toOffset, int length) {
        for (int i = 0; i < length; i++) {
            to[toOffset + i] = from[fromOffset + i];
        }
    }
}

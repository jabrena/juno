package io.github.jabrena.juno.api.net.ledger;

/**
 * Byte layout of a {@link ChainNode} block: Bitcoin's 80-byte header followed by one payload.
 *
 * <pre>
 * header   0  version(4)
 *          4  previous block hash(32)
 *         36  payload hash(32)      SHA-256 of the payload: the Merkle root of a one-entry block
 *         68  time(4)               seconds of network time
 *         72  bits(4)               required leading zero bits of the header hash
 *         76  nonce(4)
 * payload 80  miner id(4)
 *         84  n(8)                  the bounty: derived from the previous hash, never chosen by the miner
 *         92  factor count(1)
 *         93  (prime(8), exponent(1)) x count
 * </pre>
 *
 * All fields are big-endian. Unlike Bitcoin, the proof-of-work is a single SHA-256 rather than two.
 */
final class BlockFormat {
    static final int VERSION = 1;
    static final int HEADER_SIZE = 80;
    static final int PREVIOUS = 4;
    static final int PAYLOAD_HASH = 36;
    static final int TIME = 68;
    static final int BITS = 72;
    static final int NONCE = 76;

    static final int PAYLOAD = HEADER_SIZE;
    static final int MINER = PAYLOAD;
    static final int NUMBER = PAYLOAD + 4;
    static final int COUNT = PAYLOAD + 12;
    static final int FACTORS = PAYLOAD + 13;
    static final int MAX_SIZE = FACTORS + FactorProtocol.MAX_FACTORS * 9;

    /** Bit length of every bounty, the number each block has to factor. */
    static final int BOUNTY_BITS = 40;

    private BlockFormat() {
    }

    /** Size of the block at {@code offset}; only meaningful once {@link #wellFormed} holds. */
    static int size(byte[] buffer, int offset) {
        return FACTORS + FactorProtocol.unsigned(buffer[offset + COUNT]) * 9;
    }

    static boolean wellFormed(byte[] buffer, int offset) {
        return FactorProtocol.readInt(buffer, offset) == VERSION
                && FactorProtocol.unsigned(buffer[offset + COUNT]) <= FactorProtocol.MAX_FACTORS;
    }

    /** The number a block whose parent hash is at {@code buffer[offset..offset + 32)} must factor. */
    static long bounty(byte[] buffer, int offset) {
        return (FactorProtocol.readLong(buffer, offset) >>> (64 - BOUNTY_BITS)) | (1L << (BOUNTY_BITS - 1));
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

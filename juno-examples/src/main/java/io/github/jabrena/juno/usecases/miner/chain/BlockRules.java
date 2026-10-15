package io.github.jabrena.juno.usecases.miner.chain;

import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.games.blockchain.Factorizer;
import io.github.jabrena.juno.games.blockchain.Sha256;

/**
 * The consensus rules a block can be checked against without the rest of the chain: its payload
 * (hash, bounty, factorization) and the difficulty its parent dictates. {@link BlockPool} applies them
 * and adds the checks that need the chain: proof-of-work, parent and time.
 */
final class BlockRules {
    static final int MIN_BITS = 4;
    static final int MAX_BITS = 40;

    private final byte[] digest = new byte[Sha256.DIGEST_SIZE];
    private final byte[] shaBlock = new byte[Sha256.BLOCK_SIZE];
    private final int[] shaSchedule = new int[Sha256.SCHEDULE_SIZE];
    private final int[] shaState = new int[Sha256.STATE_SIZE];
    private final long[] primes = new long[Factorizer.MAX_FACTORS];
    private final int[] exponents = new int[Factorizer.MAX_FACTORS];

    /**
     * Whether the block at {@code buffer[base..)}, whose header hashes to {@code hash[0..32)}, is well formed
     * and its hash meets the difficulty it declares. Whether that difficulty is the right one depends on
     * the parent, which {@link BlockPool} checks.
     */
    boolean headerAcceptable(byte[] buffer, int base, byte[] hash) {
        if (!BlockFormat.wellFormed(buffer, base)) {
            return false;
        }
        int bits = Bytes.readInt(buffer, base + BlockFormat.BITS);
        return bits >= MIN_BITS && bits <= MAX_BITS && BlockFormat.meetsTarget(hash, bits);
    }

    /**
     * Whether the block at {@code buffer[base..)} carries a payload matching its payload hash, a bounty its parent
     * hash dictates for one of the allowed bit lengths, and a correct factorization of it.
     */
    boolean payloadValid(byte[] buffer, int base) {
        payloadHash(buffer, base, digest);
        if (!BlockFormat.sameHash(digest, 0, buffer, base + BlockFormat.PAYLOAD_HASH)) {
            return false;
        }
        long number = Bytes.readLong(buffer, base + BlockFormat.NUMBER);
        int length = BlockFormat.bitLength(number);
        if (length < BlockFormat.MIN_BOUNTY_BITS || length > BlockFormat.MAX_BOUNTY_BITS
                || number != BlockFormat.bounty(buffer, base + BlockFormat.PREVIOUS, length)) {
            return false;
        }
        int count = Bytes.unsigned(buffer[base + BlockFormat.COUNT]);
        for (int i = 0; i < count; i++) {
            primes[i] = Bytes.readLong(buffer, base + BlockFormat.FACTORS + i * 9);
            exponents[i] = Bytes.unsigned(buffer[base + BlockFormat.FACTORS + i * 9 + 8]);
        }
        return Factorizer.verify(number, primes, exponents, count);
    }

    /** Writes the SHA-256 of the 80-byte header at {@code buffer[base..)} into {@code hash[0..32)}: the block's id. */
    void headerHash(byte[] buffer, int base, byte[] hash) {
        Sha256.digest(buffer, base, BlockFormat.HEADER_SIZE, hash, shaBlock, shaSchedule, shaState);
    }

    /** Writes the SHA-256 of the payload of the block at {@code buffer[base..)} into {@code hash[0..32)}. */
    void payloadHash(byte[] buffer, int base, byte[] hash) {
        Sha256.digest(buffer, base + BlockFormat.PAYLOAD, BlockFormat.size(buffer, base) - BlockFormat.PAYLOAD, hash,
                shaBlock, shaSchedule, shaState);
    }

    /**
     * The difficulty a block at {@code height} must have. Every {@code interval} blocks it moves one bit
     * toward a block every {@code targetSeconds}, judged from the time the parent's period took.
     */
    static int expectedBits(int parentBits, int parentTime, int parentPeriodStart, int height, int interval,
                            int targetSeconds) {
        if (height % interval != 0) {
            return parentBits;
        }
        int span = parentTime - parentPeriodStart;
        int target = (interval - 1) * targetSeconds;
        if (span < target / 2 && parentBits < MAX_BITS) {
            return parentBits + 1;
        }
        if (span > target * 2 && parentBits > MIN_BITS) {
            return parentBits - 1;
        }
        return parentBits;
    }
}

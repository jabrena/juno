package io.github.jabrena.juno.usecases.miner;

import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.games.blockchain.Factorizer;
import io.github.jabrena.juno.games.blockchain.Sha256;

/**
 * Builds a block on top of a {@link BlockPool}'s tip and searches for a nonce that meets its target,
 * a slice at a time so the caller can keep serving the network between slices.
 *
 * <p>{@link #prepare} does the useful work first: it factors the bounty the parent hash dictates and
 * commits the result through the payload hash. Only then does the hashing start. The first 64 header
 * bytes (version, parent hash, most of the payload hash) are fixed for the whole search, so they are
 * absorbed once into a midstate and each attempt only compresses the last 16 bytes.
 */
public final class Miner {
    private final byte[] block = new byte[BlockFormat.MAX_SIZE];
    private final byte[] digest = new byte[Sha256.DIGEST_SIZE];
    private final byte[] shaBlock = new byte[Sha256.BLOCK_SIZE];
    private final int[] shaSchedule = new int[Sha256.SCHEDULE_SIZE];
    private final int[] midstate = new int[Sha256.STATE_SIZE];
    private final int[] shaState = new int[Sha256.STATE_SIZE];
    private final long[] primes = new long[Factorizer.MAX_FACTORS];
    private final int[] exponents = new int[Factorizer.MAX_FACTORS];
    private final long[] stack = new long[Factorizer.STACK_SIZE];

    private int bits;
    private int nonce;
    private int attempts;

    /** Starts a new block on {@code pool}'s tip, mined by {@code minerId} at network time {@code now} (seconds). */
    public void prepare(BlockPool pool, int minerId, int now) {
        prepare(pool, minerId, now, BlockFormat.BOUNTY_BITS);
    }

    /**
     * Like {@link #prepare(BlockPool, int, int)}, factoring a bounty of {@code bountyBits} bits, from
     * {@link BlockFormat#MIN_BOUNTY_BITS} to {@link BlockFormat#MAX_BOUNTY_BITS}.
     */
    public void prepare(BlockPool pool, int minerId, int now, int bountyBits) {
        int tip = pool.tip();
        byte[] hashes = pool.hashes();
        for (int i = 0; i < BlockFormat.MAX_SIZE; i++) {
            block[i] = 0;
        }
        Bytes.writeInt(block, 0, BlockFormat.VERSION);
        BlockFormat.copy(hashes, tip * Sha256.DIGEST_SIZE, block, BlockFormat.PREVIOUS, Sha256.DIGEST_SIZE);

        long number = BlockFormat.bounty(block, BlockFormat.PREVIOUS, bountyBits);
        int count = Factorizer.factorize(number, primes, exponents, stack);
        Bytes.writeInt(block, BlockFormat.MINER, minerId);
        Bytes.writeLong(block, BlockFormat.NUMBER, number);
        block[BlockFormat.COUNT] = (byte) count;
        for (int i = 0; i < count; i++) {
            Bytes.writeLong(block, BlockFormat.FACTORS + i * 9, primes[i]);
            block[BlockFormat.FACTORS + i * 9 + 8] = (byte) exponents[i];
        }
        Sha256.digest(block, BlockFormat.PAYLOAD, BlockFormat.size(block, 0) - BlockFormat.PAYLOAD, digest,
                shaBlock, shaSchedule, shaState);
        BlockFormat.copy(digest, 0, block, BlockFormat.PAYLOAD_HASH, Sha256.DIGEST_SIZE);

        int parentTime = pool.time(tip);
        Bytes.writeInt(block, BlockFormat.TIME, now > parentTime ? now : parentTime + 1);
        bits = pool.nextBits();
        Bytes.writeInt(block, BlockFormat.BITS, bits);
        nonce = 0;
        attempts = 0;

        Sha256.initialize(midstate);
        Sha256.absorb(block, 0, shaBlock, shaSchedule, midstate);
    }

    /**
     * Tries up to {@code tries} nonces and returns whether one met the target; the finished block is
     * then in {@link #block()}.
     */
    public boolean mine(int tries) {
        for (int i = 0; i < tries; i++) {
            Bytes.writeInt(block, BlockFormat.NONCE, nonce);
            for (int s = 0; s < Sha256.STATE_SIZE; s++) {
                shaState[s] = midstate[s];
            }
            Sha256.finish(block, Sha256.BLOCK_SIZE, BlockFormat.HEADER_SIZE - Sha256.BLOCK_SIZE,
                    BlockFormat.HEADER_SIZE, digest, shaBlock, shaSchedule, shaState);
            attempts++;
            if (BlockFormat.meetsTarget(digest, bits)) {
                return true;
            }
            nonce++;
            if (nonce == 0) {
                // Every nonce failed: move the time, which sits after the midstate, and start again.
                Bytes.writeInt(block, BlockFormat.TIME, Bytes.readInt(block, BlockFormat.TIME) + 1);
            }
        }
        return false;
    }

    public byte[] block() {
        return block;
    }

    int bits() {
        return bits;
    }

    /** Hashes tried since {@link #prepare}. */
    public int attempts() {
        return attempts;
    }
}

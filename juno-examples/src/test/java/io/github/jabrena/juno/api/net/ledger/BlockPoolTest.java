package io.github.jabrena.juno.api.net.ledger;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockPoolTest {
    private static final int BITS = 4;

    @Test
    void everyNodeStartsFromTheSameGenesis() {
        BlockPool a = pool();
        BlockPool b = pool();

        assertThat(tipHash(a)).isEqualTo(tipHash(b));
        assertThat(a.height(a.tip())).isZero();
        assertThat(a.work(a.tip())).isZero();
    }

    @Test
    void minedBlocksExtendTheChainAndAPeerAcceptsThem() {
        BlockPool a = pool();
        BlockPool b = pool();
        List<byte[]> mined = mine(a, 1, 3, 0, 1);

        assertThat(a.height(a.tip())).isEqualTo(3);
        assertThat(a.work(a.tip())).isEqualTo(3L << BITS);
        for (byte[] packet : mined) {
            assertThat(deliver(packet, b)).isEqualTo(BlockPool.NEW_TIP);
        }
        assertThat(tipHash(b)).isEqualTo(tipHash(a));
        assertThat(deliver(mined.get(2), b)).isEqualTo(BlockPool.DUPLICATE);
    }

    @Test
    void rejectsBlocksThatBreakAConsensusRule() {
        BlockPool a = pool();
        byte[] good = mine(a, 1, 1, 0, 1).get(0);

        byte[] wrongFactors = good.clone();
        wrongFactors[ChainProtocol.BODY + BlockFormat.FACTORS + 7] ^= 2;
        assertThat(deliver(wrongFactors, pool())).as("payload no longer matches its hash").isEqualTo(BlockPool.INVALID);

        byte[] lyingPayload = good.clone();
        lyingPayload[ChainProtocol.BODY + BlockFormat.FACTORS + 7] ^= 2;
        rehashPayload(lyingPayload);
        assertThat(deliver(lyingPayload, pool())).as("factors that do not multiply back").isEqualTo(BlockPool.INVALID);

        byte[] easyNumber = good.clone();
        FactorProtocol.writeLong(easyNumber, ChainProtocol.BODY + BlockFormat.NUMBER, 2);
        easyNumber[ChainProtocol.BODY + BlockFormat.COUNT] = 1;
        FactorProtocol.writeLong(easyNumber, ChainProtocol.BODY + BlockFormat.FACTORS, 2);
        easyNumber[ChainProtocol.BODY + BlockFormat.FACTORS + 8] = 1;
        rehashPayload(easyNumber);
        assertThat(deliver(easyNumber, pool())).as("a number the miner chose").isEqualTo(BlockPool.INVALID);

        byte[] wrongBits = good.clone();
        FactorProtocol.writeInt(wrongBits, ChainProtocol.BODY + BlockFormat.BITS, BITS + 1);
        remine(wrongBits);
        assertThat(deliver(wrongBits, pool())).as("difficulty off the retarget rule").isEqualTo(BlockPool.INVALID);

        byte[] oldTime = good.clone();
        FactorProtocol.writeInt(oldTime, ChainProtocol.BODY + BlockFormat.TIME, 0);
        remine(oldTime);
        assertThat(deliver(oldTime, pool())).as("not later than its parent").isEqualTo(BlockPool.INVALID);

        byte[] noWork = good.clone();
        do {
            FactorProtocol.writeInt(noWork, ChainProtocol.BODY + BlockFormat.NONCE,
                    FactorProtocol.readInt(noWork, ChainProtocol.BODY + BlockFormat.NONCE) + 1);
        } while (meetsTarget(noWork));
        assertThat(deliver(noWork, pool())).as("hash above the target").isEqualTo(BlockPool.INVALID);
    }

    @Test
    void aMutatedCopyArrivingFirstDoesNotShadowTheGenuineBlock() {
        BlockPool a = pool();
        List<byte[]> mined = mine(a, 1, 2, 0, 1);
        BlockPool c = pool();
        byte[] mutated = mined.get(1).clone();
        mutated[ChainProtocol.BODY + BlockFormat.FACTORS + 7] ^= 2;

        assertThat(deliver(mutated, c)).as("same header hash, broken payload, parent unknown").isEqualTo(BlockPool.INVALID);
        assertThat(deliver(mined.get(1), c)).isEqualTo(BlockPool.NEED_PARENT);
        assertThat(deliver(mined.get(0), c)).isEqualTo(BlockPool.NEW_TIP);
        assertThat(tipHash(c)).isEqualTo(tipHash(a));
    }

    @Test
    void reorganizesToTheBranchWithMoreWorkAndKeepsTheFirstSeenOnATie() {
        BlockPool a = pool();
        BlockPool b = pool();
        List<byte[]> branchA = mine(a, 0xA, 2, 0, 1);
        List<byte[]> branchB = mine(b, 0xB, 3, 0, 1);

        for (byte[] packet : branchA) {
            assertThat(deliver(packet, b)).as("a lighter branch is kept aside").isEqualTo(BlockPool.STORED);
        }
        assertThat(deliver(branchB.get(0), a)).isEqualTo(BlockPool.STORED);
        assertThat(deliver(branchB.get(1), a)).as("equal work: the first seen stays").isEqualTo(BlockPool.STORED);
        assertThat(miner(a)).isEqualTo(0xA);
        assertThat(deliver(branchB.get(2), a)).isEqualTo(BlockPool.NEW_TIP);
        assertThat(tipHash(a)).isEqualTo(tipHash(b));
        assertThat(a.height(a.tip())).isEqualTo(3);
    }

    @Test
    void fetchesAMissingParentBeforeConnectingABlock() {
        BlockPool a = pool();
        BlockPool c = pool();
        List<byte[]> mined = mine(a, 1, 2, 0, 1);

        assertThat(deliver(mined.get(1), c)).isEqualTo(BlockPool.NEED_PARENT);
        byte[] wanted = new byte[Sha256.DIGEST_SIZE];
        c.missingParent(wanted, 0);
        assertThat(wanted).isEqualTo(hashOf(mined.get(0)));
        assertThat(deliver(mined.get(0), c)).isEqualTo(BlockPool.NEW_TIP);
        assertThat(tipHash(c)).isEqualTo(tipHash(a));
    }

    @Test
    void joinsALongerChainThroughACheckpointAndValidatesWhatFollows() {
        BlockPool a = pool();
        List<byte[]> mined = mine(a, 1, 8, 0, 1);
        BlockPool late = pool();

        assertThat(deliver(mined.get(7), late)).isEqualTo(BlockPool.NEED_PARENT);
        assertThat(deliver(mined.get(6), late)).isEqualTo(BlockPool.NEED_PARENT);
        assertThat(deliver(mined.get(5), late)).isEqualTo(BlockPool.NEED_PARENT);
        assertThat(deliver(mined.get(4), late)).isEqualTo(BlockPool.NEW_TIP);

        assertThat(tipHash(late)).isEqualTo(tipHash(a));
        assertThat(late.height(late.tip())).isEqualTo(8);
        assertThat(late.work(late.tip())).isEqualTo(a.work(a.tip()));
    }

    @Test
    void doesNotFetchTheHistoryOfALighterBranch() {
        BlockPool a = pool();
        BlockPool b = pool();
        mine(a, 1, 3, 0, 1);
        List<byte[]> other = mine(b, 2, 2, 0, 1);

        assertThat(deliver(other.get(1), a)).isEqualTo(BlockPool.STORED);
    }

    @Test
    void retargetsTowardTheTargetBlockTime() {
        BlockPool fast = new BlockPool(6, 4, 30);
        mine(fast, 1, 4, 0, 0);
        assertThat(fast.bits(fast.tip())).as("blocks one second apart").isEqualTo(7);

        BlockPool slow = new BlockPool(6, 4, 30);
        mine(slow, 1, 4, 1000, 1000);
        assertThat(slow.bits(slow.tip())).as("blocks 1000 seconds apart").isEqualTo(5);

        BlockPool onTime = new BlockPool(6, 4, 30);
        mine(onTime, 1, 4, 30, 30);
        assertThat(onTime.bits(onTime.tip())).isEqualTo(6);
    }

    @Test
    void prunesOldBlocksAndKeepsExtendingTheChain() {
        BlockPool a = pool();
        byte[] genesis = tipHash(a);
        mine(a, 1, BlockPool.CAPACITY + 8, 0, 1);

        assertThat(a.height(a.tip())).isEqualTo(BlockPool.CAPACITY + 8);
        assertThat(a.find(genesis, 0)).isEqualTo(-1);
    }

    private static BlockPool pool() {
        return new BlockPool(BITS, 8, 30);
    }

    /** Mines {@code count} blocks on {@code pool}'s tip at times start, start + step, ... and returns their BLOCK packets. */
    private static List<byte[]> mine(BlockPool pool, int minerId, int count, int start, int step) {
        Miner miner = new Miner();
        List<byte[]> packets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            miner.prepare(pool, minerId, start + i * step);
            while (!miner.mine(64)) {
                // keep hashing
            }
            assertThat(pool.add(miner.block(), 0, 0, 0L, 0)).isEqualTo(BlockPool.NEW_TIP);
            byte[] packet = new byte[ChainProtocol.PACKET_SIZE];
            ChainProtocol.writeBlock(packet, pool, pool.tip());
            packets.add(packet);
        }
        return packets;
    }

    private static int deliver(byte[] packet, BlockPool to) {
        assertThat(ChainProtocol.valid(packet, packet.length)).isTrue();
        return to.add(packet, ChainProtocol.BODY, ChainProtocol.height(packet), ChainProtocol.work(packet),
                ChainProtocol.periodStart(packet));
    }

    private static byte[] tipHash(BlockPool pool) {
        return Arrays.copyOfRange(pool.hashes(), pool.tip() * Sha256.DIGEST_SIZE, (pool.tip() + 1) * Sha256.DIGEST_SIZE);
    }

    private static int miner(BlockPool pool) {
        return FactorProtocol.readInt(pool.blocks(), pool.tip() * BlockFormat.MAX_SIZE + BlockFormat.MINER);
    }

    private static byte[] hashOf(byte[] packet) {
        byte[] digest = new byte[Sha256.DIGEST_SIZE];
        Sha256.digest(packet, ChainProtocol.BODY, BlockFormat.HEADER_SIZE, digest, new byte[Sha256.BLOCK_SIZE],
                new int[Sha256.SCHEDULE_SIZE], new int[Sha256.STATE_SIZE]);
        return digest;
    }

    private static boolean meetsTarget(byte[] packet) {
        return BlockFormat.meetsTarget(hashOf(packet), FactorProtocol.readInt(packet, ChainProtocol.BODY + BlockFormat.BITS));
    }

    /** Searches a new nonce so an edited header meets its own (possibly edited) target again. */
    private static void remine(byte[] packet) {
        int nonce = 0;
        do {
            FactorProtocol.writeInt(packet, ChainProtocol.BODY + BlockFormat.NONCE, nonce);
            nonce++;
        } while (!meetsTarget(packet));
    }

    /** Recomputes the payload hash after editing the payload, then re-mines the header. */
    private static void rehashPayload(byte[] packet) {
        int base = ChainProtocol.BODY;
        byte[] digest = new byte[Sha256.DIGEST_SIZE];
        Sha256.digest(packet, base + BlockFormat.PAYLOAD, BlockFormat.size(packet, base) - BlockFormat.PAYLOAD, digest,
                new byte[Sha256.BLOCK_SIZE], new int[Sha256.SCHEDULE_SIZE], new int[Sha256.STATE_SIZE]);
        System.arraycopy(digest, 0, packet, base + BlockFormat.PAYLOAD_HASH, Sha256.DIGEST_SIZE);
        remine(packet);
    }
}

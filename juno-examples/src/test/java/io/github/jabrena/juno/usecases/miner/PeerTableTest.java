package io.github.jabrena.juno.usecases.miner;

import io.github.jabrena.juno.games.blockchain.Bytes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PeerTableTest {
    private static final int ALIVE_FOR = PeerTable.SUSPECT_AFTER_MILLIS - 1;
    private static final int SUSPECT_AT = PeerTable.SUSPECT_AFTER_MILLIS;
    private static final int DEAD_AT = PeerTable.DEAD_AFTER_MILLIS;

    /** A UUID that differs from every other one this test makes, by its number. */
    private static byte[] uuid(int number) {
        byte[] uuid = new byte[NodeId.SIZE];
        Bytes.writeInt(uuid, 0, number);
        Bytes.writeInt(uuid, 12, ~number);
        return uuid;
    }

    private static boolean update(PeerTable peers, int number, int height, long work, int flags, int now) {
        return peers.update(uuid(number), 0, height, work, flags, now);
    }

    private static int shortIdOf(PeerTable peers, int slot) {
        return NodeId.shortId(peers.uuids(), slot * NodeId.SIZE);
    }

    @Test
    void aTableWithNoHeartbeatsHasNoPeers() {
        PeerTable peers = new PeerTable();

        assertThat(peers.alive(0)).isZero();
        assertThat(peers.suspect(0)).isZero();
        assertThat(peers.bestWork(0)).isZero();
        assertThat(peers.nextDead(DEAD_AT)).isEqualTo(-1);
    }

    @Test
    void aHeartbeatAddsAPeerOnceAndRefreshesItAfterwards() {
        PeerTable peers = new PeerTable();

        assertThat(update(peers, 7, 3, 48L, 0, 1000)).as("first heartbeat joins").isTrue();
        assertThat(update(peers, 7, 4, 64L, 0, 2000)).as("a later one only refreshes").isFalse();

        assertThat(peers.alive(2000)).isEqualTo(1);
    }

    @Test
    void peersThatShareTheFirstFourBytesAreStillDifferentNodes() {
        PeerTable peers = new PeerTable();
        byte[] first = uuid(7);
        byte[] second = uuid(7);
        second[NodeId.SIZE - 1] ^= 1;

        peers.update(first, 0, 3, 48L, 0, 1000);
        boolean secondIsNew = peers.update(second, 0, 3, 48L, 0, 1000);

        assertThat(secondIsNew).as("only the whole UUID identifies a node").isTrue();
        assertThat(peers.alive(1000)).isEqualTo(2);
    }

    @Test
    void aPeerGoesFromAliveToSuspectToDeadAsHeartbeatsAreMissed() {
        PeerTable peers = new PeerTable();
        update(peers, 7, 3, 48L, 0, 1000);

        assertThat(peers.alive(1000 + ALIVE_FOR)).isEqualTo(1);
        assertThat(peers.suspect(1000 + ALIVE_FOR)).isZero();

        assertThat(peers.alive(1000 + SUSPECT_AT)).isZero();
        assertThat(peers.suspect(1000 + SUSPECT_AT)).as("one missed beat is not death").isEqualTo(1);

        assertThat(peers.suspect(1000 + DEAD_AT)).isZero();
        assertThat(peers.alive(1000 + DEAD_AT)).isZero();
    }

    @Test
    void eachSlotReportsItsPeersStateHeightAndMiningFlag() {
        PeerTable peers = new PeerTable();
        update(peers, 7, 12, 192L, ChainProtocol.MINING, 1000);
        int slot = 0;

        assertThat(peers.state(slot, 1000 + ALIVE_FOR)).isEqualTo(PeerTable.ALIVE);
        assertThat(peers.state(slot, 1000 + SUSPECT_AT)).isEqualTo(PeerTable.SUSPECT);
        assertThat(peers.state(slot, 1000 + DEAD_AT)).isEqualTo(PeerTable.DEAD);
        assertThat(peers.state(1, 1000)).as("a slot never used").isEqualTo(PeerTable.EMPTY);
        assertThat(peers.height(slot)).isEqualTo(12);
        assertThat(peers.isMining(slot)).isTrue();

        update(peers, 7, 13, 208L, 0, 2000);
        assertThat(peers.isMining(slot)).as("the latest heartbeat wins").isFalse();
    }

    @Test
    void aDeathIsReportedOnceAndAHeartbeatBringsThePeerBack() {
        PeerTable peers = new PeerTable();
        update(peers, 7, 3, 48L, 0, 1000);
        update(peers, 9, 3, 48L, 0, 1000);

        assertThat(peers.nextDead(1000 + SUSPECT_AT)).as("only suspect so far").isEqualTo(-1);
        int first = peers.nextDead(1000 + DEAD_AT);
        int second = peers.nextDead(1000 + DEAD_AT);
        assertThat(new int[] {shortIdOf(peers, first), shortIdOf(peers, second)}).containsExactlyInAnyOrder(7, 9);
        assertThat(peers.nextDead(1000 + DEAD_AT)).as("handed out once").isEqualTo(-1);

        assertThat(update(peers, 7, 5, 80L, 0, 1000 + DEAD_AT + 1)).as("back from the dead").isTrue();
        assertThat(peers.alive(1000 + DEAD_AT + 1)).isEqualTo(1);
        int again = peers.nextDead(1000 + 2 * DEAD_AT + 1);
        assertThat(shortIdOf(peers, again)).as("it can die again").isEqualTo(7);
    }

    @Test
    void onlyAlivePeersCountTowardTheBestTipAndMining() {
        PeerTable peers = new PeerTable();
        update(peers, 1, 10, 160L, ChainProtocol.MINING, 0);
        update(peers, 2, 4, 64L, 0, SUSPECT_AT);

        int now = SUSPECT_AT + 1;
        assertThat(peers.alive(now)).as("node 1 went quiet").isEqualTo(1);
        assertThat(peers.bestWork(now)).as("a quiet peer's tip is not worth waiting for").isEqualTo(64L);
        assertThat(peers.bestHeight(now)).isEqualTo(4);
        assertThat(peers.mining(now)).isZero();
        assertThat(peers.mining(SUSPECT_AT - 1)).as("node 1 was alive and mining").isEqualTo(1);
    }

    @Test
    void aFullTableReplacesThePeerHeardFromLongestAgo() {
        PeerTable peers = new PeerTable();
        for (int number = 1; number <= PeerTable.CAPACITY; number++) {
            update(peers, number, 1, 16L, 0, number * 10);
        }

        update(peers, 99, 1, 16L, 0, 1000);

        assertThat(peers.alive(1000)).isEqualTo(PeerTable.CAPACITY);
        assertThat(update(peers, 1, 1, 16L, 0, 1001)).as("node 1 was the one replaced").isTrue();
    }

    @Test
    void timeCompareBySubtractionSoAWrappingClockDoesNotMatter() {
        PeerTable peers = new PeerTable();
        int beforeWrap = Integer.MAX_VALUE - 100;
        update(peers, 5, 1, 16L, 0, beforeWrap);

        int afterWrap = beforeWrap + 500;
        assertThat(afterWrap).as("the clock wrapped").isNegative();
        assertThat(peers.alive(afterWrap)).isEqualTo(1);
        assertThat(peers.alive(beforeWrap + SUSPECT_AT)).isZero();
    }
}

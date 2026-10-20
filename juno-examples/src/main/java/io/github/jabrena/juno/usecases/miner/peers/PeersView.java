package io.github.jabrena.juno.usecases.miner.peers;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.hardware.time.Clock;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.util.mvc.Navigator;
import io.github.jabrena.juno.api.util.mvc.View;
import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.games.blockchain.NodeIo;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Header;
import io.github.jabrena.juno.usecases.miner.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner.commons.TabBar;
import io.github.jabrena.juno.usecases.miner.services.NodeService;

/**
 * The nodes this node has heard from, one row per slot of its {@link PeerTable}: the first word of the peer's UUID,
 * the height of the tip it announced, whether it is alive, suspect or lost, and whether it mines. A row is redrawn
 * only when what it shows changes; a peer moves from alive to suspect on its own, as heartbeats stop arriving.
 */
public final class PeersView implements View {
    private static final Clock CLOCK = MCU.getClock();
    /** The status row of the first peer slot; the slots take the rows below it. */
    static final int FIRST_PEER_ROW = 2;

    private final Navigator navigator;
    private final Miner2State model;
    private final Miner2Header header;
    private final NodeService node;
    private final int[] shownRows = new int[PeerTable.CAPACITY];
    private boolean shownNone;

    public PeersView(Navigator navigator, Miner2State model, Miner2Header header, NodeService node) {
        this.navigator = navigator;
        this.model = model;
        this.header = header;
        this.node = node;
    }

    @Override
    public void enter() {
        header.enter();
        TabBar.draw(Miner2Views.PEERS);
        GameScreen.row(0, 1, TftTouchShield.WHITE);
        TftTouchShield.print("This node ");
        TftTouchShield.print(NodeIo.hexWord(model.uuidBytes(), 0));
        GameScreen.row(1, 1, TftTouchShield.GRAY);
        TftTouchShield.print("Heard by heartbeat:");
        for (int slot = 0; slot < PeerTable.CAPACITY; slot++) {
            shownRows[slot] = -1;
        }
        shownNone = false;
    }

    @Override
    public void render() {
        header.render();
        PeerTable peers = model.peers();
        int now = CLOCK.millis();
        boolean none = true;
        for (int slot = 0; slot < PeerTable.CAPACITY; slot++) {
            int state = peers.state(slot, now);
            int key = rowKey(state, peers.height(slot), peers.isMining(slot), NodeId.shortId(peers.uuids(),
                    slot * NodeId.SIZE));
            if (key != shownRows[slot]) {
                shownRows[slot] = key;
                showPeer(peers, slot, state);
            }
            none = none && state == PeerTable.EMPTY;
        }
        if (none && !shownNone) {
            GameScreen.row(FIRST_PEER_ROW, 1, TftTouchShield.GRAY);
            TftTouchShield.print("No peers heard yet");
        }
        shownNone = none;
    }

    @Override
    public void touch(int x, int y) {
        if (Miner2Header.isWifi(x, y)) {
            node.toggleWifi();
        } else {
            TabBar.follow(navigator, x, y);
        }
    }

    /**
     * One number that changes whenever what a peer's row would show changes: -2 for an empty slot, never negative
     * for a used one.
     */
    static int rowKey(int state, int height, boolean mining, int shortId) {
        if (state == PeerTable.EMPTY) {
            return -2;
        }
        return ((height * 4 + state) * 2 + (mining ? 1 : 0)) * 31 + shortId & 0x7fffffff;
    }

    /** The word {@code state} shows in a peer's row. */
    static String stateLabel(int state) {
        if (state == PeerTable.ALIVE) {
            return "alive";
        }
        return state == PeerTable.SUSPECT ? "suspect" : "lost";
    }

    private static void showPeer(PeerTable peers, int slot, int state) {
        GameScreen.row(FIRST_PEER_ROW + slot, 1, color(state));
        if (state == PeerTable.EMPTY) {
            return;
        }
        TftTouchShield.print(NodeIo.hexWord(peers.uuids(), slot * NodeId.SIZE));
        TftTouchShield.print("  h " + peers.height(slot));
        TftTouchShield.print("  " + stateLabel(state));
        if (state != PeerTable.DEAD && peers.isMining(slot)) {
            TftTouchShield.print("  mining");
        }
    }

    private static int color(int state) {
        if (state == PeerTable.ALIVE) {
            return TftTouchShield.GREEN;
        }
        return state == PeerTable.SUSPECT ? TftTouchShield.YELLOW : TftTouchShield.GRAY;
    }
}

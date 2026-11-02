package io.github.jabrena.juno.usecases.miner.chain;

import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.games.blockchain.Sha256;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Header;
import io.github.jabrena.juno.usecases.miner.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner.commons.TabBar;
import io.github.jabrena.juno.usecases.miner.peers.PeerTable;
import io.github.jabrena.juno.usecases.miner.services.NodeService;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/**
 * The tip of the chain: height, difficulty, how many blocks this node
 * mined, the nodes it knows, and the tip's bounty with its factorization and hash. It redraws the tip rows only when
 * the tip moves, and the nodes row only when its counts change.
 */
public final class ChainView implements View {

    private final TftTouchShield tft;
    private static final Clock CLOCK = ArduinoUno.getClock();
    private final Navigator navigator;
    private final Miner2State model;
    private final Miner2Header header;
    private final NodeService node;
    private int shownTip;
    private int shownNodes;

    public ChainView(TftTouchShield tft, Navigator navigator, Miner2State model, Miner2Header header, NodeService node) {
        this.tft = tft;
        this.navigator = navigator;
        this.model = model;
        this.header = header;
        this.node = node;
    }

    @Override
    public void enter() {
        header.enter(tft);
        TabBar.draw(tft, Miner2Views.CHAIN);
        shownTip = model.pool().tipChanges() - 1;
        shownNodes = -1;
    }

    @Override
    public void render() {
        header.render(tft);
        BlockPool pool = model.pool();
        if (pool.tipChanges() != shownTip) {
            shownTip = pool.tipChanges();
            showTip(tft, pool);
        }
        int now = CLOCK.millis();
        int nodes = nodesKey(model.peers(), model.syncing(), now);
        if (nodes != shownNodes) {
            shownNodes = nodes;
            showNodes(tft, model.peers(), model.syncing(), now);
        }
    }

    @Override
    public void touch(int x, int y) {
        if (Miner2Header.isWifi(x, y)) {
            node.toggleWifi();
        } else {
            TabBar.follow(navigator, x, y);
        }
    }

    /** One number that changes whenever what {@link #showNodes} would draw changes. */
    static int nodesKey(PeerTable peers, boolean waiting, int now) {
        return peers.alive(now) * 1000 + peers.suspect(now) * 100 + peers.mining(now) * 10 + (waiting ? 1 : 0);
    }

    /** Row 2: how many nodes this node knows of (itself included), how many mine, and whether it is catching up. */
    private static void showNodes(TftTouchShield tft, PeerTable peers, boolean waiting, int now) {
        int suspect = peers.suspect(now);
        int miners = peers.mining(now) + (waiting ? 0 : 1);
        GameScreen.row(tft, 2, 1, waiting ? TftTouchShield.YELLOW : TftTouchShield.GREEN);
        tft.print("Nodes " + (peers.alive(now) + 1));
        tft.print("  mining " + miners);
        if (suspect > 0) {
            tft.print("  lost? " + suspect);
        }
        if (waiting) {
            tft.print("  SYNC");
        }
    }

    private void showTip(TftTouchShield tft, BlockPool pool) {
        int tip = pool.tip();
        byte[] blocks = pool.blocks();
        int base = tip * BlockFormat.MAX_SIZE;
        GameScreen.row(tft, 0, 2, TftTouchShield.WHITE);
        tft.print("Height " + pool.height(tip));
        GameScreen.row(tft, 1, 2, TftTouchShield.WHITE);
        tft.print("Bits " + pool.bits(tip) + "  Mined " + model.mined());
        if (pool.height(tip) == 0) {
            GameScreen.row(tft, 3, 1, TftTouchShield.GRAY);
            tft.print("Genesis: mining the first block");
            return;
        }
        int blockMiner = Bytes.readInt(blocks, base + BlockFormat.MINER);
        boolean minedHere = blockMiner == model.nodeId();
        GameScreen.row(tft, 3, 1, minedHere ? TftTouchShield.GREEN : TftTouchShield.CYAN);
        tft.print(minedHere ? "Tip mined here" : "Tip mined by " + blockMiner);
        GameScreen.row(tft, 4, 1, TftTouchShield.WHITE);
        tft.print("n = " + Bytes.readLong(blocks, base + BlockFormat.NUMBER));
        GameScreen.row(tft, 5, 1, TftTouchShield.WHITE);
        tft.print("= ");
        int count = Bytes.unsigned(blocks[base + BlockFormat.COUNT]);
        for (int i = 0; i < count; i++) {
            GameScreen.factor(tft, Bytes.readLong(blocks, base + BlockFormat.FACTORS + i * 9),
                    Bytes.unsigned(blocks[base + BlockFormat.FACTORS + i * 9 + 8]), i == 0);
        }
        GameScreen.row(tft, 6, 1, TftTouchShield.YELLOW);
        tft.print("hash ");
        GameScreen.hashPrefix(tft, pool.hashes(), tip * Sha256.DIGEST_SIZE);
        GameScreen.row(tft, 7, 1, TftTouchShield.GRAY);
        tft.print("Work ");
        tft.print("" + pool.work(tip));
    }
}

package io.github.jabrena.juno.usecases.miner2.chain;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.games.blockchain.Sha256;
import io.github.jabrena.juno.usecases.miner.BlockFormat;
import io.github.jabrena.juno.usecases.miner.BlockPool;
import io.github.jabrena.juno.usecases.miner.PeerTable;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2Header;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner2.commons.TabBar;
import io.github.jabrena.juno.usecases.miner2.services.NodeService;

/**
 * The tip of the chain, as the original Miner's only screen showed it: height, difficulty, how many blocks this node
 * mined, the nodes it knows, and the tip's bounty with its factorization and hash. It redraws the tip rows only when
 * the tip moves, and the nodes row only when its counts change.
 */
public final class ChainView implements View {
    private final Navigator navigator;
    private final Miner2State model;
    private final Miner2Header header;
    private final NodeService node;
    private int shownTip;
    private int shownNodes;

    public ChainView(Navigator navigator, Miner2State model, Miner2Header header, NodeService node) {
        this.navigator = navigator;
        this.model = model;
        this.header = header;
        this.node = node;
    }

    @Override
    public void enter() {
        header.enter();
        TabBar.draw(Miner2Views.CHAIN);
        shownTip = model.pool().tipChanges() - 1;
        shownNodes = -1;
    }

    @Override
    public void render() {
        header.render();
        BlockPool pool = model.pool();
        if (pool.tipChanges() != shownTip) {
            shownTip = pool.tipChanges();
            showTip(pool);
        }
        int now = Clock.millis();
        int nodes = nodesKey(model.peers(), model.syncing(), now);
        if (nodes != shownNodes) {
            shownNodes = nodes;
            showNodes(model.peers(), model.syncing(), now);
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
    private static void showNodes(PeerTable peers, boolean waiting, int now) {
        int suspect = peers.suspect(now);
        int miners = peers.mining(now) + (waiting ? 0 : 1);
        GameScreen.row(2, 1, waiting ? TftTouchShield.YELLOW : TftTouchShield.GREEN);
        TftTouchShield.print("Nodes " + (peers.alive(now) + 1));
        TftTouchShield.print("  mining " + miners);
        if (suspect > 0) {
            TftTouchShield.print("  lost? " + suspect);
        }
        if (waiting) {
            TftTouchShield.print("  SYNC");
        }
    }

    private void showTip(BlockPool pool) {
        int tip = pool.tip();
        byte[] blocks = pool.blocks();
        int base = tip * BlockFormat.MAX_SIZE;
        GameScreen.row(0, 2, TftTouchShield.WHITE);
        TftTouchShield.print("Height " + pool.height(tip));
        GameScreen.row(1, 2, TftTouchShield.WHITE);
        TftTouchShield.print("Bits " + pool.bits(tip) + "  Mined " + model.mined());
        if (pool.height(tip) == 0) {
            GameScreen.row(3, 1, TftTouchShield.GRAY);
            TftTouchShield.print("Genesis: mining the first block");
            return;
        }
        int blockMiner = Bytes.readInt(blocks, base + BlockFormat.MINER);
        boolean minedHere = blockMiner == model.nodeId();
        GameScreen.row(3, 1, minedHere ? TftTouchShield.GREEN : TftTouchShield.CYAN);
        TftTouchShield.print(minedHere ? "Tip mined here" : "Tip mined by " + blockMiner);
        GameScreen.row(4, 1, TftTouchShield.WHITE);
        TftTouchShield.print("n = " + Bytes.readLong(blocks, base + BlockFormat.NUMBER));
        GameScreen.row(5, 1, TftTouchShield.WHITE);
        TftTouchShield.print("= ");
        int count = Bytes.unsigned(blocks[base + BlockFormat.COUNT]);
        for (int i = 0; i < count; i++) {
            GameScreen.factor(Bytes.readLong(blocks, base + BlockFormat.FACTORS + i * 9),
                    Bytes.unsigned(blocks[base + BlockFormat.FACTORS + i * 9 + 8]), i == 0);
        }
        GameScreen.row(6, 1, TftTouchShield.YELLOW);
        TftTouchShield.print("hash ");
        GameScreen.hashPrefix(pool.hashes(), tip * Sha256.DIGEST_SIZE);
        GameScreen.row(7, 1, TftTouchShield.GRAY);
        TftTouchShield.print("Work ");
        TftTouchShield.print("" + pool.work(tip));
    }
}

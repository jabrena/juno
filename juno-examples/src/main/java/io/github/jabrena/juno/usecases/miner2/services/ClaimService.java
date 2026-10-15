package io.github.jabrena.juno.usecases.miner2.services;

import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.usecases.miner.BlockFormat;
import io.github.jabrena.juno.usecases.miner.BlockPool;
import io.github.jabrena.juno.usecases.miner.ChainProtocol;
import io.github.jabrena.juno.usecases.miner.NodeId;
import io.github.jabrena.juno.usecases.miner2.commons.BitBoard;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2State;

/**
 * Shares the bounty bit lengths out between the nodes, so each one mines a length no other node is mining. Before
 * mining, a node broadcasts {@link ChainProtocol#CLAIM} for the lowest free length on its {@link BitBoard} and waits
 * {@link #ASK_MILLIS}: a node that owns the length, or has seen it mined, answers {@link ChainProtocol#TAKEN}, and the
 * claimer tries the next one. Silence means the length is free, and the node owns it until a block carrying it joins
 * the chain. Heartbeats repeat each node's owned length and mined mask, so a node that joins late learns the board.
 * Two nodes asking for one length at once settle it by UUID: the lower one keeps it.
 *
 * <p>Every packet also carries the sender's color and the color of each mined cell. Colors are unique the same way:
 * when two nodes say the same color, the one with the higher UUID moves to the lowest color no other node uses, and
 * {@link #colorChanged} tells the caller to save it with the node's identity.
 *
 * <p>Ownership is a courtesy between nodes, not a consensus rule: the chain accepts a bounty of any length.
 */
final class ClaimService {
    /** How long a claimer waits for a TAKEN before it owns the length. */
    static final int ASK_MILLIS = 1500;

    private static final int NONE = 0;
    private static final int ASKING = 1;
    private static final int OWNED = 2;

    private final Miner2State model;
    private final BitBoard board;
    private final BlockPool pool;
    private int state = NONE;
    private int bit;
    private int answerBy;
    private int seenTip = -1;
    private boolean colorChanged;

    ClaimService(Miner2State model) {
        this.model = model;
        this.board = model.board();
        this.pool = model.pool();
    }

    /** Whether this node's color changed since the last call, so the caller should save the new one. */
    boolean colorChanged() {
        boolean changed = colorChanged;
        colorChanged = false;
        return changed;
    }

    /** The bit length this node owns and may mine, 0 while it owns none. */
    int owned() {
        return state == OWNED ? bit : 0;
    }

    /**
     * Brings the board up to the chain's tip and moves the claim on. Returns whether it wrote a CLAIM into
     * {@code packet}, for the caller to broadcast.
     */
    boolean step(int now, byte[] packet) {
        followTip();
        if (board.full()) {
            board.startRound(board.round() + 1, pool.height(pool.tip()));
            state = NONE;
        }
        if (state != NONE && board.isMined(bit)) {
            state = NONE;
        }
        if (state == ASKING && now - answerBy >= 0) {
            state = OWNED;
        }
        boolean claiming = false;
        if (state == NONE) {
            // Withdraw the length just lost or mined, so it does not look like this node's to take again.
            board.claim(model.nodeId(), 0, now);
            bit = nextFree(now);
            if (bit > 0) {
                state = ASKING;
                answerBy = now + ASK_MILLIS;
                ChainProtocol.writeClaim(packet, ChainProtocol.CLAIM, model.uuidBytes());
                stampBoard(packet, bit);
                claiming = true;
            }
        }
        int own = state == NONE ? 0 : bit;
        board.heard(model.nodeId(), board.ownColor(), now);
        board.claim(model.nodeId(), own, now);
        board.publishOwn(own, state == ASKING);
        return claiming;
    }

    /** Adds the board to a heartbeat that {@link ChainProtocol#writeHello} wrote. */
    void stampHello(byte[] packet) {
        stampBoard(packet, owned());
    }

    /** Takes in a peer's heartbeat: its board, and the length it owns, which an earlier owner keeps. */
    void serveHello(byte[] incoming, int now) {
        if (ChainProtocol.boardRound(incoming) == 0) {
            // A MinerGame node: it has no board and claims nothing.
            return;
        }
        peer(incoming, now);
        int theirs = ChainProtocol.boardBit(incoming);
        board.claim(NodeId.shortId(incoming, ChainProtocol.HELLO_UUID), theirs, now);
        if (theirs != 0 && theirs == bit) {
            boolean theyWin = state == ASKING
                    || state == OWNED && NodeId.precedes(incoming, ChainProtocol.HELLO_UUID, model.uuidBytes(), 0);
            if (theyWin) {
                state = NONE;
            }
        }
    }

    /**
     * Serves a CLAIM or TAKEN. Returns whether it wrote a TAKEN into {@code reply}, for the caller to send back to the
     * claimer.
     */
    boolean serve(byte[] incoming, byte[] reply, int now) {
        if (NodeId.same(incoming, ChainProtocol.HELLO_UUID, model.uuidBytes(), 0)) {
            // This node's own claim, echoed back by the network.
            return false;
        }
        peer(incoming, now);
        int wanted = ChainProtocol.boardBit(incoming);
        if (!BitBoard.onBoard(wanted)) {
            return false;
        }
        int peer = NodeId.shortId(incoming, ChainProtocol.HELLO_UUID);
        boolean mine = state != NONE && bit == wanted;
        if (ChainProtocol.type(incoming) == ChainProtocol.TAKEN) {
            if (!board.isMined(wanted)) {
                board.claim(peer, wanted, now);
            }
            if (state == ASKING && mine) {
                state = NONE;
            }
            return false;
        }
        boolean keep = board.isMined(wanted) || mine && (state == OWNED
                || NodeId.precedes(model.uuidBytes(), 0, incoming, ChainProtocol.HELLO_UUID));
        if (keep) {
            ChainProtocol.writeClaim(reply, ChainProtocol.TAKEN, model.uuidBytes());
            stampBoard(reply, wanted);
            return true;
        }
        if (mine) {
            state = NONE;
        }
        board.claim(peer, wanted, now);
        return false;
    }

    /** Writes this node's board into {@code packet}, saying {@code about} as its bit length. */
    private void stampBoard(byte[] packet, int about) {
        ChainProtocol.writeBoard(packet, board.round(), about, board.minedMask(), board.ownColor() + 1);
        for (int cell = BitBoard.FIRST; cell <= BitBoard.LAST; cell++) {
            ChainProtocol.writeBoardCell(packet, cell, board.isMined(cell) ? board.cellColor(cell) + 1 : 0);
        }
    }

    /**
     * Takes in what any packet from a peer says of its board: its round and mined cells, the colors of those cells,
     * and its own color, moving this node off that color when the peer's UUID is the lower one.
     */
    private void peer(byte[] incoming, int now) {
        int peer = NodeId.shortId(incoming, ChainProtocol.HELLO_UUID);
        int color = ChainProtocol.boardColor(incoming) - 1;
        board.heard(peer, color, now);
        if (board.merge(ChainProtocol.boardRound(incoming), ChainProtocol.boardMined(incoming),
                pool.height(pool.tip()))) {
            for (int cell = BitBoard.FIRST; cell <= BitBoard.LAST; cell++) {
                board.learnCell(cell, ChainProtocol.boardCell(incoming, cell) - 1);
            }
        }
        if (color == board.ownColor()
                && NodeId.precedes(incoming, ChainProtocol.HELLO_UUID, model.uuidBytes(), 0)) {
            int free = board.freeColor(model.nodeId(), now);
            if (free != BitBoard.NO_COLOR) {
                board.publishOwnColor(free);
                colorChanged = true;
            }
        }
    }

    /** Marks the bounty of every held block of this round on the tip's branch, whenever the tip moves. */
    private void followTip() {
        if (pool.tipChanges() == seenTip) {
            return;
        }
        seenTip = pool.tipChanges();
        byte[] blocks = pool.blocks();
        int slot = pool.tip();
        while (slot >= 0 && pool.isValid(slot) && pool.height(slot) > board.roundStart()) {
            int base = slot * BlockFormat.MAX_SIZE;
            board.markMined(BlockFormat.bitLength(Bytes.readLong(blocks, base + BlockFormat.NUMBER)),
                    Bytes.readInt(blocks, base + BlockFormat.MINER));
            slot = pool.parent(slot);
        }
    }

    /** The lowest length neither mined nor claimed by another node, or 0 when there is none. */
    private int nextFree(int now) {
        for (int candidate = BitBoard.FIRST; candidate <= BitBoard.LAST; candidate++) {
            if (!board.isMined(candidate) && board.claimant(candidate, now) == BitBoard.UNKNOWN) {
                return candidate;
            }
        }
        return 0;
    }
}

package io.github.jabrena.juno.usecases.miner.services;

import io.github.jabrena.juno.games.blockchain.Bytes;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.usecases.miner.chain.BlockFormat;
import io.github.jabrena.juno.usecases.miner.chain.BlockPool;
import io.github.jabrena.juno.usecases.miner.peers.NodeId;

import java.io.IOException;
import java.util.UUID;

/** Small append-only journal for the identity, the color and the useful work of a Miner2 node. */
public final class Miner2Persistence {
    private static final String ID_FILE = "MINER2.ID";
    private static final String COLOR_FILE = "MINER2.COL";
    private static final String CHAIN_FILE = "MINER2.CHAIN";
    private static final String FACTOR_FILE = "MINER2.FAC";
    private static final String RUN_FILE = "MINER2.RUN";

    private boolean ready;

    /** Loads the saved node identity, or creates and saves one when no valid identity exists yet. */
    public UUID loadOrCreateIdentity() {
        ready = SdCard.begin();
        UUID identity = ready ? readIdentity() : null;
        if (identity != null) {
            return identity;
        }

        identity = UUID.randomUUID();
        if (ready) {
            writeIdentity(identity);
        }
        return identity;
    }

    /**
     * The color saved by {@link #saveColor}, or -1 when there is none, the card is missing, or what it holds is not a
     * color below {@code colors}. Call it after {@link #loadOrCreateIdentity}, which starts the card.
     */
    public int loadColor(int colors) {
        if (!ready || !SdCard.exists(COLOR_FILE)) {
            return -1;
        }
        java.io.InputStream file = SdCard.open(COLOR_FILE);
        if (file == null) {
            return -1;
        }
        try {
            int digit = hex(file.read());
            file.close();
            return digit >= 0 && digit < colors ? digit : -1;
        } catch (IOException failed) {
            return -1;
        }
    }

    /** Saves this node's color, so it keeps it after a restart, like its identity. */
    public void saveColor(int color) {
        if (!ready) {
            return;
        }
        SdCard.remove(COLOR_FILE);
        SdCard.appendText(COLOR_FILE, color + "\n");
    }

    /** Whether the SD card was available when the identity was loaded. */
    public boolean isReady() {
        return ready;
    }

    public void appendMining(BlockPool pool, int tip) {
        if (!ready) {
            return;
        }
        byte[] blocks = pool.blocks();
        int base = tip * BlockFormat.MAX_SIZE;
        // The nonce is the final four bytes of the fixed 80-byte header.
        SdCard.appendLine(RUN_FILE, "height=" + pool.height(tip) + " nonce=" + Bytes.readInt(blocks, base + 76));
        int count = blocks[base + BlockFormat.COUNT] & 0xff;
        for (int i = 0; i < count; i++) {
            int factor = base + BlockFormat.FACTORS + i * 9;
            SdCard.appendLine(FACTOR_FILE, "" + Bytes.readLong(blocks, factor)
                    + "^" + (blocks[factor + 8] & 0xff));
        }
    }

    /** Records a tip learned from the network, so the replicated chain is auditable after a restart. */
    public void appendChain(BlockPool pool, int tip) {
        if (!ready) {
            return;
        }
        byte[] blocks = pool.blocks();
        int base = tip * BlockFormat.MAX_SIZE;
        SdCard.appendLine(CHAIN_FILE, "height=" + pool.height(tip));
        SdCard.appendLine(CHAIN_FILE, "work=" + pool.work(tip));
        SdCard.appendLine(CHAIN_FILE, "miner=" + Bytes.readInt(blocks, base + BlockFormat.MINER));
        int count = blocks[base + BlockFormat.COUNT] & 0xff;
        for (int i = 0; i < count; i++) {
            int factor = base + BlockFormat.FACTORS + i * 9;
            SdCard.appendLine(CHAIN_FILE, "f=" + Bytes.readLong(blocks, factor) + "^" + (blocks[factor + 8] & 0xff));
        }
    }

    private UUID readIdentity() {
        if (!SdCard.exists(ID_FILE)) {
            return null;
        }
        java.io.InputStream file = SdCard.open(ID_FILE);
        if (file == null) {
            return null;
        }
        try {
            int count = 0;
            int value;
            long mostSignificantBits = 0L;
            long leastSignificantBits = 0L;
            while (count < NodeId.SIZE * 2 && (value = file.read()) >= 0) {
                int digit = hex(value);
                if (digit >= 0) {
                    if (count < NodeId.SIZE) {
                        mostSignificantBits = mostSignificantBits << 4 | digit;
                    } else {
                        leastSignificantBits = leastSignificantBits << 4 | digit;
                    }
                    count++;
                }
            }
            file.close();
            return count == NodeId.SIZE * 2 ? new UUID(mostSignificantBits, leastSignificantBits) : null;
        } catch (IOException failed) {
            return null;
        }
    }

    private void writeIdentity(UUID identity) {
        SdCard.remove(ID_FILE);
        long mostSignificantBits = identity.getMostSignificantBits();
        long leastSignificantBits = identity.getLeastSignificantBits();
        SdCard.appendText(ID_FILE, hexWord((int) (mostSignificantBits >>> 32)));
        SdCard.appendText(ID_FILE, hexWord((int) mostSignificantBits));
        SdCard.appendText(ID_FILE, hexWord((int) (leastSignificantBits >>> 32)));
        SdCard.appendText(ID_FILE, hexWord((int) leastSignificantBits));
        SdCard.appendText(ID_FILE, "\n");
    }

    private static String hexWord(int value) {
        return hexByte((byte) (value >>> 24)) + hexByte((byte) (value >>> 16))
                + hexByte((byte) (value >>> 8)) + hexByte((byte) value);
    }

    private static String hexByte(byte value) {
        final String digits = "0123456789abcdef";
        int unsigned = value & 0xff;
        return "" + digits.charAt(unsigned >>> 4) + digits.charAt(unsigned & 15);
    }

    private static int hex(int value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return -1;
    }
}

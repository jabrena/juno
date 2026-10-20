package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * Loads firmware into an RCX brick over infrared, as the LEGO IR tower does: the brick's boot ROM
 * answers these four commands even when it has no firmware, which is the state it is in after its batteries
 * were out (the display then shows program {@code 0}). Set up the link with {@link RcxRemote#begin} first,
 * with both the IR LED and the IR receiver module wired, since every step waits for the brick's reply.
 *
 * <p>The sequence is {@link #delete}, {@link #start} with the image's entry address and checksum, one
 * successful {@link #transfer} per block of up to {@value #BLOCK_SIZE} bytes numbered from 1, then {@link #unlock}; a
 * 16 KB image takes about three minutes at 2400 baud.
 */
public final class RcxFirmware {
    private final RcxLink link;
    private final Clock clock;

    /** The largest block {@link #transfer} sends. */
    public static final int BLOCK_SIZE = 200;
    /** Where the RCX firmware image starts in the brick's memory. */
    public static final int IMAGE_START = 0x8000;
    /** The image checksum covers the bytes below this address only. */
    public static final int CHECKSUM_END = 0xCC00;
    /** {@link #transfer} result when the brick did not answer. */
    public static final int NO_REPLY = -1;

    private static final int OPCODE_VERSIONS = 0x15;
    private static final int OPCODE_DELETE = 0x65;
    private static final int OPCODE_START = 0x75;
    private static final int OPCODE_TRANSFER = 0x45;
    private static final int OPCODE_UNLOCK = 0xA5;
    private static final int OPCODE_TOGGLE = 0x08;
    private static final int RETRIES = 5;
    private static final int REPLY_MILLIS = 150;
    private static final int TRANSFER_REPLY_MILLIS = 500;
    private static final int UNLOCK_MILLIS = 1000;

    private int frameSum;
    private boolean toggle;
    private int lastTransferIndex = -1;
    private int transferCode;

    /** A firmware loader on the board's own infrared link and timer. Create it once, at startup. */
    public RcxFirmware() {
        this(new RcxLink(), MCU.getClock());
    }

    /** A firmware loader on a link shared with other RCX classes, so they share one toggle bit. */
    public RcxFirmware(RcxLink link) {
        this(link, MCU.getClock());
    }

    /** A firmware loader on {@code link}, timing its waits on {@code clock}: a simulated clock in a unit test. */
    public RcxFirmware(RcxLink link, Clock clock) {
        this.link = link;
        this.clock = clock;
    }

    /**
     * Asks the brick for its versions: {@code 1} when it runs firmware, {@code 0} when only its boot ROM runs (so
     * it ignores motor, sensor and sound commands until firmware is loaded), or {@link #NO_REPLY}. The boot ROM
     * answers this too, so it tells a brick without firmware from one out of range.
     */
    public int installed() {
        for (int attempt = 0; attempt < RETRIES; attempt++) {
            beginFrame(toggled(OPCODE_VERSIONS));
            sendPair(1);
            sendPair(3);
            sendPair(5);
            sendPair(7);
            sendPair(11);
            endFrame();
            if (link.awaitReply(OPCODE_VERSIONS, REPLY_MILLIS) >= 8) {
                int firmware = link.argument(4) | link.argument(5) | link.argument(6) | link.argument(7);
                return firmware != 0 ? 1 : 0;
            }
        }
        return NO_REPLY;
    }

    /** Deletes the firmware the brick holds, if any. Returns whether the brick acknowledged. */
    public boolean delete() {
        for (int attempt = 0; attempt < RETRIES; attempt++) {
            beginFrame(toggled(OPCODE_DELETE));
            sendPair(1);
            sendPair(3);
            sendPair(5);
            sendPair(7);
            sendPair(11);
            endFrame();
            if (link.awaitReply(OPCODE_DELETE, REPLY_MILLIS) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Announces an image that will run from {@code entryAddress}, whose bytes below {@link #CHECKSUM_END} add
     * up to {@code checksum} (modulo 65536). Returns whether the brick acknowledged.
     */
    public boolean start(int entryAddress, int checksum) {
        for (int attempt = 0; attempt < RETRIES; attempt++) {
            beginFrame(toggled(OPCODE_START));
            sendPair(entryAddress & 0xFF);
            sendPair((entryAddress >> 8) & 0xFF);
            sendPair(checksum & 0xFF);
            sendPair((checksum >> 8) & 0xFF);
            sendPair(0);
            endFrame();
            if (link.awaitReply(OPCODE_START, REPLY_MILLIS) == 1 && link.argument0 == 0) {
                lastTransferIndex = -1;
                return true;
            }
        }
        return false;
    }

    /**
     * Sends block number {@code index} (1 for the first) holding the first {@code length} bytes of {@code block}
     * (at most {@value #BLOCK_SIZE}), once; a full block takes about two seconds. Returns the brick's status,
     * {@code 0} when it stored the block, {@link #NO_REPLY} when no answer came, or the brick's error code. The
     * caller may retry the identical block, but the boot ROM mishandles duplicate firmware transfers: repeated
     * missing replies require restarting the download, never assuming the block was stored. Use index {@code 0}
     * for the final block; it finalizes the image checksum and cannot be retransmitted.
     */
    public int transfer(int index, byte[] block, int length) {
        if (index != lastTransferIndex) {
            transferCode = toggled(OPCODE_TRANSFER);
            lastTransferIndex = index;
        }
        beginFrame(transferCode);
        sendPair(index & 0xFF);
        sendPair((index >> 8) & 0xFF);
        sendPair(length & 0xFF);
        sendPair((length >> 8) & 0xFF);
        int blockSum = 0;
        for (int i = 0; i < length; i++) {
            int value = block[i] & 0xFF;
            blockSum += value;
            sendPair(value);
        }
        sendPair(blockSum & 0xFF);
        endFrame();
        if (link.awaitReply(OPCODE_TRANSFER, TRANSFER_REPLY_MILLIS) < 1) {
            return NO_REPLY;
        }
        return link.argument0;
    }

    /**
     * Starts the downloaded firmware once the brick has checked it. The brick answers with a long text that this
     * receiver does not decode, so the call only reports whether any answer arrived; that the firmware runs shows
     * on the brick's display (program {@code 1}) and in {@link RcxBrick} commands being acknowledged.
     */
    public boolean unlock() {
        beginFrame(toggled(OPCODE_UNLOCK));
        sendPair('L');
        sendPair('E');
        sendPair('G');
        sendPair('O');
        sendPair(0xAE);
        endFrame();
        int started = clock.millis();
        while (clock.millis() - started < UNLOCK_MILLIS) {
            if (Infrared.receiving()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The opcode with its toggle bit flipped from the last message: the brick ignores a message whose opcode
     * equals the previous one as a repeat, so a retry after a lost reply must differ.
     */
    private int toggled(int opcode) {
        toggle = !toggle;
        return toggle ? opcode | OPCODE_TOGGLE : opcode;
    }

    private void beginFrame(int code) {
        Infrared.writeByte(0x55);
        Infrared.writeByte(0xFF);
        Infrared.writeByte(0x00);
        frameSum = 0;
        sendPair(code);
    }

    private void sendPair(int value) {
        frameSum += value;
        Infrared.writeByte(value);
        Infrared.writeByte(~value & 0xFF);
    }

    private void endFrame() {
        int sum = frameSum & 0xFF;
        Infrared.writeByte(sum);
        Infrared.writeByte(~sum & 0xFF);
    }
}

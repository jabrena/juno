package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.ir.Infrared;

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
    private RcxFirmware() {
    }

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
    private static final int UNLOCK_MILLIS = 1000;

    private static int frameSum;
    private static boolean toggle;

    /**
     * Asks the brick for its versions: {@code 1} when it runs firmware, {@code 0} when only its boot ROM runs (so
     * it ignores motor, sensor and sound commands until firmware is loaded), or {@link #NO_REPLY}. The boot ROM
     * answers this too, so it tells a brick without firmware from one out of range.
     */
    public static int installed() {
        for (int attempt = 0; attempt < RETRIES; attempt++) {
            beginFrame(toggled(OPCODE_VERSIONS));
            sendPair(1);
            sendPair(3);
            sendPair(5);
            sendPair(7);
            sendPair(11);
            endFrame();
            if (RcxLink.awaitReply(OPCODE_VERSIONS, REPLY_MILLIS) >= 8) {
                int firmware = RcxLink.argument(4) | RcxLink.argument(5) | RcxLink.argument(6) | RcxLink.argument(7);
                return firmware != 0 ? 1 : 0;
            }
        }
        return NO_REPLY;
    }

    /** Deletes the firmware the brick holds, if any. Returns whether the brick acknowledged. */
    public static boolean delete() {
        for (int attempt = 0; attempt < RETRIES; attempt++) {
            beginFrame(toggled(OPCODE_DELETE));
            sendPair(1);
            sendPair(3);
            sendPair(5);
            sendPair(7);
            sendPair(11);
            endFrame();
            if (RcxLink.awaitReply(OPCODE_DELETE, REPLY_MILLIS) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Announces an image that will run from {@code entryAddress}, whose bytes below {@link #CHECKSUM_END} add
     * up to {@code checksum} (modulo 65536). Returns whether the brick acknowledged.
     */
    public static boolean start(int entryAddress, int checksum) {
        for (int attempt = 0; attempt < RETRIES; attempt++) {
            beginFrame(toggled(OPCODE_START));
            sendPair(entryAddress & 0xFF);
            sendPair((entryAddress >> 8) & 0xFF);
            sendPair(checksum & 0xFF);
            sendPair((checksum >> 8) & 0xFF);
            sendPair(0);
            endFrame();
            if (RcxLink.awaitReply(OPCODE_START, REPLY_MILLIS) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends block number {@code index} (1 for the first) holding the first {@code length} bytes of {@code block}
     * (at most {@value #BLOCK_SIZE}), once; a full block takes about two seconds. Returns the brick's status,
     * {@code 0} when it stored the block, {@link #NO_REPLY} when no answer came, or the brick's error code. The
     * caller retries: the toggle bit of the opcode follows the block number, so the brick ignores a resend straight
     * after it stored the block and answers the one after that.
     */
    public static int transfer(int index, byte[] block, int length) {
        int code = (index & 1) != 0 ? OPCODE_TRANSFER | OPCODE_TOGGLE : OPCODE_TRANSFER;
        beginFrame(code);
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
        if (RcxLink.awaitReply(OPCODE_TRANSFER, REPLY_MILLIS) < 1) {
            return NO_REPLY;
        }
        return RcxLink.argument0;
    }

    /**
     * Starts the downloaded firmware once the brick has checked it. The brick answers with a long text that this
     * receiver does not decode, so the call only reports whether any answer arrived; that the firmware runs shows
     * on the brick's display (program {@code 1}) and in {@link RcxBrick} commands being acknowledged.
     */
    public static boolean unlock() {
        beginFrame(toggled(OPCODE_UNLOCK));
        sendPair('L');
        sendPair('E');
        sendPair('G');
        sendPair('O');
        sendPair(0xAE);
        endFrame();
        int started = Clock.millis();
        while (Clock.millis() - started < UNLOCK_MILLIS) {
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
    private static int toggled(int opcode) {
        toggle = !toggle;
        return toggle ? opcode | OPCODE_TOGGLE : opcode;
    }

    private static void beginFrame(int code) {
        Infrared.writeByte(0x55);
        Infrared.writeByte(0xFF);
        Infrared.writeByte(0x00);
        frameSum = 0;
        sendPair(code);
    }

    private static void sendPair(int value) {
        frameSum += value;
        Infrared.writeByte(value);
        Infrared.writeByte(~value & 0xFF);
    }

    private static void endFrame() {
        int sum = frameSum & 0xFF;
        Infrared.writeByte(sum);
        Infrared.writeByte(~sum & 0xFF);
    }
}

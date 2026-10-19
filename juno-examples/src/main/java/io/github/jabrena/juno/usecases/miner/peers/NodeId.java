package io.github.jabrena.juno.usecases.miner.peers;

import io.github.jabrena.juno.api.hardware.MCU;

import io.github.jabrena.juno.api.hardware.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.util.Random;
import io.github.jabrena.juno.games.blockchain.Bytes;

/**
 * A node's identity: a random version-4 UUID drawn when the node boots, 16 bytes that peers use to tell
 * heartbeats apart. A board that restarts becomes a new node, and the old one ages out of its peers' tables.
 *
 * <p>The board's random generator is seeded from the boot clock mixed with its own address on the network. The
 * clock alone would be about 32 bits of luck; the address is different for every board on a network, so two boards
 * never draw the same sequence, however close their boot times are.
 */
public final class NodeId {
    private static final Clock CLOCK = MCU.getClock();
    public static final int SIZE = 16;

    private static final String HEX = "0123456789abcdef";

    private NodeId() {
    }

    /** Fills {@code uuid[0..SIZE)} with a new random UUID; call it once Wi-Fi is up, so the address is known. */
    public static void generate(byte[] uuid) {
        int[] address = new int[4];
        Wifi.localIP(address);
        Random.seed(CLOCK.micros() ^ (address[0] << 24 | address[1] << 16 | address[2] << 8 | address[3]));
        for (int i = 0; i < SIZE; i++) {
            // More boot-clock jitter goes into every byte, on top of the seeded sequence.
            uuid[i] = (byte) (Random.nextInt(256) ^ CLOCK.micros());
        }
        uuid[6] = (byte) (uuid[6] & 0x0f | 0x40);
        uuid[8] = (byte) (uuid[8] & 0x3f | 0x80);
    }

    /** The first four bytes as the 32-bit id that a block records for its miner; never 0. */
    public static int shortId(byte[] uuid, int offset) {
        int id = Bytes.readInt(uuid, offset);
        return id == 0 ? 1 : id;
    }

    /** Whether the UUIDs at {@code a[aOffset..)} and {@code b[bOffset..)} are the same. */
    public static boolean same(byte[] a, int aOffset, byte[] b, int bOffset) {
        for (int i = 0; i < SIZE; i++) {
            if (a[aOffset + i] != b[bOffset + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the UUID at {@code a[aOffset..)} sorts before the one at {@code b[bOffset..)}, comparing unsigned bytes:
     * an order every node agrees on, to settle a tie between two of them.
     */
    public static boolean precedes(byte[] a, int aOffset, byte[] b, int bOffset) {
        for (int i = 0; i < SIZE; i++) {
            int x = a[aOffset + i] & 0xff;
            int y = b[bOffset + i] & 0xff;
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    /** Prints {@code uuid} in the usual 8-4-4-4-12 form, without a line break. */
    public static void print(byte[] uuid) {
        for (int i = 0; i < SIZE; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                Serial.print("-");
            }
            Serial.print(hexByte(uuid[i]));
        }
    }

    private static String hexByte(byte value) {
        int unsigned = value & 0xff;
        return "" + HEX.charAt(unsigned >>> 4) + HEX.charAt(unsigned & 0xf);
    }
}

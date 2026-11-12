package io.github.jabrena.juno.hardware.security;

import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.util.Delay;
import org.jspecify.annotations.Nullable;

/**
 * An ATECC608 secure element on an I2C bus, holding named secrets in data slots. Each slot holds a length byte
 * followed by the value, in two 32-byte blocks. The slots are read in clear with plain {@code Read} commands: no read
 * key is compiled into the firmware, and any code on the board can read them. Every command frame and response carries
 * a CRC-16, which catches a corrupted transfer but does not authenticate the stored value.
 *
 * <p>The chip sits at 7-bit address {@link #ADDRESS}. A value that is unknown, missing, empty, too long for the caller's
 * buffer, or fails a check is reported as missing, and nothing falls back to a plaintext copy. The instance allocates
 * its buffers once, at construction, and {@link #read} allocates nothing.
 */
public final class Atecc608 implements SecretStore {
    /** The chip's 7-bit I2C address. */
    public static final int ADDRESS = 0x60;

    private static final int WAKE_ADDRESS = 0x00;
    private static final int WORD_SLEEP = 0x01;
    private static final int WORD_COMMAND = 0x03;
    private static final int OPCODE_READ = 0x02;
    private static final int OPCODE_WRITE = 0x12;
    /** Write's frame: count 39 (count, opcode, parameters, 32 data bytes, CRC-16), after the word address. */
    private static final int WRITE_COUNT = 39;
    private static final int STATUS_RESPONSE_BYTES = 4;
    private static final int WRITE_DELAY_MILLIS = 30;
    /** Read's zone field: data zone is 2, in bits 1..0; bit 7 selects a 32-byte read. */
    private static final int READ_DATA_ZONE_32 = 0x82;
    private static final int BLOCK_BYTES = 32;
    private static final int SLOT_BYTES = 2 * BLOCK_BYTES;
    /** The longest value: one length byte precedes it in the slot. */
    static final int MAX_VALUE_BYTES = SLOT_BYTES - 1;
    private static final int READ_RESPONSE_BYTES = BLOCK_BYTES + 3;
    private static final int READ_ATTEMPTS = 3;
    private static final int WAKE_DELAY_MILLIS = 2;
    private static final int READ_DELAY_MILLIS = 6;
    private static final int RETRY_DELAY_MILLIS = 1;

    private final I2c i2c;
    private final SecretSlots slots;
    /** Word address, then a command frame: count, opcode, parameters, CRC-16. */
    private final byte[] command = new byte[8];
    private final byte[] response = new byte[READ_RESPONSE_BYTES];
    /** Word address, then a Write frame: count, opcode, parameters, one 32-byte block, CRC-16. */
    private final byte[] writeFrame = new byte[1 + WRITE_COUNT];
    private final byte[] status = new byte[STATUS_RESPONSE_BYTES];
    private final byte[] slot = new byte[SLOT_BYTES];
    private final byte[] value = new byte[MAX_VALUE_BYTES];

    /**
     * A driver over {@code i2c}, the bus the chip is wired to, reading the names in {@code slots}. The driver holds no
     * secret names or slot numbers: the program that provisions the chip owns that mapping.
     */
    public Atecc608(I2c i2c, SecretSlots slots) {
        this.i2c = i2c;
        this.slots = slots;
    }

    /** The store this chip provides; the same instance is returned every call. */
    public SecretStore getSecretStore() {
        return this;
    }

    @Override
    public int read(String key, byte[] buffer) {
        int slotNumber = slots.slotOf(key);
        if (slotNumber < 0 || !readBlock(slotNumber, 0) || !readBlock(slotNumber, 1)) {
            return -1;
        }
        int length = slot[0] & 0xFF;
        if (length == 0 || length > MAX_VALUE_BYTES || length > buffer.length) {
            return -1;
        }
        for (int i = 0; i < length; i++) {
            buffer[i] = slot[i + 1];
        }
        return length;
    }

    /**
     * Stores {@code value[0..length)} under {@code key}, rewriting both blocks of its slot so no byte of an older, longer
     * value survives. A {@code length} of {@code 0} clears the name. Returns {@code length}, or a negative value when the
     * name is unknown, the bytes do not fit the slot, the chip does not answer, or it reports an error (for example a
     * locked data zone). A failed write may leave the slot partly rewritten, so the caller should rewrite it.
     */
    @Override
    public int clear(String key) {
        return write(key, slot, 0) == 0 ? 0 : -1;
    }

    @Override
    public int clearAll() {
        int result = 0;
        for (int i = 0; i < slots.count(); i++) {
            if (clear(slots.nameAt(i)) != 0) {
                result = -1;
            }
        }
        return result;
    }

    @Override
    public int write(String key, byte[] value, int length) {
        int slotNumber = slots.slotOf(key);
        if (slotNumber < 0 || length < 0 || length > MAX_VALUE_BYTES || length > value.length) {
            return -1;
        }
        slot[0] = (byte) length;
        for (int i = 0; i < MAX_VALUE_BYTES; i++) {
            slot[i + 1] = i < length ? value[i] : 0;
        }
        if (!writeBlock(slotNumber, 0) || !writeBlock(slotNumber, 1)) {
            return -1;
        }
        return length;
    }

    @Override
    public @Nullable String getString(String key) {
        int length = read(key, value);
        if (length < 0) {
            return null;
        }
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append((char) (value[i] & 0xFF));
        }
        return text.toString();
    }

    /** CRC-16 with polynomial 0x8005 and zero initial value, processed bit by bit as the chip does. */
    static int crc16(byte[] data, int offset, int length) {
        int crc = 0;
        for (int i = offset; i < offset + length; i++) {
            int current = data[i] & 0xFF;
            for (int shift = 0; shift < 8; shift++) {
                int dataBit = (current >> shift) & 1;
                int crcBit = (crc >> 15) & 1;
                crc = (crc << 1) & 0xFFFF;
                if (dataBit != crcBit) {
                    crc ^= 0x8005;
                }
            }
        }
        return crc;
    }

    /** Reads one 32-byte block of {@code slotNumber} into the slot buffer; false on any bus, length or CRC failure. */
    private boolean readBlock(int slotNumber, int block) {
        wake();
        int address = (slotNumber << 8) | (block << 3);
        command[0] = WORD_COMMAND;
        command[1] = 7;
        command[2] = OPCODE_READ;
        command[3] = (byte) READ_DATA_ZONE_32;
        command[4] = (byte) (address & 0xFF);
        command[5] = (byte) ((address >> 8) & 0xFF);
        int crc = crc16(command, 1, 6);
        command[6] = (byte) (crc & 0xFF);
        command[7] = (byte) ((crc >> 8) & 0xFF);
        if (i2c.write(ADDRESS, command, command.length) != command.length) {
            sleep();
            return false;
        }
        Delay.millis(READ_DELAY_MILLIS);
        boolean ok = false;
        for (int attempt = 0; attempt < READ_ATTEMPTS && !ok; attempt++) {
            ok = receiveBlock(block);
            if (!ok) {
                Delay.millis(RETRY_DELAY_MILLIS);
            }
        }
        sleep();
        return ok;
    }

    /** Writes one 32-byte block of the slot buffer to {@code slotNumber}; false on any bus, status or CRC failure. */
    private boolean writeBlock(int slotNumber, int block) {
        wake();
        int address = (slotNumber << 8) | (block << 3);
        writeFrame[0] = WORD_COMMAND;
        writeFrame[1] = WRITE_COUNT;
        writeFrame[2] = OPCODE_WRITE;
        writeFrame[3] = (byte) READ_DATA_ZONE_32;
        writeFrame[4] = (byte) (address & 0xFF);
        writeFrame[5] = (byte) ((address >> 8) & 0xFF);
        for (int i = 0; i < BLOCK_BYTES; i++) {
            writeFrame[6 + i] = slot[block * BLOCK_BYTES + i];
        }
        int crc = crc16(writeFrame, 1, WRITE_COUNT - 2);
        writeFrame[1 + WRITE_COUNT - 2] = (byte) (crc & 0xFF);
        writeFrame[1 + WRITE_COUNT - 1] = (byte) ((crc >> 8) & 0xFF);
        if (i2c.write(ADDRESS, writeFrame, writeFrame.length) != writeFrame.length) {
            sleep();
            return false;
        }
        Delay.millis(WRITE_DELAY_MILLIS);
        boolean ok = false;
        for (int attempt = 0; attempt < READ_ATTEMPTS && !ok; attempt++) {
            ok = receiveStatus();
            if (!ok) {
                Delay.millis(RETRY_DELAY_MILLIS);
            }
        }
        sleep();
        return ok;
    }

    /** Receives a 4-byte status response; true only for a well-formed success (status 0x00). */
    private boolean receiveStatus() {
        if (i2c.read(ADDRESS, status, STATUS_RESPONSE_BYTES) != STATUS_RESPONSE_BYTES) {
            return false;
        }
        if ((status[0] & 0xFF) != STATUS_RESPONSE_BYTES) {
            return false;
        }
        int crc = crc16(status, 0, 2);
        int received = (status[2] & 0xFF) | ((status[3] & 0xFF) << 8);
        return crc == received && status[1] == 0;
    }

    /** Receives a read response and, when its length and CRC check out, copies the block into the slot buffer. */
    private boolean receiveBlock(int block) {
        if (i2c.read(ADDRESS, response, READ_RESPONSE_BYTES) != READ_RESPONSE_BYTES) {
            return false;
        }
        if ((response[0] & 0xFF) != READ_RESPONSE_BYTES) {
            return false;
        }
        int crc = crc16(response, 0, READ_RESPONSE_BYTES - 2);
        int received = (response[READ_RESPONSE_BYTES - 2] & 0xFF) | ((response[READ_RESPONSE_BYTES - 1] & 0xFF) << 8);
        if (crc != received) {
            return false;
        }
        for (int i = 0; i < BLOCK_BYTES; i++) {
            slot[block * BLOCK_BYTES + i] = response[1 + i];
        }
        return true;
    }

    /** Wakes the chip with a low pulse on SDA: a transfer to address 0 that sends no data. */
    private void wake() {
        i2c.write(WAKE_ADDRESS, command, 0);
        Delay.millis(WAKE_DELAY_MILLIS);
    }

    /** Puts the chip to sleep, so the next command starts with a wake pulse. */
    private void sleep() {
        command[0] = WORD_SLEEP;
        i2c.write(ADDRESS, command, 1);
    }
}

package io.github.jabrena.juno.hardware.security;

import org.jspecify.annotations.Nullable;

/**
 * The part every store shares: a fixed set of slots, each holding one self-describing record, so a program asks for a
 * secret by name and never learns which slot holds it.
 *
 * <p>A record is {@code [name length][name bytes][value bytes]} and fills at most {@link SecretStore#RECORD_BYTES}
 * bytes of its slot. A lookup reads the slots in order and returns the first record whose name matches; a write
 * replaces the record with that name in place, or takes the first empty slot. A slot whose bytes do not form a record,
 * such as a raw value from before records carried their names, is never matched and never overwritten.
 *
 * <p>The instance owns its scratch buffers and allocates nothing while reading or writing; only
 * {@link #getString} allocates its result.
 */
abstract class NamedSlotStore implements SecretStore {
    /** How many slots most stores have, 0 to 15; a lookup and a whole-store {@code clearAll} walk all of them. */
    static final int SLOT_COUNT = 16;
    /** The longest name, as {@code juno:secret} also checks before it uploads one. */
    static final int MAX_NAME_BYTES = 31;

    final boolean available;
    private final int slotCount;
    private final byte[] record = new byte[RECORD_BYTES];
    private final byte[] textBytes = new byte[RECORD_BYTES];
    private int recordLength;
    private int firstEmptySlot;

    NamedSlotStore(boolean available) {
        this(available, SLOT_COUNT);
    }

    /** A store with {@code slotCount} slots, for one whose storage holds fewer than {@link #SLOT_COUNT}. */
    NamedSlotStore(boolean available, int slotCount) {
        this.available = available;
        this.slotCount = slotCount;
    }

    /** Copies slot {@code slot} into {@code buffer} and returns its length, or a value {@code <= 0} when it is empty. */
    abstract int readSlot(int slot, byte[] buffer, int capacity);

    /** Stores {@code bytes[0..length)} in slot {@code slot}, replacing whatever was there. */
    abstract boolean writeSlot(int slot, byte[] bytes, int length);

    /** Empties slot {@code slot}. */
    abstract boolean clearSlot(int slot);

    /** Empties every slot, trying each one even after a failure. */
    boolean clearEverySlot() {
        boolean cleared = true;
        for (int slot = 0; slot < slotCount; slot++) {
            if (!clearSlot(slot)) {
                cleared = false;
            }
        }
        return cleared;
    }

    @Override
    public int read(String key, byte[] buffer) {
        if (!available || !isValidName(key)) {
            return -1;
        }
        if (locate(key) < 0) {
            return -1;
        }
        int valueStart = 1 + key.length();
        int valueLength = recordLength - valueStart;
        if (valueLength > buffer.length) {
            return -1;
        }
        for (int i = 0; i < valueLength; i++) {
            buffer[i] = record[valueStart + i];
        }
        return valueLength;
    }

    @Override
    public int write(String key, byte[] value, int length) {
        if (!available || !isValidName(key) || length < 0 || length > value.length
                || length > RECORD_BYTES - 1 - key.length()) {
            return -1;
        }
        int slot = locate(key);
        if (length == 0) {
            return slot < 0 || clearSlot(slot) ? 0 : -1;
        }
        if (slot < 0) {
            slot = firstEmptySlot;
        }
        if (slot < 0) {
            return -1;
        }
        int nameLength = key.length();
        record[0] = (byte) nameLength;
        for (int i = 0; i < nameLength; i++) {
            record[1 + i] = (byte) key.charAt(i);
        }
        for (int i = 0; i < length; i++) {
            record[1 + nameLength + i] = value[i];
        }
        return writeSlot(slot, record, 1 + nameLength + length) ? length : -1;
    }

    @Override
    public int clear(String key) {
        return write(key, record, 0) == 0 ? 0 : -1;
    }

    @Override
    public int clearAll() {
        return available && clearEverySlot() ? 0 : -1;
    }

    @Override
    public @Nullable String getString(String key) {
        int length = read(key, textBytes);
        if (length < 0) {
            return null;
        }
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append((char) (textBytes[i] & 0xFF));
        }
        return text.toString();
    }

    /**
     * The slot whose record is named {@code key}, with that record left in {@link #record}, or {@code -1} when no slot
     * holds it. Also records the first empty slot seen, or {@code -1}, for a write that needs a new one.
     */
    private int locate(String key) {
        firstEmptySlot = -1;
        for (int slot = 0; slot < slotCount; slot++) {
            int length = readSlot(slot, record, RECORD_BYTES);
            if (length <= 0) {
                if (firstEmptySlot < 0) {
                    firstEmptySlot = slot;
                }
            } else if (length <= RECORD_BYTES && isRecordNamed(key, length)) {
                recordLength = length;
                return slot;
            }
        }
        return -1;
    }

    private boolean isRecordNamed(String key, int length) {
        int nameLength = key.length();
        if (record[0] != nameLength || length <= 1 + nameLength) {
            return false;
        }
        for (int i = 0; i < nameLength; i++) {
            if (record[1 + i] != (byte) key.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** One to {@link #MAX_NAME_BYTES} printable, non-space ASCII characters. */
    private static boolean isValidName(String key) {
        int length = key.length();
        if (length == 0 || length > MAX_NAME_BYTES) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char character = key.charAt(i);
            if (character <= ' ' || character > '~') {
                return false;
            }
        }
        return true;
    }
}

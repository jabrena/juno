package io.github.jabrena.juno.hardware.security;

/**
 * The names a program reads from an {@link Atecc608} and the data slot that holds each one. The program builds it once,
 * at startup, with the slot numbers its provisioning used; the driver itself knows no names. A name is added with
 * {@link #add}, and a lookup for a name that was never added returns {@code -1}.
 */
public final class SecretSlots {
    /** The most names a table holds: one entry per data slot in the chip. */
    public static final int MAX_NAMES = 16;
    /** The chip's data slots run from 0 to 15. */
    private static final int SLOT_COUNT = 16;

    private final String[] names = new String[MAX_NAMES];
    private final int[] slots = new int[MAX_NAMES];
    private int count;

    /** An empty table, with room for {@link #MAX_NAMES} names. */
    public SecretSlots() {
    }

    /**
     * Maps {@code name} to data slot {@code slot}. Fails when the table is full, the slot is outside the chip's data
     * slots, or the name is already mapped, so a wrong table is caught at startup rather than as a missing secret.
     */
    public SecretSlots add(String name, int slot) {
        if (count == MAX_NAMES) {
            throw new IllegalStateException("the table holds " + MAX_NAMES + " names; no room for " + name);
        }
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("slot must be 0 to " + (SLOT_COUNT - 1) + ": " + slot);
        }
        if (slotOf(name) >= 0) {
            throw new IllegalStateException("name is already mapped: " + name);
        }
        names[count] = name;
        slots[count] = slot;
        count++;
        return this;
    }

    /** How many names the table holds. */
    public int count() {
        return count;
    }

    /** The {@code index}-th name added, from {@code 0} up to {@link #count()} minus one. */
    public String nameAt(int index) {
        return names[index];
    }

    /** Whether {@code name} was added to this table. */
    public boolean contains(String name) {
        return slotOf(name) >= 0;
    }

    /** The slot holding {@code name}, or {@code -1} when the name was never added. */
    int slotOf(String name) {
        for (int i = 0; i < count; i++) {
            if (names[i].equals(name)) {
                return slots[i];
            }
        }
        return -1;
    }
}

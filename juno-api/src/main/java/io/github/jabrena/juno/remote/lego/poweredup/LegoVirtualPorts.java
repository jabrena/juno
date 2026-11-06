package io.github.jabrena.juno.remote.lego.poweredup;

/** The virtual ports the hub made by pairing two motor ports (Hub Attached I/O). Four slots, allocated once. */
final class LegoVirtualPorts {
    static final int SLOTS = 4;
    private static final int FREE = -1;

    private final int[] ids = new int[SLOTS];
    private final int[] first = new int[SLOTS];
    private final int[] second = new int[SLOTS];

    LegoVirtualPorts() {
        clear();
    }

    void clear() {
        for (int i = 0; i < SLOTS; i++) {
            ids[i] = FREE;
        }
    }

    /** Records that the hub announced virtual port {@code id} pairing {@code a} and {@code b}, replacing an old one. */
    void announce(int id, int a, int b) {
        forget(id);
        for (int i = 0; i < SLOTS; i++) {
            if (ids[i] == FREE) {
                ids[i] = id;
                first[i] = a;
                second[i] = b;
                return;
            }
        }
    }

    /** The virtual port pairing {@code a} and {@code b} in either order, or -1 when there is none. */
    int find(int a, int b) {
        for (int i = 0; i < SLOTS; i++) {
            if (ids[i] != FREE && (first[i] == a && second[i] == b || first[i] == b && second[i] == a)) {
                return ids[i];
            }
        }
        return -1;
    }

    /** Forgets virtual port {@code id}, when the hub removes it or the program unlinks it. */
    void forget(int id) {
        for (int i = 0; i < SLOTS; i++) {
            if (ids[i] == id) {
                ids[i] = FREE;
            }
        }
    }
}

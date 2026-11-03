package io.github.jabrena.juno.remote.lego.pf;

/** A channel of a Power Functions receiver, as set by its channel switch: {@code CHANNEL_1} to {@code CHANNEL_4}. */
public enum PFChannel {
    /** The receiver's channel switch set to 1. */
    CHANNEL_1(0),
    /** The receiver's channel switch set to 2. */
    CHANNEL_2(1),
    /** The receiver's channel switch set to 3. */
    CHANNEL_3(2),
    /** The receiver's channel switch set to 4. */
    CHANNEL_4(3);

    private final int value;

    PFChannel(int value) {
        this.value = value;
    }

    /** The channel's value in the protocol's two-bit channel field. */
    int value() {
        return value;
    }
}

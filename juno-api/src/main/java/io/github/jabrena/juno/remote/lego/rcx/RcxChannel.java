package io.github.jabrena.juno.remote.lego.rcx;

/** A Juno firmware channel of an RCX brick, or the original unchannelized broadcast. */
public enum RcxChannel {
    /** The first Juno firmware channel. */
    CHANNEL_1(1),
    /** The second Juno firmware channel. */
    CHANNEL_2(2),
    /** The third Juno firmware channel. */
    CHANNEL_3(3),
    /** The fourth Juno firmware channel. */
    CHANNEL_4(4),
    /** The fifth Juno firmware channel. */
    CHANNEL_5(5),
    /** All bricks listening to the original unchannelized RCX protocol. */
    BROADCAST(0);

    private final int value;

    RcxChannel(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }
}

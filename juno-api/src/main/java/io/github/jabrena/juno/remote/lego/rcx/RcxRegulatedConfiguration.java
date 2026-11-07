package io.github.jabrena.juno.remote.lego.rcx;

/**
 * The checked configuration of a regulated pair: its session, its configuration flags, and the link that must be able to
 * hear the brick. A pair with no maximum motor power is rejected, since it would never move. Checked before the pair takes
 * the link, so a rejected configuration leaves the link free.
 */
final class RcxRegulatedConfiguration {
    private static final int MAX_SESSION = 255;
    private static final int MAX_BYTE = 0xFF;
    /** The configuration bits that hold the maximum motor power, 1 to 7. */
    private static final int MAX_POWER_MASK = 0xE0;

    private final int session;
    private final int flags;

    RcxRegulatedConfiguration(RcxLink link, int session, int flags) {
        if (session < 1 || session > MAX_SESSION) {
            throw new IllegalArgumentException("session must be between 1 and 255");
        }
        if (flags < 0 || flags > MAX_BYTE) {
            throw new IllegalArgumentException("configuration flags must fit in one byte");
        }
        if ((flags & MAX_POWER_MASK) == 0) {
            throw new IllegalArgumentException(
                    "maximum motor power must be 1 to 7: pass RcxRegulatedPair.maxPower(level) in the flags");
        }
        if (!link.canReceive()) {
            throw new IllegalArgumentException("regulated commands need an infrared receiver to hear the brick's replies");
        }
        this.session = session;
        this.flags = flags;
    }

    int session() {
        return session;
    }

    int flags() {
        return flags;
    }
}

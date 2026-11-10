package io.github.jabrena.juno.remote.lego.rcx;

/**
 * Diagnostics read from the brick for a regulated pair, for tuning and floor tests: the control-cycle counter, which
 * gives the brick's cycle rate, and how the wheel sync drives each side. Reads change no state of the pair, and the drive
 * values are kept in fields, so polling them allocates nothing. Get it from {@link RcxRegulatedPair#diagnostics()}.
 */
public final class RcxRegulatedDiagnostics {
    private final RcxRegulatedProtocol protocol;
    private final int session;

    RcxRegulatedDiagnostics(RcxRegulatedProtocol protocol, int session) {
        this.protocol = protocol;
        this.session = session;
    }

    /**
     * The brick's count of regulator control cycles since it booted, 0 to 65535 and wrapping, or {@code -1} when it did
     * not answer. Two reads a known time apart give the cycle rate, which the firmware's time base must match.
     */
    public int controlCycles() {
        int cycles = protocol.cycles(session);
        return cycles == RcxRegulatedProtocol.NO_REPLY ? -1 : cycles;
    }

    /**
     * Reads each side's motor power this cycle (0 while it is braked) and the cycles of the current motion each was held
     * braked to let the other catch up. Returns whether the brick answered; the values are then in
     * {@link #leftPower()} and the like.
     */
    public boolean refreshDrive() {
        return protocol.drive(session);
    }

    /** The left side's power at the last {@link #refreshDrive()}, 0 while braked. */
    public int leftPower() {
        return protocol.driveLeftPower;
    }

    /** The right side's power at the last {@link #refreshDrive()}, 0 while braked. */
    public int rightPower() {
        return protocol.driveRightPower;
    }

    /** Control cycles the left side was held braked in the current motion, at the last {@link #refreshDrive()}. */
    public int leftHeldCycles() {
        return protocol.driveLeftHeld;
    }

    /** Control cycles the right side was held braked in the current motion, at the last {@link #refreshDrive()}. */
    public int rightHeldCycles() {
        return protocol.driveRightHeld;
    }
}

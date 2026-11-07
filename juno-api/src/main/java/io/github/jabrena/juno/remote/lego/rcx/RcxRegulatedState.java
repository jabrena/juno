package io.github.jabrena.juno.remote.lego.rcx;

/**
 * The state of a regulated RCX pair, as the firmware reports it, plus two states the host itself determines:
 * {@link #EXTERNAL_OVERRIDE} when a manual output command took the outputs back, and {@link #LINK_UNKNOWN} when the
 * outcome of the last command could not be resolved over the infrared link.
 */
public enum RcxRegulatedState {
    NOT_ARMED,
    READY,
    RUNNING,
    COMPLETE,
    STOPPED,
    STALLED,
    TIMED_OUT,
    FAULT,
    EXTERNAL_OVERRIDE,
    LINK_UNKNOWN;

    private static final RcxRegulatedState[] FIRMWARE_STATES = {
        NOT_ARMED, READY, RUNNING, COMPLETE, STOPPED, STALLED, TIMED_OUT, FAULT
    };

    /** Maps a firmware state code ({@code 0..7}) to its state; any other code is {@link #FAULT}. */
    static RcxRegulatedState fromFirmware(int code) {
        return code >= 0 && code < FIRMWARE_STATES.length ? FIRMWARE_STATES[code] : FAULT;
    }

    /** Whether a new motion may start from this state: the firmware accepts a start when ready or after a finished one. */
    boolean canStart() {
        return this == READY || this == COMPLETE || this == STOPPED;
    }
}

package io.github.jabrena.juno.remote.lego.rcx;

/** A lamp connected to one of an RCX brick's motor outputs for tone activity indication. */
public final class RcxLight {
    /** Motor output A. */
    public static final int PORT_A = 0;
    /** Motor output B. */
    public static final int PORT_B = 1;
    /** Motor output C. */
    public static final int PORT_C = 2;

    private final int port;

    /** Creates a light connected to port A, B or C. */
    public RcxLight(int port) {
        if (port < PORT_A || port > PORT_C) {
            throw new IllegalArgumentException("RCX light port must be A (0), B (1) or C (2)");
        }
        this.port = port;
    }

    /** Returns the motor output (port) used as the tone indicator. */
    public int port() {
        return port;
    }
}

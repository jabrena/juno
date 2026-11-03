package io.github.jabrena.juno.remote.lego.pf;

/** An output of a Power Functions receiver: the red port, also called A, or the blue port, also called B. */
public enum PFPort {
    /** The red output, also called A. */
    RED(0),
    /** The blue output, also called B. */
    BLUE(1);

    private final int value;

    PFPort(int value) {
        this.value = value;
    }

    /** The port's value in the protocol's output field. */
    int value() {
        return value;
    }
}

package io.github.jabrena.juno.remote.lego.poweredup;

/**
 * An external port of a Powered Up hub, printed on the hub as A to D: it takes a motor or an external sensor. A City
 * Hub has ports A and B; a Technic Hub and a Move Hub have A to D. Select a port of the connected hub, and the hub's
 * own built-in sensors are a {@link BuiltInSensor} instead.
 */
public enum PoweredUpPort {
    /** External port A, the first motor or sensor port. */
    A(0),
    /** External port B. */
    B(1),
    /** External port C (Technic and Move hubs). */
    C(2),
    /** External port D (Technic and Move hubs). */
    D(3);

    private final int value;

    PoweredUpPort(int value) {
        this.value = value;
    }

    /** The port number the LEGO Wireless Protocol uses for this port. */
    int value() {
        return value;
    }
}

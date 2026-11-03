package io.github.jabrena.juno.remote.lego.poweredup;

/** A colour of a Powered Up hub's status LED, one of the eleven fixed colours the hub's LED protocol offers. */
public enum HubColor {
    /** The LED is off. */
    OFF(0),
    /** Pink. */
    PINK(1),
    /** Purple. */
    PURPLE(2),
    /** Blue. */
    BLUE(3),
    /** Light blue. */
    LIGHT_BLUE(4),
    /** Cyan. */
    CYAN(5),
    /** Green. */
    GREEN(6),
    /** Yellow. */
    YELLOW(7),
    /** Orange. */
    ORANGE(8),
    /** Red. */
    RED(9),
    /** White. */
    WHITE(10);

    private final int value;

    HubColor(int value) {
        this.value = value;
    }

    /** The colour index the LED command carries. */
    int value() {
        return value;
    }
}

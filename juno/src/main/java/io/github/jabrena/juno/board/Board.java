package io.github.jabrena.juno.board;

import io.github.jabrena.juno.CompileException;

/**
 * A Juno compilation target, resolved from the entry-point class's {@code @Board} annotation (see
 * {@code io.github.jabrena.juno.annotations.Board}). {@link #DEFAULT} is used when the entry point
 * carries no {@code @Board} annotation at all, preserving Juno's original UNO-R4-WiFi-shaped behavior.
 */
public enum Board {
    UNO_R4_WIFI("io/github/jabrena/juno/annotations/ArduinoUnoR4WiFi", "UNO R4 WiFi",
            "arduino:renesas_uno:unor4wifi", true, true, false),
    UNO_Q("io/github/jabrena/juno/annotations/ArduinoUnoQ", "UNO Q", "arduino:zephyr:unoq", false, false, true);

    public static final Board DEFAULT = UNO_R4_WIFI;

    private final String apiClassName;
    private final String displayName;
    private final String fqbn;
    private final boolean hasLedMatrix;
    private final boolean hasWifi;
    private final boolean zephyrCore;

    Board(String apiClassName, String displayName, String fqbn, boolean hasLedMatrix, boolean hasWifi,
          boolean zephyrCore) {
        this.apiClassName = apiClassName;
        this.displayName = displayName;
        this.fqbn = fqbn;
        this.hasLedMatrix = hasLedMatrix;
        this.hasWifi = hasWifi;
        this.zephyrCore = zephyrCore;
    }

    /** {@code apiClassName} is a JVM-internal name, e.g. {@code io/github/jabrena/juno/annotations/ArduinoUnoR4WiFi}. */
    public static Board fromApiClassName(String apiClassName) {
        for (Board board : values()) {
            if (board.apiClassName.equals(apiClassName)) {
                return board;
            }
        }
        throw new CompileException("Unsupported @Board target: " + apiClassName.replace('/', '.')
                + "; supported boards are ArduinoUnoR4WiFi and ArduinoUnoQ");
    }

    public String displayName() {
        return displayName;
    }

    public String fqbn() {
        return fqbn;
    }

    public boolean hasLedMatrix() {
        return hasLedMatrix;
    }

    public boolean hasWifi() {
        return hasWifi;
    }

    /**
     * Whether the board's Arduino core is built on Zephyr (the UNO Q), which provides its own
     * {@code yield()} and inlines {@code delay()}/{@code delayMicroseconds()}, so generated code
     * must reach those through shim functions rather than branching to them directly.
     */
    public boolean zephyrCore() {
        return zephyrCore;
    }
}

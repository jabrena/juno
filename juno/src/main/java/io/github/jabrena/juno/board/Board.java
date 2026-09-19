package io.github.jabrena.juno.board;

import io.github.jabrena.juno.CompileException;

/**
 * A Juno compilation target, resolved from the entry-point class's {@code @Board} annotation (see
 * {@code io.github.jabrena.juno.api.Board}). {@link #DEFAULT} is used when the entry point carries no
 * {@code @Board} annotation at all, preserving Juno's original UNO-R4-WiFi-shaped behavior.
 */
public enum Board {
    UNO_R4_WIFI("io/github/jabrena/juno/api/ArduinoUnoR4WiFi", "UNO R4 WiFi", "arduino:renesas_uno:unor4wifi", true),
    UNO_R4_MINIMA("io/github/jabrena/juno/api/ArduinoUnoR4Minima", "UNO R4 Minima", "arduino:renesas_uno:minima", false);

    public static final Board DEFAULT = UNO_R4_WIFI;

    private final String apiClassName;
    private final String displayName;
    private final String fqbn;
    private final boolean hasLedMatrix;

    Board(String apiClassName, String displayName, String fqbn, boolean hasLedMatrix) {
        this.apiClassName = apiClassName;
        this.displayName = displayName;
        this.fqbn = fqbn;
        this.hasLedMatrix = hasLedMatrix;
    }

    /** {@code apiClassName} is a JVM-internal name, e.g. {@code io/github/jabrena/juno/api/ArduinoUnoR4WiFi}. */
    public static Board fromApiClassName(String apiClassName) {
        for (Board board : values()) {
            if (board.apiClassName.equals(apiClassName)) {
                return board;
            }
        }
        throw new CompileException("Unsupported @Board target: " + apiClassName.replace('/', '.')
                + "; supported boards are ArduinoUnoR4WiFi and ArduinoUnoR4Minima");
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
}

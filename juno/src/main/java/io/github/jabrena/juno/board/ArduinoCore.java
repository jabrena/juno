package io.github.jabrena.juno.board;

/**
 * The Arduino core a {@link Board} is built with. The core, not the board, decides how generated
 * code reaches the platform runtime (which {@code delay()}/{@code yield()} symbols are callable,
 * which bundled libraries exist), so boards sharing a core share that behavior.
 */
public enum ArduinoCore {
    /** {@code arduino:renesas_uno}: the UNO R4's classic, non-RTOS core. */
    RENESAS_UNO("Renesas"),
    /**
     * {@code arduino:zephyr}: provides its own {@code yield()} and inlines {@code delay()}/
     * {@code delayMicroseconds()}, so generated code must reach those through shim functions.
     */
    ZEPHYR("Zephyr");

    private final String displayName;

    ArduinoCore(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

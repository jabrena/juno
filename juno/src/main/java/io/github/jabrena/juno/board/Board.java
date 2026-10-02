package io.github.jabrena.juno.board;

import io.github.jabrena.juno.CompileException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Juno compilation target, resolved from the entry-point class's {@code @Board} annotation (see
 * {@code io.github.jabrena.juno.annotations.Board}). {@link #DEFAULT} is used when the entry point
 * carries no {@code @Board} annotation at all, preserving Juno's original UNO-R4-WiFi-shaped behavior.
 * {@code @Board} may declare more than one board; {@link #id()}/{@link #fromId} is how a build picks
 * one of them explicitly (the standalone CLI's {@code --board} option, the Maven plugin's
 * {@code -Djuno.board} property).
 */
public enum Board {
    UNO_R4_WIFI("io/github/jabrena/juno/annotations/ArduinoUnoR4WiFi", "arduino-uno-r4-wifi", "UNO R4 WiFi",
            "arduino:renesas_uno:unor4wifi", ArduinoCore.RENESAS_UNO,
            EnumSet.of(Capability.LED_MATRIX, Capability.WIFI, Capability.HTTPS_CLIENT,
                    Capability.WIFI_S3_NETWORKING, Capability.BLUETOOTH_LE, Capability.WATCHDOG)),
    UNO_Q("io/github/jabrena/juno/annotations/ArduinoUnoQ", "arduino-uno-q", "UNO Q", "arduino:zephyr:unoq",
            ArduinoCore.ZEPHYR, EnumSet.of(Capability.WIFI, Capability.HTTPS_CLIENT));

    public static final Board DEFAULT = UNO_R4_WIFI;

    private final String apiClassName;
    private final String id;
    private final String displayName;
    private final String fqbn;
    private final ArduinoCore core;
    private final Set<Capability> capabilities;

    Board(String apiClassName, String id, String displayName, String fqbn, ArduinoCore core,
          Set<Capability> capabilities) {
        this.apiClassName = apiClassName;
        this.id = id;
        this.displayName = displayName;
        this.fqbn = fqbn;
        this.core = core;
        this.capabilities = Set.copyOf(capabilities);
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

    /**
     * Resolves the user-facing id passed to {@code --board}/{@code -Djuno.board} (e.g.
     * {@code "arduino-uno-q"}), case-insensitively.
     */
    public static Board fromId(String id) {
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (Board board : values()) {
            if (board.id.equals(normalized)) {
                return board;
            }
        }
        throw new CompileException("Unknown board '" + id + "'; supported boards are "
                + Arrays.stream(values()).map(Board::id).collect(Collectors.joining(", ")));
    }

    /** The user-facing id passed to {@code --board}/{@code -Djuno.board}, e.g. {@code "arduino-uno-q"}. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String fqbn() {
        return fqbn;
    }

    /** The Arduino core this board's sketches are built with, which decides how generated code calls into it. */
    public ArduinoCore core() {
        return core;
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    /** The {@code @Board} annotation argument selecting this board, e.g. {@code ArduinoUnoR4WiFi.class}. */
    public String annotationArgument() {
        return apiClassName.substring(apiClassName.lastIndexOf('/') + 1) + ".class";
    }
}

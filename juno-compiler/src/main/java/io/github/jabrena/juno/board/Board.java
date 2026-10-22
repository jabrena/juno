package io.github.jabrena.juno.board;

import io.github.jabrena.juno.CompileException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Juno compilation target, resolved from the entry-point class's {@code @Board} annotation (see
 * {@code io.github.jabrena.juno.annotations.Board}). {@code @Board} may declare more than one board;
 * {@link #id()}/{@link #fromId} is how a build picks
 * one of them explicitly (the standalone CLI's {@code --board} option, the Maven plugin's
 * {@code -Djuno.board} property).
 */
public enum Board {
    /**
     * The classic ATmega328P UNO: basic GPIO, serial and shield APIs within its 2 KB of SRAM, and Wi-Fi, UDP, TCP and
     * plain HTTP through the retired Arduino WiFi Shield's legacy {@code WiFi} library (no access point or HTTPS), reached
     * through {@code ArduinoWifiShield.getWifi()}: the board has no onboard radio, so {@code Arduino.getWifi()} is rejected.
     */
    UNO_R3("io/github/jabrena/juno/annotations/ArduinoUnoR3", "arduino-uno-r3", "UNO R3", "arduino:avr:uno",
            ArduinoCore.CLASSIC_AVR, UnoGeneration.R3,
            EnumSet.of(Capability.WIFI, Capability.HTTP_CLIENT, Capability.WATCHDOG)),
    UNO_R4_WIFI("io/github/jabrena/juno/annotations/ArduinoUnoR4WiFi", "arduino-uno-r4-wifi", "UNO R4 WiFi",
            "arduino:renesas_uno:unor4wifi", ArduinoCore.RENESAS_UNO, UnoGeneration.R4,
            EnumSet.of(Capability.SECURE_ENTROPY, Capability.LED_MATRIX, Capability.WIFI, Capability.ONBOARD_WIFI,
                    Capability.WIFI_ACCESS_POINT, Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT,
                    Capability.TLS_EMAIL,
                    Capability.HTTP_SERVER, Capability.SMTP_STARTTLS, Capability.BLUETOOTH_LE, Capability.WATCHDOG,
                    Capability.USB_HID, Capability.STRUCTURED_CONCURRENCY, Capability.BIG_NUMBERS, Capability.DOUBLE_MATH)),
    UNO_Q("io/github/jabrena/juno/annotations/ArduinoUnoQ", "arduino-uno-q", "UNO Q", "arduino:zephyr:unoq",
            ArduinoCore.ZEPHYR, UnoGeneration.Q, EnumSet.of(Capability.SECURE_ENTROPY, Capability.LED_MATRIX, Capability.WIFI,
                    Capability.ONBOARD_WIFI, Capability.WIFI_ACCESS_POINT, Capability.HTTPS_CLIENT,
                    Capability.TLS_EMAIL,
                    Capability.HTTP_SERVER, Capability.BLUETOOTH_LE, Capability.STRUCTURED_CONCURRENCY, Capability.BIG_NUMBERS,
                    Capability.DOUBLE_MATH)),
    /**
     * The ATmega4809 board: Wi-Fi, UDP, HTTP, HTTPS and TLS email through its NINA-W102 module ({@code WiFiNINA}), and the
     * GPIO, SPI and analog pins shields such as the SD card, TFT touch and LCD keypad shields use.
     */
    UNO_R3_WIFI_REV2("io/github/jabrena/juno/annotations/ArduinoUnoR3WiFiRev2", "arduino-uno-r3-wifi-rev2", "UNO WiFi Rev2",
            "arduino:megaavr:uno2018", ArduinoCore.MEGAAVR, UnoGeneration.R3,
            EnumSet.of(Capability.SECURE_ENTROPY, Capability.WIFI, Capability.ONBOARD_WIFI, Capability.WIFI_ACCESS_POINT,
                    Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT, Capability.TLS_EMAIL,
                    Capability.STRUCTURED_CONCURRENCY, Capability.BIG_NUMBERS, Capability.WATCHDOG)),
    /**
     * The ESP32-S3 Nano: GPIO, delays, USB serial, SPI SD cards, the onboard Wi-Fi of its NORA-W106 module (station
     * mode only, with UDP and SNTP), plain HTTP and HTTPS requests (checked against Mozilla's root certificates), an
     * HTTP server on station Wi-Fi, implicit-TLS email (SMTP and POP3), the double-precision libm its newlib toolchain
     * ships, and structured concurrency over FreeRTOS tasks; the other optional capabilities are not wired to its core
     * yet.
     */
    NANO_ESP32("io/github/jabrena/juno/annotations/ArduinoNanoEsp32", "arduino-nano-esp32", "Nano ESP32",
            "arduino:esp32:nano_nora", ArduinoCore.ESP32, UnoGeneration.NANO,
            EnumSet.of(Capability.WIFI, Capability.ONBOARD_WIFI, Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT,
                    Capability.HTTP_SERVER, Capability.TLS_EMAIL, Capability.DOUBLE_MATH,
                    Capability.STRUCTURED_CONCURRENCY, Capability.WATCHDOG));

    private final String apiClassName;
    private final String id;
    private final String displayName;
    private final String fqbn;
    private final ArduinoCore core;
    private final UnoGeneration generation;
    @SuppressWarnings("ImmutableEnumChecker") // Set.copyOf in the constructor makes it unmodifiable
    private final Set<Capability> capabilities;

    Board(String apiClassName, String id, String displayName, String fqbn, ArduinoCore core,
          UnoGeneration generation, Set<Capability> capabilities) {
        this.apiClassName = apiClassName;
        this.id = id;
        this.displayName = displayName;
        this.fqbn = fqbn;
        this.core = core;
        this.generation = generation;
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
                + "; supported boards are "
                + Arrays.stream(values()).map(Board::annotationArgument)
                        .map(argument -> argument.substring(0, argument.length() - ".class".length()))
                        .collect(Collectors.joining(", ")));
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

    /** The UNO generation {@code BoardModel.isR3()}/{@code isR4()}/{@code isQ()} report. */
    public UnoGeneration generation() {
        return generation;
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    /** The {@code @Board} annotation argument selecting this board, e.g. {@code ArduinoUnoR4WiFi.class}. */
    public String annotationArgument() {
        return apiClassName.substring(apiClassName.lastIndexOf('/') + 1) + ".class";
    }
}

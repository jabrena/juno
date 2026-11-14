package io.github.jabrena.juno.board;

/**
 * The Arduino core a {@link Board} is built with. The core, not the board, decides how generated
 * code reaches the platform runtime (which {@code delay()}/{@code yield()} symbols are callable,
 * which bundled libraries exist) and how many carriers threads run on. Memory budgets are not a core property:
 * each {@link Board} declares its own {@link BoardMemoryProfile}.
 */
public enum ArduinoCore {
    /** {@code arduino:avr}: the classic UNO R3's ATmega328P core, with 2 KB of RAM. */
    CLASSIC_AVR("AVR", Architecture.AVR),
    /** {@code arduino:renesas_uno}: the UNO R4's classic, non-RTOS core. */
    RENESAS_UNO("Renesas", Architecture.THUMB2),
    /**
     * {@code arduino:zephyr}: provides its own {@code yield()} and inlines {@code delay()}/
     * {@code delayMicroseconds()}, so generated code must reach those through shim functions.
     */
    ZEPHYR("Zephyr", Architecture.THUMB2),
    /** {@code arduino:megaavr}: the UNO WiFi Rev2's ATmega4809 core, with no RTOS and 6 KB of RAM. */
    MEGAAVR("megaAVR", Architecture.AVR),
    /**
     * {@code arduino:esp32}: the Nano ESP32's FreeRTOS-based core, whose {@code pinMode}/{@code digitalWrite} are
     * macros mapping the board's printed pin numbers to ESP32-S3 GPIO numbers.
     */
    ESP32("ESP32", Architecture.XTENSA),
    /** {@code esp32:esp32}: the AI Thinker ESP32-CAM's original ESP32 core and GPIO numbering. */
    ESP32_CAM("ESP32-CAM", Architecture.XTENSA),
    /**
     * {@code m5stack:esp32}: the M5Stack Cardputer's ESP32-S3 core, built on ESP-IDF 5 rather than the Arduino-ESP32
     * 2.x line the Nano uses. Its runtime matches the Nano's, except that the task watchdog takes a configuration
     * structure instead of a timeout in seconds.
     */
    M5STACK_ESP32("M5Stack ESP32", Architecture.XTENSA),
    /**
     * {@code rp2040:rp2040}: Earle Philhower's arduino-pico core, built for the RP2350's Cortex-M33 on the Raspberry Pi
     * Pico 2 W. Published on its own Boards Manager index, not Arduino's.
     */
    RP2350("RP2350", Architecture.THUMB2),
    /** {@code arduino:mbed_portenta}: Arduino's Mbed-based Portenta H7 M7 core. */
    MBED_PORTENTA("Mbed Portenta", Architecture.THUMB2);

    private final String displayName;
    private final Architecture architecture;

    ArduinoCore(String displayName, Architecture architecture) {
        this.displayName = displayName;
        this.architecture = architecture;
    }

    /** The instruction set this core's boards run, which selects the code generator. */
    public Architecture architecture() {
        return architecture;
    }

    public String displayName() {
        return displayName;
    }

    /** Number of physical carriers available to a runtime built for this core. */
    public int carrierCount() {
        return switch (this) {
            case ESP32, ESP32_CAM, M5STACK_ESP32, RP2350 -> 2;
            default -> 1;
        };
    }
}

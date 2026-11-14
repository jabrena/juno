package io.github.jabrena.juno.board;

import io.github.jabrena.juno.CompileException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
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
     * The classic ATmega328P UNO: basic GPIO, serial and shield APIs within its 2 KB of SRAM, and bounded {@code java.math}
     * support within its 1 KiB arena. It has no onboard radio and no network support, so {@code MCU.getWifi()},
     * {@code Udp}, {@code Tcp}, plain-HTTP {@code HttpClient} and the SNTP clock are rejected.
     */
    UNO_R3("io/github/jabrena/juno/annotations/ArduinoUnoR3Board", "arduino-uno-r3", "UNO R3", "arduino:avr:uno",
            ArduinoCore.CLASSIC_AVR, UnoGeneration.R3, new BoardMemoryProfile(2048, 1024, 1024, 512, 512, MemoryAuthority.LINKER, OptionalInt.empty()), EnumSet.of(Capability.CLASS_INHERITANCE, Capability.UUID_RANDOM, Capability.BIG_NUMBERS,
                    Capability.WATCHDOG)),
    UNO_R4_WIFI("io/github/jabrena/juno/annotations/ArduinoUnoR4WiFiBoard", "arduino-uno-r4-wifi", "UNO R4 WiFi",
            "arduino:renesas_uno:unor4wifi", ArduinoCore.RENESAS_UNO, UnoGeneration.R4, new BoardMemoryProfile(32768, 8192, 25736, 2048, 8192, MemoryAuthority.LINKER, OptionalInt.empty()), EnumSet.of(Capability.CLASS_INHERITANCE, Capability.UUID_RANDOM, Capability.SECURE_ENTROPY, Capability.LED_MATRIX, Capability.WIFI, Capability.ONBOARD_WIFI,
                    Capability.WIFI_ACCESS_POINT, Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT,
                    Capability.TLS_EMAIL,
                    Capability.HTTP_SERVER, Capability.SMTP_STARTTLS, Capability.BLUETOOTH_LE, Capability.WATCHDOG,
                    Capability.USB_HID, Capability.STRUCTURED_CONCURRENCY, Capability.BIG_NUMBERS, Capability.DOUBLE_MATH)),
    UNO_Q("io/github/jabrena/juno/annotations/ArduinoUnoQBoard", "arduino-uno-q", "UNO Q", "arduino:zephyr:unoq",
            ArduinoCore.ZEPHYR, UnoGeneration.Q, new BoardMemoryProfile(262144, 8192, 226928, 4096, 16384, MemoryAuthority.LOADER_POOL, OptionalInt.empty()), EnumSet.of(Capability.CLASS_INHERITANCE, Capability.UUID_RANDOM, Capability.SECURE_ENTROPY, Capability.LED_MATRIX, Capability.WIFI,
                    Capability.ONBOARD_WIFI, Capability.WIFI_ACCESS_POINT, Capability.HTTPS_CLIENT,
                    Capability.TLS_EMAIL,
                    Capability.HTTP_SERVER, Capability.HTTPS_SERVER, Capability.BLUETOOTH_LE,
                    Capability.STRUCTURED_CONCURRENCY, Capability.BIG_NUMBERS, Capability.DOUBLE_MATH)),
    /**
     * The ATmega4809 board: Wi-Fi, UDP, HTTP, HTTPS and TLS email through its NINA-W102 module ({@code WiFiNINA}), a
     * plain-HTTP server on its station address, and the GPIO, SPI and analog pins shields such as the SD card, TFT touch
     * and LCD keypad shields use.
     */
    UNO_R3_WIFI_REV2("io/github/jabrena/juno/annotations/ArduinoUnoR3WiFiRev2Board", "arduino-uno-r3-wifi-rev2", "UNO WiFi Rev2",
            "arduino:megaavr:uno2018", ArduinoCore.MEGAAVR, UnoGeneration.R3, new BoardMemoryProfile(6144, 1024, 3072, 512, 1024, MemoryAuthority.LINKER, OptionalInt.empty()), EnumSet.of(Capability.CLASS_INHERITANCE, Capability.UUID_RANDOM, Capability.SECURE_ENTROPY, Capability.WIFI, Capability.ONBOARD_WIFI, Capability.WIFI_ACCESS_POINT,
                    Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT, Capability.TLS_EMAIL, Capability.HTTP_SERVER,
                    Capability.STRUCTURED_CONCURRENCY, Capability.BIG_NUMBERS, Capability.WATCHDOG, Capability.ONBOARD_IMU)),
    /**
     * The ESP32-S3 Nano: GPIO, delays, USB serial, SPI SD cards, the onboard Wi-Fi of its NORA-W106 module (station and
     * access-point modes, with UDP and SNTP), plain HTTP and HTTPS requests (checked against Mozilla's root
     * certificates), an HTTP and HTTPS server on station Wi-Fi, email over implicit TLS and STARTTLS (SMTP and
     * POP3), Bluetooth LE for {@code RemotePoweredUpHub}, the hardware random-number generator behind
     * {@code UUID.randomUUID()}, {@code java.math}, the double-precision libm its newlib toolchain ships,
     * structured concurrency over FreeRTOS tasks, and a USB HID mouse through the core's TinyUSB stack, sharing the
     * USB port with the serial console; the other optional capabilities are not wired to its core yet.
     */
    NANO_ESP32("io/github/jabrena/juno/annotations/ArduinoNanoEsp32Board", "arduino-nano-esp32", "Nano ESP32",
            "arduino:esp32:nano_nora", ArduinoCore.ESP32, NanoFamily.NANO_ESP32, new BoardMemoryProfile(327680, 8192, 296840, 8192, 32768, MemoryAuthority.RUNTIME_HEAP, OptionalInt.of(8 * 1024 * 1024)), EnumSet.of(Capability.CLASS_INHERITANCE, Capability.UUID_RANDOM, Capability.SECURE_ENTROPY, Capability.WIFI, Capability.WIFI_ACCESS_POINT,
                    Capability.ONBOARD_WIFI, Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT, Capability.HTTP_SERVER,
                    Capability.HTTPS_SERVER, Capability.TLS_EMAIL, Capability.SMTP_STARTTLS, Capability.BLUETOOTH_LE,
                    Capability.BIG_NUMBERS, Capability.DOUBLE_MATH, Capability.STRUCTURED_CONCURRENCY,
                    Capability.WATCHDOG, Capability.USB_HID, Capability.PSRAM)),
    /**
     * AI Thinker ESP32-CAM: the original ESP32 with the OV2640 camera, GPIO33 status LED and GPIO4 flash LED. The
     * Juno exposes its GPIO, serial, onboard Wi-Fi (station and access-point modes), Bluetooth LE for
     * {@code RemotePoweredUpHub}, microSD socket and OV2640 camera as JPEG frames through {@code MCU.getCamera()}.
     * The ESP32 hardware TLS, task watchdog, random-number generator and double-precision libm are also available.
     */
    AI_THINKER_ESP32_CAM("io/github/jabrena/juno/annotations/AiThinkerEsp32CamBoard", "ai-thinker-esp32-cam",
            "AI Thinker ESP32-CAM", "esp32:esp32:esp32cam", ArduinoCore.ESP32_CAM, Esp32Family.AI_THINKER_ESP32_CAM, new BoardMemoryProfile(327680, 8192, 305216, 8192, 32768, MemoryAuthority.RUNTIME_HEAP, OptionalInt.of(4 * 1024 * 1024)), EnumSet.of(Capability.CLASS_INHERITANCE, Capability.BIG_NUMBERS, Capability.WIFI, Capability.ONBOARD_WIFI,
                    Capability.BLUETOOTH_LE, Capability.CAMERA, Capability.PSRAM, Capability.HTTP_SERVER, Capability.HTTP_STREAM,
                    Capability.STRUCTURED_CONCURRENCY, Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT,
                    Capability.WIFI_ACCESS_POINT, Capability.HTTPS_SERVER, Capability.TLS_EMAIL, Capability.SMTP_STARTTLS,
                    Capability.WATCHDOG, Capability.UUID_RANDOM, Capability.SECURE_ENTROPY, Capability.DOUBLE_MATH,
                    Capability.ONBOARD_SD_CARD), OptionalInt.of(13)),
    /**
     * M5Stack Cardputer: ESP32-S3 support for USB serial and HID, GPIO, SD storage, onboard Wi-Fi, plain HTTP and
     * HTTPS clients, an HTTP and HTTPS server, arbitrary-precision numbers, color display, keyboard, speaker, and
     * battery, and the ESP32 task watchdog behind {@code @Watchdog}.
     */
    M5_CARDPUTER("io/github/jabrena/juno/annotations/M5StackCardputerBoard", "m5stack-cardputer", "M5Stack Cardputer",
            "m5stack:esp32:m5stack_cardputer:USBMode=default,CDCOnBoot=cdc,UploadMode=cdc", ArduinoCore.M5STACK_ESP32,
            M5StackFamily.CARDPUTER_ADV, new BoardMemoryProfile(327680, 8192, 273408, 8192, 32768, MemoryAuthority.RUNTIME_HEAP, OptionalInt.empty()), EnumSet.of(Capability.CLASS_INHERITANCE, Capability.UUID_RANDOM, Capability.SECURE_ENTROPY, Capability.WIFI, Capability.ONBOARD_WIFI, Capability.WIFI_ACCESS_POINT,
                    Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT, Capability.HTTP_SERVER, Capability.HTTPS_SERVER,
                    Capability.BIG_NUMBERS, Capability.M5_DISPLAY, Capability.M5_KEYBOARD, Capability.M5_AUDIO,
                    Capability.M5_BATTERY, Capability.WATCHDOG, Capability.STRUCTURED_CONCURRENCY, Capability.DOUBLE_MATH, Capability.BLUETOOTH_LE,
                    Capability.TLS_EMAIL, Capability.USB_HID, Capability.ONBOARD_SD_CARD), OptionalInt.of(12)),
    /**
     * Raspberry Pi Pico 2 W: the RP2350's Cortex-M33 on Earle Philhower's arduino-pico core, reached through
     * {@code MCU} on the Pico 2 W. It runs GPIO, delays, USB serial, {@code java.math} and the onboard CYW43439 Wi-Fi
     * (station and access point, with UDP, TCP sockets and SNTP); its built-in LED sits on that radio chip, which the
     * core drives as pin 64. HTTPS, TLS email and Bluetooth LE use the arduino-pico libraries; the board also exposes
     * the hardware watchdog, the shared Thumb-2 thread port, double-precision math, USB HID and the Grove Shield's
     * UART/SPI socket map.
     */
    PICO_2_W("io/github/jabrena/juno/annotations/RaspberryPico2WifiBoard", "raspberry-pi-pico-2-w",
            "Raspberry Pi Pico 2 W", "rp2040:rp2040:rpipico2w:ipbtstack=ipv4btcble", ArduinoCore.RP2350, PicoFamily.PICO_2, new BoardMemoryProfile(524288, 8192, 436176, 4096, 16384, MemoryAuthority.RUNTIME_HEAP, OptionalInt.empty()), EnumSet.of(Capability.CLASS_INHERITANCE, Capability.BIG_NUMBERS, Capability.WIFI, Capability.ONBOARD_WIFI,
                    Capability.WIFI_ACCESS_POINT, Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT, Capability.TLS_EMAIL,
                    Capability.HTTP_SERVER, Capability.HTTPS_SERVER, Capability.BLUETOOTH_LE, Capability.WATCHDOG,
                    Capability.USB_HID, Capability.STRUCTURED_CONCURRENCY, Capability.DOUBLE_MATH)),
    /** Arduino Portenta H7's Cortex-M7 running the M7 image through the Arduino Mbed core. */
    PORTENTA_H7("io/github/jabrena/juno/annotations/ArduinoPortentaH7Board", "arduino-portenta-h7", "Portenta H7",
            "arduino:mbed_portenta:envie_m7", ArduinoCore.MBED_PORTENTA, MbedFamily.PORTENTA_H7,
            new BoardMemoryProfile(1024 * 1024, 65536, 917504, 8192, 32768, MemoryAuthority.RUNTIME_HEAP,
                    OptionalInt.empty()), EnumSet.of(Capability.CLASS_INHERITANCE, Capability.UUID_RANDOM,
                    Capability.SECURE_ENTROPY, Capability.WIFI, Capability.ONBOARD_WIFI,
                    Capability.WIFI_ACCESS_POINT, Capability.HTTP_CLIENT, Capability.HTTPS_CLIENT,
                    Capability.HTTP_SERVER, Capability.HTTPS_SERVER, Capability.TLS_EMAIL, Capability.SMTP_STARTTLS,
                    Capability.BLUETOOTH_LE, Capability.USB_HID, Capability.WATCHDOG, Capability.BIG_NUMBERS,
                    Capability.DOUBLE_MATH, Capability.STRUCTURED_CONCURRENCY));

    private final String apiClassName;
    private final String id;
    private final String displayName;
    private final String fqbn;
    private final ArduinoCore core;
    @SuppressWarnings("ImmutableEnumChecker") // Every BoardFamily implementation is an enum.
    private final BoardFamily family;
    @SuppressWarnings("ImmutableEnumChecker") // A record of primitives, an enum and an OptionalInt: immutable.
    private final BoardMemoryProfile memoryProfile;
    @SuppressWarnings("ImmutableEnumChecker") // Set.copyOf in the constructor makes it unmodifiable
    private final Set<Capability> capabilities;
    private final OptionalInt onboardSdChipSelect;

    Board(String apiClassName, String id, String displayName, String fqbn, ArduinoCore core,
          BoardFamily family, BoardMemoryProfile memoryProfile, Set<Capability> capabilities) {
        this(apiClassName, id, displayName, fqbn, core, family, memoryProfile, capabilities, OptionalInt.empty());
    }

    Board(String apiClassName, String id, String displayName, String fqbn, ArduinoCore core,
          BoardFamily family, BoardMemoryProfile memoryProfile, Set<Capability> capabilities,
          OptionalInt onboardSdChipSelect) {
        this.apiClassName = apiClassName;
        this.id = id;
        this.displayName = displayName;
        this.fqbn = fqbn;
        this.core = core;
        this.family = family;
        this.memoryProfile = memoryProfile;
        this.capabilities = Set.copyOf(capabilities);
        this.onboardSdChipSelect = onboardSdChipSelect;
    }

    /** {@code apiClassName} is a JVM-internal name, e.g. {@code io/github/jabrena/juno/annotations/ArduinoUnoR4WiFiBoard}. */
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

    /** The memory budget this board's arena and stacks are checked against. */
    public BoardMemoryProfile memoryProfile() {
        return memoryProfile;
    }

    /** The physical family this board belongs to. */
    public BoardFamily family() {
        return family;
    }

    /**
     * The UNO generation reported by {@code BoardModel.isR3()}/{@code isR4()}/{@code isQ()}, or an empty value for
     * boards outside the UNO family.
     */
    public Optional<UnoGeneration> unoGeneration() {
        return family instanceof UnoGeneration generation ? Optional.of(generation) : Optional.empty();
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    /** The GPIO the onboard microSD socket's chip select is wired to, or empty for a board without that socket. */
    public OptionalInt onboardSdChipSelect() {
        return onboardSdChipSelect;
    }

    /** The {@code @Board} annotation argument selecting this board, e.g. {@code ArduinoUnoR4WiFiBoard.class}. */
    public String annotationArgument() {
        return apiClassName.substring(apiClassName.lastIndexOf('/') + 1) + ".class";
    }
}

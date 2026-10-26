package io.github.jabrena.juno.backend.runtime.spi;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.board.ArduinoCore;

/**
 * Everything the generated code needs to know about the {@link ArduinoCore} it links against: the
 * symbols {@code Delay} lowers to and the runtime shim's {@code yield()}/delay glue. The Thumb-2
 * code generator itself is identical for every core of an {@link io.github.jabrena.juno.board.Architecture}; each
 * board-to-core difference lives in exactly one implementation instead of being branched on across the backend.
 *
 * <p>The implementations live in {@code backend.runtime.core}, one per {@link ArduinoCore}, and are obtained through
 * {@code CoreRuntimes.of}. This interface depends on nothing else in the backend, so the shim and the code generators
 * can both use it without depending on each other or on a specific core.
 */
public interface CoreRuntime {

    /** The symbol {@code Delay.millis} branches to, taking the delay in r0. */
    String delayMillisFunction();

    /** The symbol {@code Delay.micros} branches to, taking the delay in r0. */
    String delayMicrosFunction();

    /**
     * The symbol generated code calls for the Arduino GPIO function {@code arduinoFunction} ({@code pinMode},
     * {@code digitalWrite}, {@code digitalRead}, {@code analogRead} or {@code analogWrite}): the function itself,
     * unless the core defines it as a macro the assembly cannot call, in which case its shim wrapper.
     */
    default String gpioFunction(String arduinoFunction) {
        return arduinoFunction;
    }

    /** C++ type accepted by an AVR core's {@code pinMode}; classic AVR cores use byte constants. */
    default String avrPinModeType() {
        return "uint8_t";
    }

    /** C++ type accepted by an AVR core's {@code digitalWrite}; classic AVR cores use byte constants. */
    default String avrPinStatusType() {
        return "uint8_t";
    }

    /** The Arduino digital pin number {@code Gpio.getBuiltinLed()} resolves to on this core's board. */
    int builtinLedPin();

    /** The core's LED matrix class ({@code Arduino_LED_Matrix.h} names it differently on each core). */
    String ledMatrixType();

    /** How many columns the board's LED matrix has; it always has {@link #LED_MATRIX_ROWS} rows. */
    int ledMatrixColumns();

    /** Rows of every supported board's LED matrix. */
    int LED_MATRIX_ROWS = 8;

    /** The 32-bit words one frame of the board's LED matrix occupies (96 pixels on UNO R4, 104 on UNO Q). */
    default int ledMatrixWords() {
        return (ledMatrixColumns() * LED_MATRIX_ROWS + 31) / 32;
    }

    /**
     * Core-provided headers for network access and, when requested, UDP. UNO R4 uses WiFiS3
     * directly; UNO Q delegates networking to Linux through Arduino_RouterBridge.
     */
    String wifiIncludes(boolean udp);

    /** Core-specific implementation of the portable {@code Wifi} intrinsics. */
    String wifiHelpers();

    /**
     * The core's half of {@link io.github.jabrena.juno.hardware.net.http.HttpServer}: defines
     * {@code juno_http_server_begin}, {@code JunoHttpClient}, the {@code juno_http_server_client} the
     * shared request parser reads from, {@code juno_http_server_next_client()} (true once a client is
     * waiting and stored in {@code juno_http_server_client}) and {@code juno_http_server_release()}
     * (called after the response, so the core can forget the finished connection).
     */
    String httpServerTransport();

    /**
     * {@link #httpServerTransport()} for a program that calls {@code HttpServer.beginTls}: the same contract, plus
     * {@code juno_http_server_begin_tls}, with each accepted connection wrapped in a TLS server session. Only cores
     * whose boards declare the {@code HTTPS_SERVER} capability provide it; the linker rejects the call elsewhere first.
     */
    default String httpsServerTransport() {
        throw new IllegalStateException("This core has no TLS server; the linker should have rejected beginTls");
    }

    /** Declaration of the core's single UDP transport instance. */
    String udpDeclaration();

    /** Optional adapter for the generic {@code Tcp} transport's {@code JunoTcpClient}. */
    default String tcpClientHelpers() {
        return "";
    }

    /** Core-specific header required by the HTTPS client implementation. */
    String httpsInclude();

    /** Optional core-specific HTTPS adapter emitted before the HTTP codec. */
    String httpsHelpers();

    /** Safe number of simultaneous HTTP transport leases; excess callers wait cooperatively. */
    default int maxConcurrentHttpRequests() {
        return 2;
    }

    /** Local client declaration used by each generated HTTPS entry point. */
    String httpsClientDeclaration();

    /**
     * The shim's {@code yield()} and delay glue, emitted ahead of {@code juno_panic}. It must provide
     * (or leave to the core) the plain {@code yield} symbol every generated loop backedge calls, and
     * define {@link #delayMillisFunction()}/{@link #delayMicrosFunction()} when those aren't core
     * symbols. May contain the {@code ${JUNO_WATCHDOG_REFRESH}} placeholder, which {@code RuntimeShim}
     * substitutes afterwards.
     */
    String yieldFunction();

    /**
     * The yield glue when a generated program enables {@code @Watchdog}. Most cores use the same glue; cores whose
     * weak Arduino {@code yield()} needs overriding can add that override only to watchdog-enabled sketches.
     */
    default String watchdogYieldFunction() {
        return yieldFunction();
    }

    /** Core-specific hardware-watchdog setup for the requested timeout. */
    WatchdogRuntime watchdog(int timeoutMillis);

    /**
     * The core's {@code ParallelBus} helpers: {@code juno_parallel_bus_begin(const int32_t* pins, int32_t strobe)},
     * {@code juno_parallel_bus_write(int32_t value)} and {@code juno_parallel_bus_repeat16(int32_t value,
     * int32_t count)}, writing whole GPIO ports through the core's own pin table.
     */
    String parallelBusHelpers();

    /**
     * Core-specific SD helper implementation. Most boards use SdFat's default hardware SPI bus; a core whose shield
     * headers do not expose that bus wires its own, without introducing board checks into the backend.
     */
    String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes);

    /**
     * Core-specific {@code juno_memory_flash_used()}/{@code juno_memory_flash_capacity()}: where the program image
     * ends depends on the core's linker layout, and its limit on the board's upload size.
     */
    String flashHelpers();

    /**
     * Board-native, cryptographic random-byte provider for {@code UUID.randomUUID()}. The returned source must
     * fail closed rather than substitute Arduino's pseudorandom generator or GPIO/ADC samples.
     */
    String uuidRandomHelpers();

    /**
     * Whether the core's port runs a program's waiting device calls on a worker thread of its own (see
     * {@code IoRuntime}), so they hold up no other thread even where the wait happens inside a vendor library. Only
     * a core with an RTOS underneath can: the UNO Q's.
     */
    default boolean ioWorker() {
        return false;
    }

    /** The port's I/O worker, when {@link #ioWorker()} is true. */
    default String ioWorkerPort() {
        return "";
    }

    /** Declares the internet clock's own SNTP socket, {@code juno_sntp_udp}, of the same type as {@link #udpDeclaration()}. */
    default String sntpUdpDeclaration() {
        return udpDeclaration().replace("juno_udp", "juno_sntp_udp");
    }

    /** The plain-HTTP client type {@code HttpClient}'s {@code http://} requests connect through. */
    default String httpClientType() {
        return "WiFiClient";
    }

    /** The type of the client {@link #httpsClientDeclaration()} declares as {@code client}. */
    default String httpsClientType() {
        return httpsClientDeclaration().replace(" client;", "").strip();
    }

    /**
     * Whether the core's thread port brings a tick that preempts generated code at any instruction (the Thumb-2
     * cores' {@code juno_preempt_tick}); otherwise the backend emits safepoints on loop backedges and method entries.
     */
    default boolean preemptsAsynchronously() {
        return true;
    }

    /** Core-specific header the thread port needs ({@code ""} when the core brings none). */
    String threadIncludes();

    /**
     * The core's half of the {@code java.lang.Thread} runtime (see {@code ThreadRuntime}): how a thread's stack
     * is created and how control moves between two threads, plus the {@code JUNO_MAX_THREADS} and
     * {@code JUNO_THREAD_STACK_BYTES} constants, sized from {@code config}.
     */
    String threadPort(RuntimeConfig config);

    /**
     * The BLE central names the LEGO Powered UP runtime is written against: {@code ArduinoBLE}'s device, characteristic
     * and scanner object. A core with another BLE library names its own adapter here.
     */
    record BleNames(String device, String characteristic, String central) {
        /** {@code ArduinoBLE}, the library the UNO R4 WiFi and UNO Q use. */
        public static final BleNames ARDUINO_BLE = new BleNames("BLEDevice", "BLECharacteristic", "BLE");
    }

    /** The BLE central names the LEGO runtime uses on this core. */
    default BleNames bleNames() {
        return BleNames.ARDUINO_BLE;
    }

    /** Core-specific headers of the BLE central. */
    default String bleIncludes() {
        return "#include <ArduinoBLE.h>\n";
    }

    /** Core-specific adapter the LEGO runtime calls, emitted before it; empty when the core provides ArduinoBLE itself. */
    default String bleHelpers() {
        return "";
    }

    /** Core-specific headers of the USB HID mouse: the optional {@code Mouse} library by default. */
    default String mouseIncludes() {
        return "#include <Mouse.h>\n";
    }

    /**
     * Core-specific {@code Mouse} object the shared {@code juno_mouse_*} bindings call, emitted before them; empty when
     * the core's {@code Mouse} library provides it.
     */
    default String mouseHelpers() {
        return "";
    }
}

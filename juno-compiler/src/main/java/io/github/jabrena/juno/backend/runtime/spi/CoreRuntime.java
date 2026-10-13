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

    /** C++ type accepted by an AVR core's {@code pinMode}; classic AVR cores use byte constants. */
    default String avrPinModeType() {
        return "uint8_t";
    }

    /** C++ type accepted by an AVR core's {@code digitalWrite}; classic AVR cores use byte constants. */
    default String avrPinStatusType() {
        return "uint8_t";
    }

    /** The Arduino digital pin number {@code Gpio.builtinLed()} resolves to on this core's board. */
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
     * The core's half of {@link io.github.jabrena.juno.api.net.http.HttpServer}: defines
     * {@code juno_http_server_begin}, {@code JunoHttpClient}, the {@code juno_http_server_client} the
     * shared request parser reads from, {@code juno_http_server_next_client()} (true once a client is
     * waiting and stored in {@code juno_http_server_client}) and {@code juno_http_server_release()}
     * (called after the response, so the core can forget the finished connection).
     */
    String httpServerTransport();

    /** Declaration of the core's single UDP transport instance. */
    String udpDeclaration();

    /** Core-specific header required by the HTTPS client implementation. */
    String httpsInclude();

    /** Optional core-specific HTTPS adapter emitted before the HTTP codec. */
    String httpsHelpers();

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

    /** The type of the client {@link #httpsClientDeclaration()} declares as {@code client}. */
    default String httpsClientType() {
        return httpsClientDeclaration().replace(" client;", "").strip();
    }

    /** Core-specific header the thread port needs ({@code ""} when the core brings none). */
    String threadIncludes();

    /**
     * The core's half of the {@code java.lang.Thread} runtime (see {@code ThreadRuntime}): how a thread's stack
     * is created and how control moves between two threads, plus the {@code JUNO_MAX_THREADS} and
     * {@code JUNO_THREAD_STACK_BYTES} constants, sized from {@code config}.
     */
    String threadPort(RuntimeConfig config);
}

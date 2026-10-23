package io.github.jabrena.juno.backend.runtime.spi;

/**
 * An optional part of the generated runtime shim, recorded while lowering the program's IR and
 * consulted by {@link RuntimeShim} so each include and helper block is emitted only when some
 * generated code actually calls into it.
 */
public enum ShimFeature {
    /** {@code LedMatrix}: the board's {@code Arduino_LED_Matrix} library, with the core-bundled header. */
    LED_MATRIX,
    /** Shared one-time initialization for M5Stack Cardputer peripherals. */
    M5_DEVICE,
    /** The M5Stack Cardputer's built-in color display. */
    M5_DISPLAY,
    /** The M5Stack Cardputer's built-in keyboard. */
    M5_KEYBOARD,
    /** The M5Stack Cardputer's built-in speaker. */
    M5_AUDIO,
    /** The M5Stack Cardputer's built-in battery. */
    M5_BATTERY,
    MOUSE,
    SERVO,
    /** {@code PoweredUpHubRemote}: a BLE central for LEGO Powered Up hubs over the optional {@code ArduinoBLE} library. */
    LEGO_POWERED_UP,
    /** {@code ParallelBus}: eight data pins and a strobe, written through the core's GPIO port registers. */
    PARALLEL_BUS,
    /** {@code Infrared}: bit-banged framed bytes over a 38 kHz IR receiver and LED, no library. */
    INFRARED,
    /** {@code I2c}: register access on the primary {@code Wire} bus, with the core-bundled library. */
    I2C,
    WIFI,
    UDP,
    /** Caller-owned, single-connection TCP transport. */
    TCP,
    SD,
    /** {@code java.io.RandomAccessFile} over the SD open-file table; raises JDK exceptions, so it needs {@link #EXCEPTIONS}. */
    RANDOM_ACCESS_FILE,
    LONG,
    FLOAT,
    DOUBLE,
    HTTP,
    HTTPS,
    HTTP_PATH_BUFFER,
    HTTPS_PATH_BUFFER,
    HTTPS_REQUEST_BUFFER,
    HTTP_SERVER,
    SMTP,
    SMTP_TLS,
    POP3,
    RUNTIME_STRINGS,
    STRING_FROM_BYTES,
    STRING_BUILDER,
    MEMORY,
    /** {@code BoardMemory.flashUsedBytes()}/{@code flashCapacityBytes()}, answered by each core's linker layout. */
    FLASH,
    /** {@code SdCard.totalKiB()}/{@code freeKiB()}, on top of {@link #SD}. */
    SD_CAPACITY,
    RANDOM,
    EXCEPTIONS,
    /** Preemptive {@code java.lang.Thread} runtime: scheduler, preemption tick, per-thread stacks, collector roots. */
    THREADS,
    /** A reachable ordinary {@code Runnable} entry point, as opposed to task-only scheduler use. */
    THREAD_ENTRY,
    /** A reachable structured {@code Callable} entry point. */
    TASK_CALLABLE_ENTRY,
    /** JDK 27 structured scopes layered on the thread runtime, as cooperative subtasks. */
    STRUCTURED_TASKS,
    /** {@code java.lang.ScopedValue} bindings: a per-thread binding stack, inherited by forked subtasks. */
    SCOPED_VALUES,
    /** A reachable {@code ScopedValue.CallableOp} entry point behind {@code Carrier.call}. */
    SCOPED_CALL_ENTRY,
    /** {@code java.util.UUID}: immutable 128-bit values stored in arena blocks. */
    UUID,
    /** A board-native cryptographic random source used by {@code UUID.randomUUID()}. */
    UUID_RANDOM,
    /** {@code java.time.LocalDateTime}: immutable calendar values represented by one-word handles. */
    LOCAL_DATE_TIME,
    /** Internet-backed UTC synchronization used by {@code LocalDateTime.now()}. */
    LOCAL_DATE_TIME_NOW,
    /** The internet clock's SNTP exchange, on its own UDP socket. */
    SNTP,
    /** SD-loaded {@code application.properties} can override the internet clock's SNTP server and port. */
    LOCAL_DATE_TIME_CONFIG,
    /** {@code java.math.BigInteger}/{@code BigDecimal}/{@code MathContext}: immutable arena blocks. */
    BIG_NUMBERS,
    /** {@code BigDecimal.valueOf(double)}, which formats the double with the runtime-string helpers. */
    BIG_DECIMAL_DOUBLE,
    /** {@code Thread.sleep}/{@code Thread.yield} in a program that creates no thread. */
    THREAD_BASICS,
    /** {@code AtomicInteger}/{@code AtomicBoolean}/{@code AtomicLong} cells in the arena. */
    ATOMICS,
    /** AVR only: {@code int} multiply, divide, remainder and variable shifts, which have no 32-bit instruction there. */
    AVR_INT_HELPERS
}

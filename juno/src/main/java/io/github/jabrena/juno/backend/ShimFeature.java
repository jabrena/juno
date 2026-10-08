package io.github.jabrena.juno.backend;

/**
 * An optional part of the generated runtime shim, recorded while lowering the program's IR and
 * consulted by {@link RuntimeShim} so each include and helper block is emitted only when some
 * generated code actually calls into it.
 */
enum ShimFeature {
    /** {@code LedMatrix}: the board's {@code Arduino_LED_Matrix} library, with the core-bundled header. */
    LED_MATRIX,
    MOUSE,
    SERVO,
    /** {@code PoweredUpHubRemote}: a BLE central for LEGO Powered Up hubs over the optional {@code ArduinoBLE} library. */
    LEGO_POWERED_UP,
    /** {@code Infrared}: bit-banged framed bytes over a 38 kHz IR receiver and LED, no library. */
    INFRARED,
    /** {@code I2c}: register access on the primary {@code Wire} bus, with the core-bundled library. */
    I2C,
    WIFI,
    UDP,
    SD,
    LONG,
    FLOAT,
    DOUBLE,
    HTTP,
    HTTPS,
    HTTPS_PATH_BUFFER,
    HTTP_SERVER,
    SMTP,
    SMTP_TLS,
    POP3,
    JSON,
    RUNTIME_STRINGS,
    JSON_STRING_VALUE,
    STRING_BUILDER,
    MEMORY,
    RANDOM,
    EXCEPTIONS,
    /** Cooperative {@code java.lang.Thread} runtime: scheduler, per-thread stacks, collector roots. */
    THREADS,
    /** A reachable ordinary {@code Runnable} entry point, as opposed to task-only scheduler use. */
    THREAD_ENTRY,
    /** A reachable structured {@code Callable} entry point. */
    TASK_CALLABLE_ENTRY,
    /** JDK 27 structured scopes layered on the cooperative thread runtime. */
    STRUCTURED_TASKS,
    /** {@code java.lang.ScopedValue} bindings: a per-thread binding stack, inherited by forked subtasks. */
    SCOPED_VALUES,
    /** A reachable {@code ScopedValue.CallableOp} entry point behind {@code Carrier.call}. */
    SCOPED_CALL_ENTRY,
    /** {@code java.math.BigInteger}/{@code BigDecimal}/{@code MathContext}: immutable arena blocks. */
    BIG_NUMBERS,
    /** {@code BigDecimal.valueOf(double)}, which formats the double with the runtime-string helpers. */
    BIG_DECIMAL_DOUBLE,
    /** {@code Thread.sleep}/{@code Thread.yield} in a program that creates no thread. */
    THREAD_BASICS,
    /** {@code AtomicInteger}/{@code AtomicBoolean}/{@code AtomicLong} cells in the arena. */
    ATOMICS
}

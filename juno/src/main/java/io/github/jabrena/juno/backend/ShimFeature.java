package io.github.jabrena.juno.backend;

/**
 * An optional part of the generated runtime shim, recorded while lowering the program's IR and
 * consulted by {@link RuntimeShim} so each include and helper block is emitted only when some
 * generated code actually calls into it.
 */
enum ShimFeature {
    MOUSE,
    SERVO,
    /** {@code PoweredUpHub}: a BLE central for LEGO Powered Up hubs over the optional {@code ArduinoBLE} library. */
    LEGO_POWERED_UP,
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
    /** JDK 25 structured scopes layered on the cooperative thread runtime. */
    STRUCTURED_TASKS,
    /** {@code Thread.sleep}/{@code Thread.yield} in a program that creates no thread. */
    THREAD_BASICS
}

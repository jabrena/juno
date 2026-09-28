package io.github.jabrena.juno.backend;

/**
 * An optional part of the generated runtime shim, recorded while lowering the program's IR and
 * consulted by {@link RuntimeShim} so each include and helper block is emitted only when some
 * generated code actually calls into it.
 */
enum ShimFeature {
    MOUSE,
    SERVO,
    WIFI,
    SD,
    LONG,
    FLOAT,
    DOUBLE,
    HTTP,
    HTTPS,
    HTTPS_PATH_BUFFER,
    HTTP_SERVER,
    SMTP,
    POP3,
    JSON,
    RUNTIME_STRINGS,
    JSON_STRING_VALUE,
    STRING_BUILDER,
    MEMORY,
    RANDOM,
    EXCEPTIONS
}

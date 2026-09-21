/**
 * A small, allocation-free Internet stack for the Arduino UNO R4 WiFi, backed by the Arduino
 * {@code WiFiS3} library: {@link io.github.jabrena.juno.api.io.net.Wifi} to connect,
 * {@link io.github.jabrena.juno.api.io.net.HttpClient}/{@link
 * io.github.jabrena.juno.api.io.net.HttpsClient} to send HTTP(S) {@code GET}/{@code POST}/
 * {@code DELETE}/{@code PATCH}/{@code QUERY} requests into a caller-owned {@code byte[]} response
 * buffer, {@link io.github.jabrena.juno.api.io.net.HttpResponse} as an immutable snapshot of one
 * such response's status and buffer lengths, and {@link io.github.jabrena.juno.api.io.net.Json} to
 * read typed values directly out of a response buffer without building a parsed document.
 *
 * <p>Every host, path, request body, and JSON path here must be a compile-time constant — a
 * string literal, or a direct {@code System.getenv("NAME")} call with a literal name — because
 * Juno has no heap to build a runtime {@code String} from. See {@code docs/INTERNET.md} in the
 * project documentation for the full walkthrough, including credential handling and JSON path
 * syntax.
 */
package io.github.jabrena.juno.api.io.net;

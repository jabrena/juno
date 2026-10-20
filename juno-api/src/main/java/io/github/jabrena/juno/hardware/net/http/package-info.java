/**
 * HTTP(S) client and server support built on {@link io.github.jabrena.juno.hardware.net.Wifi}'s
 * connection: {@link io.github.jabrena.juno.hardware.net.http.HttpClient} to send {@code http://} and {@code https://}
 * requests into a caller-owned {@code byte[]} response buffer, {@link io.github.jabrena.juno.hardware.net.http.Json} to read typed
 * values directly out of a response buffer without building a parsed document, and {@link
 * io.github.jabrena.juno.hardware.net.http.HttpServer} (plain HTTP only) to serve requests, with
 * {@link io.github.jabrena.juno.hardware.net.http.HttpMethod}/{@link
 * io.github.jabrena.juno.hardware.net.http.HttpStatus} as named constants for both directions.
 *
 * <p>The convenience client's URL/body and every JSON path must normally be a compile-time constant — a string
 * literal, or a direct {@code System.getenv("NAME")} call with a literal name — because Juno has no heap to build a
 * runtime {@code String}. {@link io.github.jabrena.juno.hardware.net.http.HttpClient#requestHttps} is the lower-level
 * exception for protocol drivers: it accepts runtime paths, multiple request-header lines, and bodies in bounded,
 * caller-owned byte buffers. {@link io.github.jabrena.juno.hardware.net.http.HttpServer#respond(int, String, String)} also
 * accepts a runtime response body.
 */
@NullMarked
package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.NullMarked;

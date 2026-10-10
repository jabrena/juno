/**
 * HTTP(S) client and server support built on {@link io.github.jabrena.juno.api.net.Wifi}'s
 * connection: {@link io.github.jabrena.juno.api.net.http.HttpClient}/{@link
 * io.github.jabrena.juno.api.net.http.HttpsClient} to send requests into a caller-owned {@code
 * byte[]} response buffer, {@link io.github.jabrena.juno.api.net.http.Json} to read typed
 * values directly out of a response buffer without building a parsed document, and {@link
 * io.github.jabrena.juno.api.net.http.HttpServer} (plain HTTP only) to serve requests, with
 * {@link io.github.jabrena.juno.api.net.http.HttpMethod}/{@link
 * io.github.jabrena.juno.api.net.http.HttpStatus} as named constants for both directions.
 *
 * <p>Every host, path, request body, and JSON path here must be a compile-time constant — a
 * string literal, or a direct {@code System.getenv("NAME")} call with a literal name — because
 * Juno has no heap to build a runtime {@code String} from ({@link
 * io.github.jabrena.juno.api.net.http.HttpServer#respond(int, String, String)}'s body is the
 * one exception).
 */
@NullMarked
package io.github.jabrena.juno.api.net.http;

import org.jspecify.annotations.NullMarked;

/**
 * HTTP support built on {@link io.github.jabrena.juno.hardware.net.Wifi}'s connection, next to the JDK's
 * {@code java.net.http} client that programs send requests with: {@link io.github.jabrena.juno.hardware.net.http.Json}
 * to read typed values directly out of a response body or a request buffer without building a parsed document, and
 * {@link io.github.jabrena.juno.hardware.net.http.HttpServer} to serve requests, with
 * {@link io.github.jabrena.juno.hardware.net.http.HttpMethod}/{@link
 * io.github.jabrena.juno.hardware.net.http.HttpStatus} as named constants for both directions.
 *
 * <p>Every JSON path may be a literal or a runtime string. {@link
 * io.github.jabrena.juno.hardware.net.http.HttpServer#respond(int, String, String)} also accepts a runtime response
 * body.
 */
@NullMarked
package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.NullMarked;

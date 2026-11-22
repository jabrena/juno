/**
 * HTTP support built on {@link io.github.jabrena.juno.hardware.net.Wifi}'s connection, next to the JDK's
 * {@code java.net.http} client that programs send requests with and the JDK's {@code com.sun.net.httpserver} that they
 * serve requests with: {@link io.github.jabrena.juno.hardware.net.http.Json} and
 * {@link io.github.jabrena.juno.hardware.net.http.JsonValue} to read values directly out of a response or request body
 * without building a parsed document, {@link io.github.jabrena.juno.hardware.net.http.JsonWriter} to write JSON into a
 * buffer, and {@link io.github.jabrena.juno.hardware.net.http.HttpMethod}/{@link
 * io.github.jabrena.juno.hardware.net.http.HttpStatus} as named constants for both directions. JSON support needs the
 * UNO R4 WiFi or a larger board.
 */
@NullMarked
package io.github.jabrena.juno.hardware.net.http;

import org.jspecify.annotations.NullMarked;

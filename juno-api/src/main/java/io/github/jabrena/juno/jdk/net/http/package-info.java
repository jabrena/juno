/**
 * Board-side implementations of {@code java.net.http}: the JDK's HTTP client syntax on the board. The compiler reads
 * every class here under {@code java.net.http}, so a program written against the JDK's {@code HttpClient},
 * {@code HttpRequest} and {@code HttpResponse} runs this code, which sends the request through
 * {@link io.github.jabrena.juno.hardware.net.http.HttpClient}.
 *
 * <p>It is a subset, shaped by the board's fixed memory:
 *
 * <ul>
 *   <li>synchronous {@code GET} only, over HTTP/1.1 whatever {@code version} asks for;</li>
 *   <li>{@code BodyHandlers.ofByteArray()} only. The body is a buffer of fixed capacity,
 *       {@value io.github.jabrena.juno.jdk.net.http.HttpClient#BODY_CAPACITY} bytes, that each request refills and
 *       zero-fills past the bytes received, so a longer body is truncated and {@code body().length} is that capacity
 *       rather than the size received;</li>
 *   <li>the client allocates its response and buffers once, when it is built, so {@code send} allocates nothing;</li>
 *   <li>a request that fails throws {@link java.io.IOException}, as the JDK does; there are no timeouts, redirects,
 *       request headers, cookies or {@code sendAsync}.</li>
 * </ul>
 */
@NullMarked
package io.github.jabrena.juno.jdk.net.http;

import org.jspecify.annotations.NullMarked;

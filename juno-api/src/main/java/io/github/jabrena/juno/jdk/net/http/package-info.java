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
 *   <li>{@code BodyHandlers.ofByteArray()} and {@code BodyHandlers.ofString()}. The client receives the body into a
 *       buffer of fixed capacity, {@value io.github.jabrena.juno.jdk.net.http.HttpClient#BODY_CAPACITY} bytes, so a
 *       longer body is truncated to it. With {@code ofByteArray()} the body is that buffer, refilled by each request
 *       and zero-filled past the bytes received, so {@code body().length} is the capacity rather than the size
 *       received. With {@code ofString()} the body is a {@code String} of the bytes received, not decoded, so
 *       {@code body().length()} is the size received;</li>
 *   <li>the client allocates its buffers once, when it is built. With {@code ofByteArray()} {@code send} allocates
 *       nothing, as the response is the client's own; with {@code ofString()} each response allocates its
 *       {@code String};</li>
 *   <li>a request that fails throws {@link java.io.IOException}, as the JDK does; there are no timeouts, redirects,
 *       request headers, cookies or {@code sendAsync}.</li>
 * </ul>
 */
@NullMarked
package io.github.jabrena.juno.jdk.net.http;

import org.jspecify.annotations.NullMarked;

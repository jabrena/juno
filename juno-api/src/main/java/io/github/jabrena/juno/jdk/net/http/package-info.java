/**
 * Board-side implementations of {@code java.net.http}: the JDK's HTTP client syntax on the board. The compiler reads
 * every class here under {@code java.net.http}, so a program written against the JDK's {@code HttpClient},
 * {@code HttpRequest}, {@code HttpResponse} and {@code HttpHeaders} runs this code, which sends the request through
 * {@link io.github.jabrena.juno.hardware.net.http.HttpClient}.
 *
 * <p>It runs on the UNO R4 WiFi and larger boards; the compiler rejects it on the UNO WiFi Rev2. It is a subset,
 * shaped by the board's fixed memory:
 *
 * <ul>
 *   <li>URIs: any {@code http} or {@code https} URI with a host, of any length;</li>
 *   <li>a client is {@link java.lang.AutoCloseable}, as in the JDK since 21: {@code close}, {@code shutdown} and
 *       {@code shutdownNow} stop it from sending, and {@code isTerminated} and {@code awaitTermination} answer at
 *       once, since every request runs to completion in the thread that sends it;</li>
 *   <li>synchronous {@code send} only, over HTTP/1.1 whatever {@code version} asks for; no {@code sendAsync}, cookies
 *       or authenticator;</li>
 *   <li>requests: {@code GET}, {@code POST}, {@code PUT}, {@code DELETE}, {@code HEAD} and {@code method(...)} for
 *       any other method. Unlike the JDK, {@code CONNECT} is sent too, to the URI's {@code host:port}, and reports its
 *       status, but opens no usable tunnel: the board closes the connection after the response; headers with
 *       {@code header}, {@code headers} and {@code setHeader}, at most
 *       {@value io.github.jabrena.juno.jdk.net.http.HttpRequest#HEADERS_CAPACITY} bytes of them; bodies from
 *       {@code BodyPublishers.ofString} (at most
 *       {@value io.github.jabrena.juno.jdk.net.http.HttpRequest.BodyPublishers#STRING_CAPACITY} bytes),
 *       {@code ofByteArray} and {@code noBody}. A body without a {@code Content-Type} header is sent as
 *       {@code application/json}, the board's default, where the JDK sends none;</li>
 *   <li>responses: {@code BodyHandlers.ofByteArray()} and {@code BodyHandlers.ofString()}. With
 *       {@code ofByteArray()} the client receives the body into a buffer of fixed capacity,
 *       {@value io.github.jabrena.juno.jdk.net.http.HttpClient#BODY_CAPACITY} bytes, refilled by each request and
 *       zero-filled past the bytes received, so a longer body is truncated and {@code body().length} is the capacity
 *       rather than the size received; that path allocates nothing per request. With {@code ofString()} the body is a
 *       {@code String} of every byte received, not decoded, sized to the response ({@code Content-Length}, or grown
 *       as a chunked body arrives), with no limit but the free memory: a body that does not fit fails the request
 *       with an {@code IOException};</li>
 *   <li>{@code headers()}: {@code firstValue} and {@code firstValueAsLong}, from a buffer of
 *       {@value io.github.jabrena.juno.jdk.net.http.HttpClient#RESPONSE_HEADERS_CAPACITY} bytes that each request
 *       refills; a program that never reads headers or follows redirects keeps a 16-byte buffer instead;</li>
 *   <li>redirects: {@code followRedirects(NEVER | ALWAYS | NORMAL)}, at most
 *       {@value io.github.jabrena.juno.jdk.net.http.HttpClient#MAX_REDIRECTS} per request, to an absolute URI or an
 *       absolute path; only to a scheme one of the program's own URIs names, since only those requests are linked;</li>
 *   <li>timeouts: {@code HttpRequest.Builder.timeout} and {@code HttpClient.Builder.connectTimeout}. The board has
 *       one timeout for the whole exchange, the request's if it has one, else the client's, else
 *       {@value io.github.jabrena.juno.jdk.net.http.HttpClient#DEFAULT_TIMEOUT_MILLIS} ms; no response within it
 *       throws {@link io.github.jabrena.juno.jdk.net.http.HttpTimeoutException};</li>
 *   <li>the client allocates its buffers once, when it is built. With {@code ofByteArray()} and no redirect,
 *       {@code send} allocates nothing, as the response is the client's own; with {@code ofString()} each response
 *       allocates its {@code String}, and each redirect followed allocates the next {@code URI};</li>
 *   <li>a request that fails throws {@link java.io.IOException}, as the JDK does.</li>
 * </ul>
 */
@NullMarked
package io.github.jabrena.juno.jdk.net.http;

import org.jspecify.annotations.NullMarked;

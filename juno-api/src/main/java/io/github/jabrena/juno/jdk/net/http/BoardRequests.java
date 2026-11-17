package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.hardware.internal.NativeHttpFeatures;
import io.github.jabrena.juno.hardware.internal.NativeHttpRequests;
import io.github.jabrena.juno.hardware.internal.NativeHttpSchemes;
import io.github.jabrena.juno.jdk.net.URI;
import io.github.jabrena.juno.jdkx.net.ssl.SSLContext;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Sends one {@link HttpClient} request through the board's HTTP or HTTPS request. Each scheme asks its link-time query
 * first, so a program that only ever sends to one scheme links only that scheme's request: the other branch, and its
 * shim, are dropped when the program is linked. The query guards the request in the same method, so that the linker
 * sees the request is unreachable.
 */
final class BoardRequests {
    private static final String NO_HTTPS = "This program links no https request: name an https URI in it to send one";
    private static final String NO_HTTP = "This program links no http request: name an http URI in it to send one";

    private BoardRequests() {
    }

    /**
     * Makes the next HTTPS request trust {@code context}'s certificates, or the board's own when it is {@code null}
     * or carries none. Decided when the program is linked: a program that never calls
     * {@code HttpClient.Builder.sslContext} returns at once, and links no code to change the board's trust.
     *
     * @throws IOException when the board cannot change its trust at runtime
     */
    static void installTrust(@Nullable SSLContext context) throws IOException {
        if (!NativeHttpFeatures.customTrust()) {
            return;
        }
        String pem = context == null ? null : context.trustedCertificates();
        if (!NativeHttpRequests.setTrustedCertificate(pem, pem == null ? 0 : pem.length())) {
            throw new IOException("This board cannot change its trusted certificates at runtime");
        }
    }

    /** Sends the request with the body received into {@code body}, and returns how many bytes it holds. */
    static int receiveBytes(HttpRequest request, URI uri, String host, String method, String target,
            byte[] bodyBytes, int bodyLength, byte[] body, byte[] headers, int[] statusAndHeadersLength)
            throws IOException {
        int length;
        if (uri.isSecure()) {
            if (!NativeHttpSchemes.https()) {
                throw new IOException(NO_HTTPS);
            }
            length = NativeHttpRequests.requestHttps(host, uri.connectPort(), method, target,
                    request.headerBytes(), request.headersLength(), bodyBytes, bodyLength,
                    body, body.length, headers, headers.length, statusAndHeadersLength);
        } else {
            if (!NativeHttpSchemes.http()) {
                throw new IOException(NO_HTTP);
            }
            length = NativeHttpRequests.requestHttp(host, uri.connectPort(), method, target,
                    request.headerBytes(), request.headersLength(), bodyBytes, bodyLength,
                    body, body.length, headers, headers.length, statusAndHeadersLength);
        }
        if (length < 0) {
            throw new IOException("The HTTP request failed");
        }
        return length;
    }

    /** Sends the request and returns its body as a {@code String} sized to it. */
    static String receiveText(HttpRequest request, URI uri, String host, String method, String target,
            byte[] bodyBytes, int bodyLength, byte[] headers, int[] statusAndHeadersLength) throws IOException {
        String received;
        if (uri.isSecure()) {
            if (!NativeHttpSchemes.https()) {
                throw new IOException(NO_HTTPS);
            }
            received = NativeHttpRequests.requestHttpsForText(host, uri.connectPort(), method, target,
                    request.headerBytes(), request.headersLength(), bodyBytes, bodyLength,
                    headers, headers.length, statusAndHeadersLength);
        } else {
            if (!NativeHttpSchemes.http()) {
                throw new IOException(NO_HTTP);
            }
            received = NativeHttpRequests.requestHttpForText(host, uri.connectPort(), method, target,
                    request.headerBytes(), request.headersLength(), bodyBytes, bodyLength,
                    headers, headers.length, statusAndHeadersLength);
        }
        if (received == null) {
            throw new IOException(statusAndHeadersLength[2] == 1
                    ? "The response body does not fit in the free memory" : "The HTTP request failed");
        }
        return received;
    }
}

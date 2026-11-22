package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;

import java.util.List;
import java.util.Set;

/**
 * Source text of the runtime shim's networking helpers: the HTTP/1.1
 * client codec over {@code WiFiClient}/{@code WiFiSSLClient}, the single-connection HTTP
 * server, and SMTP/POP3 — each appended by {@link RuntimeShim} only when the lowered program uses it.
 */
final class NetworkShimLibraries {
    /** The {@code ${JUNO_HTTP_SERVER_HEADER_<hook>}} placeholders in {@code common/http-server.cpp}. */
    private static final List<String> HTTP_SERVER_HEADER_HOOKS = List.of("STATE", "RESET", "CAPTURE", "LOOKUP");

    private final RuntimeSource runtimeSource = new RuntimeSource();


    /** One bounded UDP transport shared by the program's UDP intrinsics. */
    String udpHelpers(String declaration) {
        return runtimeSource.load("common/udp.cpp").replace("${JUNO_UDP_DECLARATION}", declaration);
    }

    /** One caller-owned TCP connection with exact-length reads for application protocols. */
    String tcpHelpers(String clientHelpers) {
        return runtimeSource.load("common/tcp.cpp").replace("${JUNO_TCP_CLIENT_HELPERS}", clientHelpers);
    }

    /**
     * Backs {@code HttpClient.setTimeout} in a program that makes no HTTP/HTTPS request: there is nothing to time out,
     * so the call does nothing. A program with requests gets the real setter from {@link #httpHelpers}.
     */
    String httpTimeoutStub() {
        return """

                extern "C" void juno_http_set_timeout(int32_t) {
                }
                """;
    }

    /**
     * Backs the HTTP/HTTPS APIs with a shared HTTP/1.1 codec over plain or TLS WiFi clients. Each request is a job
     * for {@code juno_io_offload} (see {@link IoRuntime}), run in place.
     */
    String httpHelpers(Set<ShimFeature> features, String httpClientType, String httpsClientType) {
        StringBuilder helpers = new StringBuilder(runtimeSource.load("common/http-client.cpp"));
        if (features.contains(ShimFeature.HTTPS)) {
            helpers.append("""

                    using JunoHttpsClient = ${JUNO_HTTPS_CLIENT_TYPE};
                    """);
        }
        if (features.contains(ShimFeature.HTTPS_REQUEST_TEXT)) {
            helpers.append(runtimeSource.load("common/https-request-text.cpp"));
        }
        if (features.contains(ShimFeature.HTTP_REQUEST_TEXT)) {
            helpers.append(runtimeSource.load("common/http-request-text.cpp"));
        }
        if (features.contains(ShimFeature.HTTPS_REQUEST_TEXT_BODY) || features.contains(ShimFeature.HTTP_REQUEST_TEXT_BODY)) {
            helpers.append(runtimeSource.load("common/http-text-body.cpp"));
        }
        if (features.contains(ShimFeature.HTTPS_REQUEST_TEXT_BODY)) {
            helpers.append(runtimeSource.load("common/https-request-text-body.cpp"));
        }
        if (features.contains(ShimFeature.HTTP_REQUEST_TEXT_BODY)) {
            helpers.append(runtimeSource.load("common/http-request-text-body.cpp"));
        }
        return helpers.toString().replace("${JUNO_HTTP_CLIENT_TYPE}", httpClientType)
                .replace("${JUNO_HTTPS_CLIENT_TYPE}", httpsClientType);
    }

    /**
     * Backs {@code NativeHttpServer} and the board-side {@code com.sun.net.httpserver} on it: a single static {@code
     * WiFiServer} (constructed via placement-new into static storage once {@code begin(port)}
     * runs, since {@code WiFiServer} has no default constructor and this backend never calls the
     * heap allocator {@code new} for anything Java-visible) plus a single "current" {@code
     * WiFiClient} held across one {@code accept()}/{@code respond()} pair — this hardware can only
     * meaningfully serve one request at a time, so there is no connection-handle scheme.
     * {@code accept()} mirrors {@link #httpHelpers(Set, String, String)}'s response-parsing state machine, reversed
     * to parse a request line and headers instead of a status line. With {@code headers}, for {@code NativeHttpServer.header},
     * it also keeps the request's header lines in a fixed buffer; without it the four hooks expand to nothing, so a
     * server that never reads a header gets the same shim as before. With {@code response}, for {@code HttpExchange},
     * it appends the JDK response, written straight to the client; with {@code requestBody}, for
     * {@code HttpExchange.getRequestBody()}, the request body's board-side {@code InputStream}.
     */
    String httpServerHelpers(String transport, boolean headers, boolean response, boolean requestBody) {
        String server = runtimeSource.load("common/http-server.cpp");
        String hooks = headers ? runtimeSource.load("common/http-server-headers.cpp") : "";
        for (String hook : HTTP_SERVER_HEADER_HOOKS) {
            server = server.replace("${JUNO_HTTP_SERVER_HEADER_" + hook + "}", headerHook(hooks, hook));
        }
        return transport + server + (response ? runtimeSource.load("common/http-server-response.cpp") : "")
                + (requestBody ? runtimeSource.load("common/http-server-request-body.cpp") : "");
    }

    /**
     * The section of {@code common/http-server-headers.cpp} that follows its {@code // @@ <hook>} marker, up to the next
     * marker; empty when {@code hooks} is empty, as it is for a server that never reads a header.
     */
    private String headerHook(String hooks, String hook) {
        String marker = "// @@ " + hook + "\n";
        int start = hooks.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = hooks.indexOf("// @@ ", start);
        return end < 0 ? hooks.substring(start) : hooks.substring(start, end);
    }

    /**
     * {@code Smtp}/{@code Pop3Client}'s shim. {@code juno_read_line} is shared and templated over
     * the unrelated client types involved: {@code ESP_SSLClient} ({@code Smtp}'s STARTTLS
     * upgrade wrapper around a plain {@code WiFiClient}) and the core-selected implicit-TLS
     * client used by {@code Pop3Client} — the same template-over-client-type approach as
     * {@link #httpHelpers(Set, String, String)}'s {@code juno_http_request}, reused here because there is no common
     * base class between the two client types worth naming.
     */
    String emailHelpers(Set<ShimFeature> features, String tlsClientDeclaration, String starttlsTrust,
            String starttlsHelpers, String starttlsClient) {
        StringBuilder helpers = new StringBuilder();
        helpers.append(runtimeSource.load("common/email-read-line.cpp"));
        if (features.contains(ShimFeature.SMTP) || features.contains(ShimFeature.SMTP_TLS)) {
            helpers.append(runtimeSource.load("common/email-base64.cpp"));
        }
        if (features.contains(ShimFeature.SMTP)) {
            helpers.append(starttlsHelpers);
            helpers.append(runtimeSource.load("common/smtp-starttls.cpp"));
        }
        if (features.contains(ShimFeature.SMTP_TLS)) {
            helpers.append(runtimeSource.load("common/smtp-tls.cpp"));
        }
        if (features.contains(ShimFeature.POP3)) {
            helpers.append(runtimeSource.load("common/pop3.cpp"));
        }
        return helpers.toString().replace("${JUNO_TLS_CLIENT_DECLARATION}", tlsClientDeclaration)
                .replace("${JUNO_SMTP_STARTTLS_CLIENT}", starttlsClient)
                .replace("${JUNO_SMTP_STARTTLS_TRUST}", starttlsTrust);
    }
}

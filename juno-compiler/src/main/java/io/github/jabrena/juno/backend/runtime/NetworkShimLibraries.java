package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;

import java.util.Set;

/**
 * Source text of the runtime shim's networking helpers: the HTTP/1.1
 * client codec over {@code WiFiClient}/{@code WiFiSSLClient}, the single-connection HTTP
 * server, and SMTP/POP3 — each appended by {@link RuntimeShim} only when the lowered program uses it.
 */
final class NetworkShimLibraries {
    private final RuntimeSource runtimeSource = new RuntimeSource();


    /** One bounded UDP transport shared by the program's UDP intrinsics. */
    String udpHelpers(String declaration) {
        return runtimeSource.load("common/udp.cpp").replace("${JUNO_UDP_DECLARATION}", declaration);
    }

    /**
     * Backs the HTTP/HTTPS APIs with a shared HTTP/1.1 codec over plain or TLS WiFi clients. Each request is a job
     * for {@code juno_io_offload} (see {@link IoRuntime}): run in place, or on the I/O worker while the caller is
     * parked, in which case the request's text is copied first and the caller holds the network guard.
     */
    String httpHelpers(Set<ShimFeature> features, String httpsClientType, boolean worker) {
        StringBuilder helpers = new StringBuilder(runtimeSource.load("common/http-client.cpp"));
        if (features.contains(ShimFeature.HTTPS)) {
            helpers.append("""

                    using JunoHttpsClient = ${JUNO_HTTPS_CLIENT_TYPE};
                    """);
        }
        if (features.contains(ShimFeature.HTTP)) {
            helpers.append(runtimeSource.load("common/http-get.cpp"));
        }
        if (features.contains(ShimFeature.HTTPS)) {
            helpers.append(runtimeSource.load("common/https-get.cpp"));
        }
        if (features.contains(ShimFeature.HTTP_PATH_BUFFER)) {
            helpers.append(runtimeSource.load("common/http-path-buffer.cpp"));
        }
        if (features.contains(ShimFeature.HTTPS_PATH_BUFFER)) {
            helpers.append(runtimeSource.load("common/https-path-buffer.cpp"));
        }
        return helpers.toString().replace("${JUNO_HTTPS_CLIENT_TYPE}", httpsClientType)
                .replace("${JUNO_HTTP_RUN_PREPARE}", worker ? """
                      JunoIoGuard guard(JUNO_IO_NETWORK);
                      char hostCopy[JUNO_IO_TEXT_COPY_BYTES];
                      char pathCopy[JUNO_IO_TEXT_COPY_BYTES];
                      char bodyCopy[JUNO_IO_TEXT_COPY_BYTES];
                      job.host = juno_io_stable_text(host, hostCopy);
                      job.path = juno_io_stable_text(path, pathCopy);
                      job.body = juno_io_stable_text(body, bodyCopy);""" : "");
    }

    /**
     * Backs {@link io.github.jabrena.juno.api.net.http.HttpServer}: a single static {@code
     * WiFiServer} (constructed via placement-new into static storage once {@code begin(port)}
     * runs, since {@code WiFiServer} has no default constructor and this backend never calls the
     * heap allocator {@code new} for anything Java-visible) plus a single "current" {@code
     * WiFiClient} held across one {@code accept()}/{@code respond()} pair — this hardware can only
     * meaningfully serve one request at a time, so there is no connection-handle scheme.
     * {@code accept()} mirrors {@link #httpHelpers(Set, String, boolean)}'s response-parsing state machine, reversed
     * to parse a request line and headers instead of a status line.
     */
    String httpServerHelpers(String transport, boolean worker) {
        return transport + runtimeSource.load("common/http-server.cpp").replace("${JUNO_HTTP_SERVER_RESPOND_PREPARE}", worker ? """
                  char contentTypeCopy[JUNO_IO_TEXT_COPY_BYTES];
                  char bodyCopy[JUNO_IO_TEXT_COPY_BYTES];
                  job.contentType = juno_io_stable_text(contentType, contentTypeCopy);
                  if (bodyLength >= 0 && static_cast<uint32_t>(bodyLength) < JUNO_IO_TEXT_COPY_BYTES) {
                    memcpy(bodyCopy, body, static_cast<size_t>(bodyLength));
                    job.body = bodyCopy;
                  }""" : "");
    }

    /**
     * {@code Smtp}/{@code Pop3Client}'s shim. {@code juno_read_line} is shared and templated over
     * the unrelated client types involved: {@code ESP_SSLClient} ({@code Smtp}'s STARTTLS
     * upgrade wrapper around a plain {@code WiFiClient}) and the core-selected implicit-TLS
     * client used by {@code Pop3Client} — the same template-over-client-type approach as
     * {@link #httpHelpers(Set, String, boolean)}'s {@code juno_http_request}, reused here because there is no common
     * base class between the two client types worth naming.
     */
    String emailHelpers(Set<ShimFeature> features, String tlsClientDeclaration) {
        StringBuilder helpers = new StringBuilder();
        helpers.append(runtimeSource.load("common/email-read-line.cpp"));
        if (features.contains(ShimFeature.SMTP) || features.contains(ShimFeature.SMTP_TLS)) {
            helpers.append(runtimeSource.load("common/email-base64.cpp"));
        }
        if (features.contains(ShimFeature.SMTP)) {
            helpers.append(runtimeSource.load("common/smtp-starttls.cpp"));
        }
        if (features.contains(ShimFeature.SMTP_TLS)) {
            helpers.append(runtimeSource.load("common/smtp-tls.cpp"));
        }
        if (features.contains(ShimFeature.POP3)) {
            helpers.append(runtimeSource.load("common/pop3.cpp"));
        }
        return helpers.toString().replace("${JUNO_TLS_CLIENT_DECLARATION}", tlsClientDeclaration);
    }
}

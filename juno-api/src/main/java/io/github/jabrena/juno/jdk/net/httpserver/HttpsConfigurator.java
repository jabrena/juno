package io.github.jabrena.juno.jdk.net.httpserver;

import io.github.jabrena.juno.javax.net.ssl.SSLContext;

/** The TLS context used by a board HTTPS listener. */
public class HttpsConfigurator {
    private final SSLContext context;

    /** A configurator for the server identity installed in {@code context}. */
    public HttpsConfigurator(SSLContext context) {
        this.context = context;
    }

    /** The SSL context whose key manager holds the server certificate and key. */
    public SSLContext getSSLContext() {
        return context;
    }
}

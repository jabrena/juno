package io.github.jabrena.juno.jdk.net.httpserver;

import io.github.jabrena.juno.javax.net.ssl.SSLContext;

/** The TLS context used by a board HTTPS listener. */
public class HttpsConfigurator {
    private final SSLContext context;

    public HttpsConfigurator(SSLContext context) {
        this.context = context;
    }

    public SSLContext getSSLContext() {
        return context;
    }
}

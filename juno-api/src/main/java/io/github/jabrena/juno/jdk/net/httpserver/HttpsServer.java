package io.github.jabrena.juno.jdk.net.httpserver;

import io.github.jabrena.juno.javax.net.ssl.SSLContext;
import io.github.jabrena.juno.jdk.net.InetSocketAddress;
import org.jspecify.annotations.Nullable;

/** HTTPS server counterpart. Juno's board TLS backend is initialized with its PEM identity before {@link #start()}. */
public final class HttpsServer extends HttpServer {
    private static final int CERTIFICATE_CAPACITY = 2048;
    private @Nullable HttpsConfigurator configurator;
    private HttpsServer(int port, int bodyCapacity) {
        super(port, bodyCapacity);
    }

    public static HttpsServer create(InetSocketAddress address, int backlog) {
        return new HttpsServer(address.getPort(), DEFAULT_BODY_CAPACITY);
    }

    public void setHttpsConfigurator(HttpsConfigurator configurator) {
        this.configurator = configurator;
    }

    public @Nullable HttpsConfigurator getHttpsConfigurator() {
        return configurator;
    }

    /** Starts handler dispatch after the board TLS listener has been configured with PEM credentials. */
    @Override
    public void start() {
        HttpsConfigurator configured = configurator;
        if (configured == null) throw new IllegalStateException("HTTPS configurator is required");
        SSLContext context = configured.getSSLContext();
        String certificate = context.serverCertificate();
        byte @Nullable [] key = context.serverPrivateKey();
        if (certificate == null || key == null) throw new IllegalStateException("Server identity is required");
        if (certificate.length() > CERTIFICATE_CAPACITY) {
            throw new IllegalStateException("Server certificate exceeds board capacity");
        }
        byte[] certificateBytes = new byte[CERTIFICATE_CAPACITY];
        for (int index = 0; index < certificate.length(); index++) {
            certificateBytes[index] = (byte) certificate.charAt(index);
        }
        if (!io.github.jabrena.juno.hardware.net.http.HttpServer.beginTls(
                port, certificateBytes, certificate.length(), key, context.serverPrivateKeyLength())) {
            throw new IllegalStateException("Certificate or private key did not parse");
        }
        for (int index = 0; index < key.length; index++) key[index] = 0;
        startDispatchLoop();
    }
}

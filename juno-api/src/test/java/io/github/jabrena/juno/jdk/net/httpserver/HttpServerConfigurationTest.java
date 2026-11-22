package io.github.jabrena.juno.jdk.net.httpserver;

import io.github.jabrena.juno.javax.net.ssl.SSLContext;
import io.github.jabrena.juno.jdk.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The parts of the board HTTP server subset that run without the board transport. */
class HttpServerConfigurationTest {
    @Test
    void contentTypeMapsTheTwoJsonTypesAndFallsBackToPlainText() {
        Headers headers = new Headers();
        assertThat(headers.contentType()).isZero();
        headers.set("Content-Type", "application/json");
        assertThat(headers.contentType()).isEqualTo(1);
        headers.set("Content-Type", "application/problem+json");
        assertThat(headers.contentType()).isEqualTo(2);
        headers.set("Content-Type", "text/html");
        assertThat(headers.contentType()).isZero();
    }

    @Test
    void headersOtherThanContentTypeAreIgnored() {
        Headers headers = new Headers();
        headers.set("Content-Type", "application/json");
        headers.set("Cache-Control", "no-store");
        assertThat(headers.contentType()).isEqualTo(1);
    }

    @Test
    void contextsChainInRegistrationOrder() {
        HttpHandler handler = exchange -> {
        };
        HttpContext first = new HttpContext("/", handler, null);
        HttpContext second = new HttpContext("/api", handler, first);
        assertThat(second.path()).isEqualTo("/api");
        assertThat(second.handler()).isSameAs(handler);
        assertThat(second.next()).isSameAs(first);
        assertThat(first.next()).isNull();
        HttpContext third = new HttpContext("/api/led", handler, null);
        first.next(third);
        assertThat(first.next()).isSameAs(third);
    }

    @Test
    void configuratorKeepsItsSslContext() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        assertThat(new HttpsConfigurator(context).getSSLContext()).isSameAs(context);
    }

    @Test
    void listenerAddressKeepsItsPort() {
        assertThat(new InetSocketAddress(443).getPort()).isEqualTo(443);
    }
}

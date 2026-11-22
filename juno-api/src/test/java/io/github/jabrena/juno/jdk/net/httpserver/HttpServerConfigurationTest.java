package io.github.jabrena.juno.jdk.net.httpserver;

import io.github.jabrena.juno.javax.net.ssl.SSLContext;
import io.github.jabrena.juno.jdk.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The parts of the board HTTP server subset that run without the board transport. */
class HttpServerConfigurationTest {
    @Test
    void responseContentTypeDefaultsToPlainTextAndKeepsAnyValue() {
        Headers headers = Headers.response();
        assertThat(headers.contentType()).isEqualTo("text/plain");
        headers.set("Content-Type", "application/json");
        assertThat(headers.contentType()).isEqualTo("application/json");
        headers.set("Content-Type", "image/jpeg");
        assertThat(headers.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void responseHeadersOtherThanContentTypeAreIgnored() {
        Headers headers = Headers.response();
        headers.set("Content-Type", "application/json");
        headers.set("Cache-Control", "no-store");
        assertThat(headers.contentType()).isEqualTo("application/json");
    }

    @Test
    void responseHeadersHaveNoRequestValuesAndIgnoreNoSet() {
        Headers response = Headers.response();
        assertThat(response.getFirst("Content-Type")).isNull();
        Headers request = Headers.request();
        request.set("Content-Type", "application/json");
        assertThat(request.contentType()).isEqualTo("text/plain");
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

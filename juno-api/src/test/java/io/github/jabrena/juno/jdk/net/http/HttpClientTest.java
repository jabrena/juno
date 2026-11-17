package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.jdk.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpClientTest {
    private static final URI URI_SENT = URI.create("http://example.com/status");

    @Test
    @DisplayName("the byte-array handler answers with the client's one reused response and its whole buffer")
    void byteArrayHandlerReusesTheClientsResponse() {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse.BodyHandler<byte[]> handler = HttpResponse.BodyHandlers.ofByteArray();

        HttpResponse<byte[]> first = handler.respond(client, 200, 0, URI_SENT);
        HttpResponse<byte[]> second = handler.respond(client, 404, 0, URI_SENT);

        assertThat(second).isSameAs(first);
        assertThat(second.statusCode()).isEqualTo(404);
        assertThat(second.body()).hasSize(HttpClient.BODY_CAPACITY).containsOnly(0);
    }

    @Test
    @DisplayName("the String handler is one shared object, and answers with a new response of the bytes received")
    void stringHandlerAnswersWithItsOwnResponse() {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse.BodyHandler<String> handler = HttpResponse.BodyHandlers.ofString();

        HttpResponse<String> first = handler.respond(client, 200, 0, URI_SENT);
        HttpResponse<String> second = handler.respond(client, 503, 0, URI_SENT);

        assertThat(HttpResponse.BodyHandlers.ofString()).isSameAs(handler);
        assertThat(second).isNotSameAs(first);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(503);
        assertThat(second.body()).isEmpty();
    }

    @Test
    @DisplayName("both responses answer the URI they came from and the client's headers")
    void responsesAnswerTheirUriAndHeaders() {
        HttpClient client = HttpClient.newHttpClient();
        URI redirected = URI.create("https://example.com/next");

        HttpResponse<byte[]> bytes = HttpResponse.BodyHandlers.ofByteArray().respond(client, 200, 0, redirected);
        HttpResponse<String> text = HttpResponse.BodyHandlers.ofString().respond(client, 200, 0, URI_SENT);

        assertThat(bytes.uri()).isSameAs(redirected);
        assertThat(text.uri()).isSameAs(URI_SENT);
        assertThat(text.headers()).isSameAs(bytes.headers());
        assertThat(bytes.headers().firstValue("Location").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a client is AutoCloseable: once closed or shut down it is terminated and sends nothing")
    void closesAsTheJdk() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI_SENT).build();
        HttpClient closed;
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(client.isTerminated()).isFalse();
            assertThat(client.awaitTermination(Duration.ofSeconds(1))).isFalse();
            closed = client;
        }
        HttpClient shutDown = HttpClient.newHttpClient();
        shutDown.shutdown();
        shutDown.shutdown();
        HttpClient shutDownNow = HttpClient.newHttpClient();
        shutDownNow.shutdownNow();

        assertThat(closed.isTerminated()).isTrue();
        assertThat(shutDown.awaitTermination(Duration.ofSeconds(1))).isTrue();
        assertThat(shutDownNow.isTerminated()).isTrue();
        assertThatThrownBy(() -> closed.send(request, HttpResponse.BodyHandlers.ofByteArray()))
                .isInstanceOf(java.io.IOException.class)
                .hasMessage("The client is shut down");
    }

    @Test
    @DisplayName("301, 302, 303, 307 and 308 are the redirects a client follows")
    void knowsTheRedirects() {
        assertThat(HttpClient.isRedirect(301)).isTrue();
        assertThat(HttpClient.isRedirect(302)).isTrue();
        assertThat(HttpClient.isRedirect(303)).isTrue();
        assertThat(HttpClient.isRedirect(307)).isTrue();
        assertThat(HttpClient.isRedirect(308)).isTrue();
        assertThat(HttpClient.isRedirect(200)).isFalse();
        assertThat(HttpClient.isRedirect(304)).isFalse();
    }

    @Test
    @DisplayName("a 303 turns any request but HEAD into a GET, a 301 or 302 only a POST, as the JDK does")
    void changesToGetAsTheJdk() {
        assertThat(HttpClient.changesToGet(303, "POST")).isTrue();
        assertThat(HttpClient.changesToGet(303, "PUT")).isTrue();
        assertThat(HttpClient.changesToGet(303, "HEAD")).isFalse();
        assertThat(HttpClient.changesToGet(301, "POST")).isTrue();
        assertThat(HttpClient.changesToGet(302, "POST")).isTrue();
        assertThat(HttpClient.changesToGet(302, "PUT")).isFalse();
        assertThat(HttpClient.changesToGet(307, "POST")).isFalse();
        assertThat(HttpClient.changesToGet(308, "POST")).isFalse();
    }

    @Test
    @DisplayName("a client follows no redirect unless asked, and its connect timeout must be positive")
    void clientSettings() {
        assertThat(HttpClient.newHttpClient().followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(3)).build().followRedirects())
                .isEqualTo(HttpClient.Redirect.NORMAL);
        assertThatThrownBy(() -> HttpClient.newBuilder().connectTimeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a client answers the SSLContext it was given, or the board's default")
    void answersItsSslContext() throws Exception {
        io.github.jabrena.juno.javax.net.ssl.SSLContext context =
                io.github.jabrena.juno.javax.net.ssl.SSLContext.getInstance("TLS");

        assertThat(HttpClient.newBuilder().sslContext(context).build().sslContext()).isSameAs(context);
        assertThat(HttpClient.newHttpClient().sslContext())
                .isSameAs(io.github.jabrena.juno.javax.net.ssl.SSLContext.getDefault());
    }

    @Test
    @DisplayName("each handler says whether the body is received as a String sized to it or into the byte buffer")
    void handlersChooseHowTheBodyIsReceived() {
        assertThat(HttpResponse.BodyHandlers.ofString().receivesText()).isTrue();
        assertThat(HttpResponse.BodyHandlers.ofByteArray().receivesText()).isFalse();
    }
}

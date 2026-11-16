package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.jdk.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestTest {
    private static final URI URI_SENT = URI.create("http://example.com/status");

    @Test
    @DisplayName("a built request is a GET for the URI it was given, whichever builder form set it")
    void buildsAGetForItsUri() {
        HttpRequest request = HttpRequest.newBuilder().uri(URI_SENT).GET().build();

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri()).isSameAs(URI_SENT);
        assertThat(request.bodyLength()).isEqualTo(HttpRequest.NO_BODY_LENGTH);
        assertThat(request.headersLength()).isZero();
        assertThat(request.timeoutMillis()).isZero();
        assertThat(HttpRequest.newBuilder(URI_SENT).build().uri()).isSameAs(URI_SENT);
    }

    @Test
    @DisplayName("a request without a URI cannot be built")
    void requiresAUri() {
        assertThatThrownBy(() -> HttpRequest.newBuilder().GET().build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("POST and PUT carry their body; DELETE and HEAD carry none")
    void methodsAndBodies() {
        HttpRequest.BodyPublisher json = HttpRequest.BodyPublishers.ofString("{\"a\":1}");

        HttpRequest post = HttpRequest.newBuilder(URI_SENT).POST(json).build();
        HttpRequest put = HttpRequest.newBuilder(URI_SENT).PUT(json).build();
        HttpRequest delete = HttpRequest.newBuilder(URI_SENT).DELETE().build();
        HttpRequest head = HttpRequest.newBuilder(URI_SENT).HEAD().build();

        assertThat(post.method()).isEqualTo("POST");
        assertThat(post.bodyBytes()).isSameAs(json.bodyBytes());
        assertThat(post.bodyLength()).isEqualTo(7);
        assertThat(put.method()).isEqualTo("PUT");
        assertThat(put.bodyLength()).isEqualTo(7);
        assertThat(delete.method()).isEqualTo("DELETE");
        assertThat(delete.bodyLength()).isEqualTo(HttpRequest.NO_BODY_LENGTH);
        assertThat(head.method()).isEqualTo("HEAD");
        assertThat(head.bodyLength()).isEqualTo(HttpRequest.NO_BODY_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "OPTIONS", "CONNECT", "QUERY"})
    @DisplayName("any token method is sent, CONNECT included, unlike the JDK")
    void anyTokenMethod(String method) {
        HttpRequest request = HttpRequest.newBuilder(URI_SENT)
                .method(method, HttpRequest.BodyPublishers.noBody()).build();

        assertThat(request.method()).isEqualTo(method);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "GET POST", "GET\r\n"})
    @DisplayName("an empty method, or one that is not a token, is rejected")
    void rejectsInvalidMethods(String method) {
        assertThatThrownBy(() -> HttpRequest.newBuilder(URI_SENT).method(method, HttpRequest.BodyPublishers.noBody()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("headers are kept as the lines the board sends, in the order they were added")
    void keepsHeaderLines() {
        HttpRequest request = HttpRequest.newBuilder(URI_SENT)
                .header("Accept", "application/json")
                .headers("X-Board", "uno", "X-Board", "r4")
                .build();

        assertThat(headers(request)).isEqualTo("Accept: application/json\r\nX-Board: uno\r\nX-Board: r4\r\n");
    }

    @Test
    @DisplayName("setHeader replaces every header of that name, in any case")
    void setHeaderReplaces() {
        HttpRequest request = HttpRequest.newBuilder(URI_SENT)
                .header("X-Board", "uno")
                .header("Accept", "text/plain")
                .header("x-board", "r4")
                .setHeader("X-BOARD", "esp32")
                .build();

        assertThat(headers(request)).isEqualTo("Accept: text/plain\r\nX-BOARD: esp32\r\n");
    }

    @Test
    @DisplayName("a header added after build does not change the request already built")
    void builtRequestKeepsItsHeaders() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI_SENT).header("Accept", "text/plain");
        HttpRequest first = builder.build();

        HttpRequest second = builder.header("X-Board", "uno").build();

        assertThat(headers(first)).isEqualTo("Accept: text/plain\r\n");
        assertThat(headers(second)).isEqualTo("Accept: text/plain\r\nX-Board: uno\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Host", "content-length", "Connection", "EXPECT", "Upgrade"})
    @DisplayName("the headers the client sets itself are restricted, as in the JDK")
    void rejectsRestrictedHeaders(String name) {
        assertThatThrownBy(() -> HttpRequest.newBuilder(URI_SENT).header(name, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Restricted");
    }

    @Test
    @DisplayName("an invalid name, a value with a line break, a name without a value, and too many bytes are rejected")
    void rejectsInvalidHeaders() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI_SENT);

        assertThatThrownBy(() -> builder.header("", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.header("X Board", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.header("X-Board", "a\r\nHost: evil")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.headers("X-Board")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.header("X-Long", "v".repeat(HttpRequest.HEADERS_CAPACITY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a timeout is kept in milliseconds, and must be positive")
    void keepsTheTimeout() {
        HttpRequest request = HttpRequest.newBuilder(URI_SENT).timeout(Duration.ofSeconds(2)).build();

        assertThat(request.timeoutMillis()).isEqualTo(2000);
        assertThatThrownBy(() -> HttpRequest.newBuilder(URI_SENT).timeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpRequest.newBuilder(URI_SENT).timeout(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("each body publisher has the bytes and length it sends")
    void bodyPublishers() {
        HttpRequest.BodyPublisher text = HttpRequest.BodyPublishers.ofString("{\"a\":1}");
        byte[] raw = {1, 2, 3};
        HttpRequest.BodyPublisher bytes = HttpRequest.BodyPublishers.ofByteArray(raw);
        HttpRequest.BodyPublisher none = HttpRequest.BodyPublishers.noBody();

        assertThat(text.contentLength()).isEqualTo(7);
        assertThat(new String(text.bodyBytes(), 0, 7, StandardCharsets.US_ASCII)).isEqualTo("{\"a\":1}");
        assertThat(bytes.contentLength()).isEqualTo(3);
        assertThat(bytes.bodyBytes()).isSameAs(raw);
        assertThat(none.contentLength()).isZero();
        assertThatThrownBy(() -> HttpRequest.BodyPublishers.ofString("x".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the byte-array body handler is one shared object, so asking for it in a loop allocates nothing")
    void byteArrayHandlerIsShared() {
        assertThat(HttpResponse.BodyHandlers.ofByteArray()).isSameAs(HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    @DisplayName("a client can be built with either factory, and any version")
    void buildsClients() {
        assertThat(HttpClient.newHttpClient()).isNotNull();
        assertThat(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()).isNotNull();
        assertThat(HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build()).isNotNull();
    }

    private static String headers(HttpRequest request) {
        return new String(request.headerBytes(), 0, request.headersLength(), StandardCharsets.US_ASCII);
    }
}

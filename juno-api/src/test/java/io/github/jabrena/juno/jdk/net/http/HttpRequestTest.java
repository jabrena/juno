package io.github.jabrena.juno.jdk.net.http;

import io.github.jabrena.juno.jdk.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestTest {

    @Test
    @DisplayName("a built request is a GET for the URI it was given, whichever builder form set it")
    void buildsAGetForItsUri() {
        URI uri = URI.create("http://example.com/status");

        HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri()).isSameAs(uri);
        assertThat(HttpRequest.newBuilder(uri).build().uri()).isSameAs(uri);
    }

    @Test
    @DisplayName("a request without a URI cannot be built")
    void requiresAUri() {
        assertThatThrownBy(() -> HttpRequest.newBuilder().GET().build()).isInstanceOf(IllegalStateException.class);
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
}

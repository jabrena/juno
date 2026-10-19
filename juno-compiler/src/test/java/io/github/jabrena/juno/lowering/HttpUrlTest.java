package io.github.jabrena.juno.lowering;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
@DisplayName("HTTP URL parsing")
class HttpUrlTest {

    @ParameterizedTest(name = "parses {0}")
    @CsvSource({
        "https://api.example.com/v1/items, true, api.example.com, 443, /v1/items",
        "http://example.com/status, false, example.com, 80, /status",
        "http://192.168.1.10:8080/led, false, 192.168.1.10, 8080, /led",
        "https://example.com, true, example.com, 443, /",
        "https://example.com?q=1, true, example.com, 443, /?q=1"
    })
    @DisplayName("The scheme decides TLS and the default port, and an explicit port overrides the default")
    void shouldParseTheUrlIntoItsParts(String url, boolean tls, String host, int port, String path) {
        // When
        HttpUrl parsed = HttpUrl.parse(url);

        // Then
        assertThat(parsed).isEqualTo(new HttpUrl(tls, host, port, path));
    }

    @Test
    @DisplayName("A query is kept and the fragment is dropped")
    void shouldKeepTheQueryAndDropTheFragment() {
        // When
        String path = HttpUrl.parse("https://example.com/search?q=1#top").path();

        // Then
        assertThat(path).isEqualTo("/search?q=1");
    }

    @ParameterizedTest(name = "isBase is {1} for {0}")
    @CsvSource({
        "https://example.com, true",
        "https://example.com/, true",
        "https://example.com/v1, false"
    })
    @DisplayName("Only a URL without a path is a base")
    void shouldTreatOnlyAUrlWithoutAPathAsABase(String url, boolean expected) {
        // When
        boolean base = HttpUrl.parse(url).isBase();

        // Then
        assertThat(base).isEqualTo(expected);
    }

    @ParameterizedTest(name = "rejects {0}")
    @CsvSource({
        "ftp://example.com/, http:// or https://",
        "https:///path, has no host",
        "http://example.com:0/, invalid port",
        "http://example.com:http/, invalid port",
        "https://user@example.com/, not supported"
    })
    @DisplayName("Rejects URLs it cannot send")
    void shouldRejectUrlsItCannotSend(String url, String message) {
        // When & Then
        assertThatThrownBy(() -> HttpUrl.parse(url)).hasMessageContaining(message);
    }
}

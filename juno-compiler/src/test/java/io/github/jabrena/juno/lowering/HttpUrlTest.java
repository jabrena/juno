package io.github.jabrena.juno.lowering;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpUrlTest {

    @Test
    void theSchemeDecidesTlsAndTheDefaultPort() {
        assertThat(HttpUrl.parse("https://api.example.com/v1/items"))
                .isEqualTo(new HttpUrl(true, "api.example.com", 443, "/v1/items"));
        assertThat(HttpUrl.parse("http://example.com/status"))
                .isEqualTo(new HttpUrl(false, "example.com", 80, "/status"));
    }

    @Test
    void anExplicitPortOverridesTheDefault() {
        assertThat(HttpUrl.parse("http://192.168.1.10:8080/led"))
                .isEqualTo(new HttpUrl(false, "192.168.1.10", 8080, "/led"));
    }

    @Test
    void aMissingPathBecomesRootAndAQueryIsKept() {
        assertThat(HttpUrl.parse("https://example.com")).isEqualTo(new HttpUrl(true, "example.com", 443, "/"));
        assertThat(HttpUrl.parse("https://example.com?q=1")).isEqualTo(new HttpUrl(true, "example.com", 443, "/?q=1"));
        assertThat(HttpUrl.parse("https://example.com/search?q=1#top").path()).isEqualTo("/search?q=1");
    }

    @Test
    void onlyAUrlWithoutAPathIsABase() {
        assertThat(HttpUrl.parse("https://example.com").isBase()).isTrue();
        assertThat(HttpUrl.parse("https://example.com/").isBase()).isTrue();
        assertThat(HttpUrl.parse("https://example.com/v1").isBase()).isFalse();
    }

    @Test
    void rejectsWhatItCannotSend() {
        assertThatThrownBy(() -> HttpUrl.parse("ftp://example.com/")).hasMessageContaining("http:// or https://");
        assertThatThrownBy(() -> HttpUrl.parse("https:///path")).hasMessageContaining("has no host");
        assertThatThrownBy(() -> HttpUrl.parse("http://example.com:0/")).hasMessageContaining("invalid port");
        assertThatThrownBy(() -> HttpUrl.parse("http://example.com:http/")).hasMessageContaining("invalid port");
        assertThatThrownBy(() -> HttpUrl.parse("https://user@example.com/")).hasMessageContaining("not supported");
    }
}

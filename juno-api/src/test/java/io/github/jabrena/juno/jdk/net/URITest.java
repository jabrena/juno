package io.github.jabrena.juno.jdk.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class URITest {

    @Test
    @DisplayName("an http URI splits into its host, the default port and its path with the query")
    void splitsAnHttpUri() {
        URI uri = URI.create("http://api.open-meteo.com/v1/forecast?latitude=40.4168&current=temperature_2m");

        assertThat(uri.getScheme()).isEqualTo("http");
        assertThat(uri.isSecure()).isFalse();
        assertThat(uri.getHost()).isEqualTo("api.open-meteo.com");
        assertThat(uri.getPort()).isEqualTo(-1);
        assertThat(uri.connectPort()).isEqualTo(80);
        assertThat(path(uri)).isEqualTo("/v1/forecast?latitude=40.4168&current=temperature_2m");
    }

    @Test
    @DisplayName("an https URI connects to port 443 unless it names a port")
    void httpsUsesItsDefaultOrItsOwnPort() {
        assertThat(URI.create("https://example.com/").connectPort()).isEqualTo(443);

        URI local = URI.create("https://192.168.1.10:8443/led");

        assertThat(local.isSecure()).isTrue();
        assertThat(local.getHost()).isEqualTo("192.168.1.10");
        assertThat(local.getPort()).isEqualTo(8443);
        assertThat(local.connectPort()).isEqualTo(8443);
        assertThat(path(local)).isEqualTo("/led");
    }

    @Test
    @DisplayName("a URI with no path, or only a query, is sent for the root path")
    void missingPathIsTheRoot() {
        assertThat(path(URI.create("http://example.com"))).isEqualTo("/");
        assertThat(path(URI.create("http://example.com?q=1"))).isEqualTo("/?q=1");
    }

    @Test
    @DisplayName("another scheme, an empty or too-long host, and a too-long path are rejected")
    void rejectsWhatTheBoardCannotSend() {
        assertThatThrownBy(() -> URI.create("ftp://example.com/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http:///path")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://" + "h".repeat(32) + "/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://example.com/" + "p".repeat(255)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String path(URI uri) {
        return new String(uri.pathBytes(), 0, uri.pathLength(), StandardCharsets.US_ASCII);
    }
}

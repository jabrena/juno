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
    @DisplayName("a host may be as long as the longest DNS name, 253 characters")
    void acceptsAHostUpToTheDnsLimit() {
        String longest = "h".repeat(URI.MAX_HOST_LENGTH);

        assertThat(URI.create("http://" + longest + "/").getHost()).isEqualTo(longest);
        assertThat(URI.create("https://weather.api.example-provider.com/v1").getHost())
                .isEqualTo("weather.api.example-provider.com");
    }

    @Test
    @DisplayName("a user info and a fragment are not part of the request, as in the JDK")
    void dropsUserInfoAndFragment() {
        URI uri = URI.create("http://user:pw@example.com:8080/status?q=1#top");

        assertThat(uri.getHost()).isEqualTo("example.com");
        assertThat(uri.getPort()).isEqualTo(8080);
        assertThat(path(uri)).isEqualTo("/status?q=1");
        assertThat(path(URI.create("http://example.com#top"))).isEqualTo("/");
    }

    @Test
    @DisplayName("an empty port means no port, as in the JDK, so the scheme's default is used")
    void emptyPortIsNoPort() {
        URI uri = URI.create("http://example.com:/status");

        assertThat(uri.getHost()).isEqualTo("example.com");
        assertThat(uri.getPort()).isEqualTo(-1);
        assertThat(uri.connectPort()).isEqualTo(80);
        assertThat(path(uri)).isEqualTo("/status");
        assertThat(URI.create("https://example.com:?q=1").connectPort()).isEqualTo(443);
    }

    @Test
    @DisplayName("another scheme, an empty or too-long host, a bad port and a too-long path are rejected")
    void rejectsWhatTheBoardCannotSend() {
        assertThatThrownBy(() -> URI.create("ftp://example.com/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http:///path")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://user@/path")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://" + "h".repeat(URI.MAX_HOST_LENGTH + 1) + "/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://example.com:80a/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://example.com:65536/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://example.com/" + "p".repeat(255)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String path(URI uri) {
        return new String(uri.pathBytes(), 0, uri.pathLength(), StandardCharsets.US_ASCII);
    }
}

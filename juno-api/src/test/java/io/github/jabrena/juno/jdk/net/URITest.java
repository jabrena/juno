package io.github.jabrena.juno.jdk.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertThat(target(uri)).isEqualTo("/v1/forecast?latitude=40.4168&current=temperature_2m");
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
        assertThat(target(local)).isEqualTo("/led");
    }

    @Test
    @DisplayName("a URI with no path, or only a query, is sent for the root path")
    void missingPathIsTheRoot() {
        assertThat(target(URI.create("http://example.com"))).isEqualTo("/");
        assertThat(target(URI.create("http://example.com?q=1"))).isEqualTo("/?q=1");
    }

    @Test
    @DisplayName("the path, the query and the host have no length limit")
    void hasNoLengthLimit() {
        String host = "h".repeat(300);
        String path = "/rest/v1/temperatures?select=*&" + "and=(id.gt.1)".repeat(80);

        URI uri = URI.create("https://" + host + path);

        assertThat(uri.getHost()).isEqualTo(host);
        assertThat(target(uri)).isEqualTo(path).hasSizeGreaterThan(1000);
    }

    @Test
    @DisplayName("a user info and a fragment are not part of the request, as in the JDK")
    void dropsUserInfoAndFragment() {
        URI uri = URI.create("http://user:pw@example.com:8080/status?q=1#top");

        assertThat(uri.getHost()).isEqualTo("example.com");
        assertThat(uri.getPort()).isEqualTo(8080);
        assertThat(uri.getUserInfo()).isEqualTo("user:pw");
        assertThat(uri.getFragment()).isEqualTo("top");
        assertThat(target(uri)).isEqualTo("/status?q=1");
        assertThat(target(URI.create("http://example.com#top"))).isEqualTo("/");
    }

    @Test
    @DisplayName("an empty port means no port, as in the JDK, so the scheme's default is used")
    void emptyPortIsNoPort() {
        URI uri = URI.create("http://example.com:/status");

        assertThat(uri.getHost()).isEqualTo("example.com");
        assertThat(uri.getPort()).isEqualTo(-1);
        assertThat(uri.connectPort()).isEqualTo(80);
        assertThat(target(uri)).isEqualTo("/status");
        assertThat(URI.create("https://example.com:?q=1").connectPort()).isEqualTo(443);
    }

    @Test
    @DisplayName("any scheme is a URI; only an http or https one with a host has a request target")
    void acceptsAnyScheme() {
        URI ftp = URI.create("ftp://files.example.com/pub/readme.txt");
        URI mail = URI.create("mailto:juno@example.com");
        URI file = URI.create("file:///sd/config.properties");

        assertThat(ftp.getScheme()).isEqualTo("ftp");
        assertThat(ftp.getHost()).isEqualTo("files.example.com");
        assertThat(ftp.isHttp()).isFalse();
        assertThat(ftp.requestTarget()).isEmpty();
        assertThat(mail.isOpaque()).isTrue();
        assertThat(mail.getSchemeSpecificPart()).isEqualTo("juno@example.com");
        assertThat(mail.getPath()).isNull();
        assertThat(file.getHost()).isNull();
        assertThat(file.getPath()).isEqualTo("/sd/config.properties");
        assertThat(URI.create("HTTPS://Example.com/").isSecure()).isTrue();
        assertThat(URI.create("HTTPS://Example.com/").getScheme()).isEqualTo("HTTPS");
    }

    @Test
    @DisplayName("a malformed URI and a bad port are rejected")
    void rejectsMalformedUris() {
        assertThatThrownBy(() -> URI.create("http://example.com:80a/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://example.com:65536/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://example.com/a b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://example.com/%zz")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://example.com/#a#b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("mailto:")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> URI.create("http://[::1/")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://api.open-meteo.com/v1/forecast?latitude=40.4168&current=temperature_2m",
        "https://user:p%40ss@example.com:8443/a%20b/c?x=%26y&z=1#frag%21",
        "http://example.com",
        "http://example.com?q=1",
        "http://example.com:/status",
        "https://[2001:db8::1]:8080/ip6",
        "ftp://files.example.com/pub/readme.txt",
        "file:///sd/config.properties",
        "mailto:juno@example.com",
        "urn:isbn:0451450523",
        "HTTPS://Example.COM/Path",
        "news:comp.lang.java#top",
        "/relative/path?q=1",
        "relative/path",
        "../up",
        "?only=query",
        "#only-fragment",
        ""
    })
    @DisplayName("every accessor answers as java.net.URI does")
    void matchesTheJdk(String text) {
        URI board = URI.create(text);
        java.net.URI jdk = java.net.URI.create(text);

        assertThat(board.getScheme()).isEqualTo(jdk.getScheme());
        assertThat(board.isAbsolute()).isEqualTo(jdk.isAbsolute());
        assertThat(board.isOpaque()).isEqualTo(jdk.isOpaque());
        assertThat(board.getSchemeSpecificPart()).isEqualTo(jdk.getSchemeSpecificPart());
        assertThat(board.getRawSchemeSpecificPart()).isEqualTo(jdk.getRawSchemeSpecificPart());
        assertThat(board.getAuthority()).isEqualTo(jdk.getAuthority());
        assertThat(board.getRawAuthority()).isEqualTo(jdk.getRawAuthority());
        assertThat(board.getUserInfo()).isEqualTo(jdk.getUserInfo());
        assertThat(board.getRawUserInfo()).isEqualTo(jdk.getRawUserInfo());
        assertThat(board.getHost()).isEqualTo(jdk.getHost());
        assertThat(board.getPort()).isEqualTo(jdk.getPort());
        assertThat(board.getPath()).isEqualTo(jdk.getPath());
        assertThat(board.getRawPath()).isEqualTo(jdk.getRawPath());
        assertThat(board.getQuery()).isEqualTo(jdk.getQuery());
        assertThat(board.getRawQuery()).isEqualTo(jdk.getRawQuery());
        assertThat(board.getFragment()).isEqualTo(jdk.getFragment());
        assertThat(board.getRawFragment()).isEqualTo(jdk.getRawFragment());
        assertThat(board.toString()).isEqualTo(jdk.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "g", "./g", "g/", "/g", "//g", "g?y", "#s", "g#s", "g?y#s", ";x", "g;x", ".", "./", "..", "../", "../g",
        "../..", "../../", "../../g", "g.", ".g", "g..", "..g", "./../g", "./g/.", "g/./h", "g/../h",
        "https://other.example.com/new", "mailto:juno@example.com"
    })
    @DisplayName("a reference resolves as java.net.URI resolves it, for the RFC 3986 examples the two agree on")
    void resolvesAsTheJdk(String reference) {
        String base = "http://a/b/c/d;p?q";

        assertThat(URI.create(base).resolve(reference).toString())
                .isEqualTo(java.net.URI.create(base).resolve(reference).toString());
    }

    @Test
    @DisplayName("a query-only reference keeps the base path, and an absolute path loses its dot segments, as RFC 3986 says")
    void resolvesAQueryOnlyReferenceAsRfc3986() {
        URI base = URI.create("http://a/b/c/d;p?q");

        assertThat(base.resolve("?y").toString()).isEqualTo("http://a/b/c/d;p?y");
        assertThat(base.resolve("").toString()).isEqualTo("http://a/b/c/d;p?q");
        assertThat(URI.create("http://a").resolve("g").toString()).isEqualTo("http://a/g");
        assertThat(base.resolve("/./g/../h").toString()).isEqualTo("http://a/h");
        assertThat(base.resolve("/a/b/../../../c/./d/").toString()).isEqualTo("http://a/c/d/");
        assertThat(base.resolve("/a//../b").toString()).isEqualTo("http://a/a/b");
        assertThat(base.resolve("/a/../").toString()).isEqualTo("http://a/");
        assertThat(URI.create("mailto:a@b").resolve("g").toString()).isEqualTo("g");
    }

    @Test
    @DisplayName("a redirect location resolves as an absolute URI, a scheme-relative one, or a path on this host")
    void resolvesRedirectLocations() {
        URI current = URI.create("https://api.example.com:8443/v1/old?q=1");

        URI absolute = current.resolve("http://other.example.com/new");
        URI schemeRelative = current.resolve("//cdn.example.com/file");
        URI path = current.resolve("/v2/new?q=2");
        URI relative = current.resolve("relative/path");

        assertThat(absolute.isSecure()).isFalse();
        assertThat(absolute.getHost()).isEqualTo("other.example.com");
        assertThat(schemeRelative.isSecure()).isTrue();
        assertThat(target(schemeRelative)).isEqualTo("/file");
        assertThat(path.getPort()).isEqualTo(8443);
        assertThat(target(path)).isEqualTo("/v2/new?q=2");
        assertThat(target(relative)).isEqualTo("/v1/relative/path");
    }

    @Test
    @DisplayName("equal URIs ignore the case of the scheme and host, as in the JDK, and hash alike")
    void comparesAsTheJdk() {
        URI uri = URI.create("HTTP://Example.COM:8080/a?b#c");
        URI same = URI.create("http://example.com:8080/a?b#c");

        assertThat(uri).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(uri).isNotEqualTo(URI.create("http://example.com:8080/A?b#c"));
        assertThat(uri).isNotEqualTo(URI.create("http://example.com:8081/a?b#c"));
        assertThat(uri).isNotEqualTo(URI.create("http://example.com:8080/a?b"));
        assertThat(uri).isNotEqualTo(URI.create("http://u@example.com:8080/a?b#c"));
        assertThat(URI.create("mailto:a@b")).isNotEqualTo(URI.create("a@b"));
        assertThat(uri).isNotEqualTo("http://example.com:8080/a?b#c");
        assertThat(java.net.URI.create("HTTP://Example.COM:8080/a?b#c"))
                .isEqualTo(java.net.URI.create("http://example.com:8080/a?b#c"));
    }

    @Test
    @DisplayName("a CONNECT target is host:port, with the scheme's default port when the URI names none")
    void writesTheConnectTarget() {
        assertThat(URI.create("https://example.com/").authorityTarget()).isEqualTo("example.com:443");
        assertThat(URI.create("http://192.168.1.10:8080/led").authorityTarget()).isEqualTo("192.168.1.10:8080");
        assertThat(URI.create("http://h:7/").authorityTarget()).isEqualTo("h:7");
        assertThatThrownBy(() -> URI.create("file:///x").authorityTarget())
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String target(URI uri) {
        return uri.requestTarget();
    }
}

package io.github.jabrena.juno.lowering;

/**
 * A compile-time {@code http://} or {@code https://} URL split into what a request needs. The scheme decides whether
 * the request goes over TLS, as it does for {@code java.net.http.HttpClient}; the port defaults to 80 or 443.
 *
 * @param secure whether the scheme is {@code https}
 * @param host the host name or IPv4 address
 * @param port the explicit port, or the scheme's default
 * @param path the path and query, starting with {@code /}; {@code /} when the URL has none
 */
record HttpUrl(boolean secure, String host, int port, String path) {
    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int MAX_PORT = 65535;

    /** Parses {@code url}, or throws {@link IllegalArgumentException} saying what is wrong with it. */
    static HttpUrl parse(String url) {
        boolean secure;
        String rest;
        if (url.startsWith(HTTPS)) {
            secure = true;
            rest = url.substring(HTTPS.length());
        } else if (url.startsWith(HTTP)) {
            secure = false;
            rest = url.substring(HTTP.length());
        } else {
            throw new IllegalArgumentException("\"" + url + "\" must start with http:// or https://");
        }
        int fragment = rest.indexOf('#');
        if (fragment >= 0) {
            rest = rest.substring(0, fragment);
        }
        int authorityEnd = firstOf(rest, '/', '?');
        String authority = authorityEnd < 0 ? rest : rest.substring(0, authorityEnd);
        String path = authorityEnd < 0 ? "/" : rest.substring(authorityEnd);
        if (path.startsWith("?")) {
            path = "/" + path;
        }
        if (authority.indexOf('@') >= 0 || authority.indexOf('[') >= 0) {
            throw new IllegalArgumentException("\"" + url + "\": user info and IPv6 addresses are not supported");
        }
        int colon = authority.indexOf(':');
        String host = colon < 0 ? authority : authority.substring(0, colon);
        int port = colon < 0 ? (secure ? HTTPS_PORT : HTTP_PORT) : port(url, authority.substring(colon + 1));
        if (host.isEmpty()) {
            throw new IllegalArgumentException("\"" + url + "\" has no host");
        }
        return new HttpUrl(secure, host, port, path);
    }

    /** Whether the URL names only a server, as a base for a path supplied while the program runs. */
    boolean isBase() {
        return path.equals("/");
    }

    private static int port(String url, String text) {
        try {
            int port = Integer.parseInt(text);
            if (port >= 1 && port <= MAX_PORT) {
                return port;
            }
        } catch (NumberFormatException notANumber) {
            // Reported below with the URL.
        }
        throw new IllegalArgumentException("\"" + url + "\" has an invalid port \"" + text + "\"");
    }

    private static int firstOf(String text, char first, char second) {
        int a = text.indexOf(first);
        int b = text.indexOf(second);
        if (a < 0) {
            return b;
        }
        return b < 0 ? a : Math.min(a, b);
    }
}

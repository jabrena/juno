package io.github.jabrena.juno.jdk.net;

/**
 * The board-side {@code java.net.URI}: the compiler reads this class as {@code java.net.URI}, so a program that calls
 * {@code URI.create(...)} against the JDK runs this code on the board. It covers what {@code java.net.http} needs: an
 * {@code http} or {@code https} URI with a host, an optional port and a path with an optional query.
 *
 * <p>The URI is split once, when it is created, so a request reads its parts without parsing or allocating. The host
 * becomes a runtime {@code String}, so it may have at most {@value #MAX_HOST_LENGTH} characters; the path and query
 * are kept as ASCII bytes, at most {@value #MAX_PATH_LENGTH} of them. A URI outside those limits, or with another
 * scheme, is rejected with {@link IllegalArgumentException}, as {@code URI.create} rejects a malformed one.
 */
public final class URI {
    /** The longest host: a runtime {@code String} holds at most 31 characters. */
    public static final int MAX_HOST_LENGTH = 31;
    /** The longest path and query, which the board's HTTP client sends in one request line. */
    public static final int MAX_PATH_LENGTH = 255;
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    private final boolean secure;
    private final String host;
    private final int port;
    private final byte[] path = new byte[MAX_PATH_LENGTH];
    private final int pathLength;

    private URI(String text) {
        int start;
        if (startsWith(text, "https://")) {
            secure = true;
            start = 8;
        } else if (startsWith(text, "http://")) {
            secure = false;
            start = 7;
        } else {
            throw new IllegalArgumentException("Only http and https URIs are supported");
        }
        int hostEnd = start;
        while (hostEnd < text.length() && !endsHost(text.charAt(hostEnd))) {
            hostEnd++;
        }
        if (hostEnd == start || hostEnd - start > MAX_HOST_LENGTH) {
            throw new IllegalArgumentException("The host must have 1 to 31 characters");
        }
        StringBuilder name = new StringBuilder(MAX_HOST_LENGTH);
        for (int i = start; i < hostEnd; i++) {
            name.append(text.charAt(i));
        }
        host = name.toString();
        int pathStart = hostEnd;
        int explicitPort = -1;
        if (pathStart < text.length() && text.charAt(pathStart) == ':') {
            explicitPort = 0;
            pathStart++;
            while (pathStart < text.length() && isDigit(text.charAt(pathStart))) {
                explicitPort = explicitPort * 10 + (text.charAt(pathStart) - '0');
                pathStart++;
            }
        }
        port = explicitPort;
        int length = 0;
        if (pathStart == text.length() || text.charAt(pathStart) != '/') {
            path[0] = '/';
            length = 1;
        }
        for (int i = pathStart; i < text.length(); i++) {
            if (length == MAX_PATH_LENGTH) {
                throw new IllegalArgumentException("The path and query must have at most 255 characters");
            }
            path[length] = (byte) text.charAt(i);
            length++;
        }
        pathLength = length;
    }

    /** Splits {@code text} into its scheme, host, port and path; see the class description for the limits. */
    public static URI create(String text) {
        return new URI(text);
    }

    /** {@code "https"} or {@code "http"}. */
    public String getScheme() {
        return secure ? "https" : "http";
    }

    /** The host, without the scheme, port or path. */
    public String getHost() {
        return host;
    }

    /** The port written in the URI, or {@code -1} when it names none, as in the JDK. */
    public int getPort() {
        return port;
    }

    /** Whether the URI uses {@code https}. Board-side only: the JDK class has no such method. */
    public boolean isSecure() {
        return secure;
    }

    /** The port to connect to: the one written in the URI, or the scheme's default. Board-side only. */
    public int connectPort() {
        if (port >= 0) {
            return port;
        }
        return secure ? HTTPS_PORT : HTTP_PORT;
    }

    /** The path and query as ASCII bytes, valid up to {@link #pathLength()}. Board-side only. */
    public byte[] pathBytes() {
        return path;
    }

    /** How many bytes of {@link #pathBytes()} hold the path and query. Board-side only. */
    public int pathLength() {
        return pathLength;
    }

    private static boolean startsWith(String text, String prefix) {
        if (text.length() < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (text.charAt(i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsHost(char character) {
        return character == ':' || character == '/' || character == '?';
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}

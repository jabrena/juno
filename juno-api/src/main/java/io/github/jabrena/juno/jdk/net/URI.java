package io.github.jabrena.juno.jdk.net;

/**
 * The board-side {@code java.net.URI}: the compiler reads this class as {@code java.net.URI}, so a program that calls
 * {@code URI.create(...)} or {@code new URI(...)} against the JDK runs this code on the board. It covers what
 * {@code java.net.http} needs: an {@code http} or {@code https} URI with a host, an optional port and a path with an
 * optional query.
 *
 * <p>The URI is split once, when it is created, so a request reads its parts without parsing or allocating. The host
 * becomes a runtime {@code String} of at most {@value #MAX_HOST_LENGTH} characters, the longest DNS name; the path and
 * query are kept as ASCII bytes, at most {@value #MAX_PATH_LENGTH} of them. As in the JDK, a user info
 * ({@code user@}) and a fragment ({@code #...}) are not part of the request: the host starts after the last {@code @},
 * and the path ends before the {@code #}. An empty port, as in {@code http://host:/}, means no port. A URI outside
 * those limits, with another scheme, or with a port that is not a number from 0 to 65535, is rejected with
 * {@link IllegalArgumentException}, as {@code URI.create} rejects a malformed one.
 */
public final class URI {
    /** The longest host: the longest name DNS allows. */
    public static final int MAX_HOST_LENGTH = 253;
    /** The longest path and query, which the board's HTTP client sends in one request line. */
    public static final int MAX_PATH_LENGTH = 255;
    private static final String HTTPS_PREFIX = "https://";
    private static final String HTTP_PREFIX = "http://";
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int MAX_PORT = 65_535;
    private static final int NO_PORT = -1;

    private final boolean secure;
    private final String host;
    private final int port;
    private final byte[] path = new byte[MAX_PATH_LENGTH];
    private final int pathLength;

    private URI(String text) {
        secure = startsWith(text, HTTPS_PREFIX);
        int authorityStart = authorityStart(text, secure);
        int authorityEnd = authorityEnd(text, authorityStart);
        int hostStart = hostStart(text, authorityStart, authorityEnd);
        int hostEnd = hostEnd(text, hostStart, authorityEnd);
        host = copyHost(text, hostStart, hostEnd);
        port = hostEnd < authorityEnd ? parsePort(text, hostEnd + 1, authorityEnd) : NO_PORT;
        pathLength = copyPath(text, authorityEnd, fragmentStart(text, authorityEnd), path);
    }

    /** Splits {@code text} into its scheme, host, port and path; see the class description for the limits. */
    public static URI create(String text) {
        return new URI(text);
    }

    /** {@code "https"} or {@code "http"}. */
    public String getScheme() {
        return secure ? "https" : "http";
    }

    /** The host, without the user info, scheme, port or path. */
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

    /**
     * Writes {@code host:port} — the request target of a {@code CONNECT} — into {@code target} and returns its length.
     * The port is the one written in the URI, or the scheme's default. Board-side only.
     *
     * @throws IllegalArgumentException if it does not fit in {@code target}
     */
    public int writeAuthority(byte[] target) {
        int targetPort = connectPort();
        int digits = 1;
        for (int rest = targetPort / 10; rest > 0; rest = rest / 10) {
            digits++;
        }
        int length = host.length() + 1 + digits;
        if (length > target.length) {
            throw new IllegalArgumentException("The host is too long for a CONNECT request target");
        }
        for (int i = 0; i < host.length(); i++) {
            target[i] = (byte) host.charAt(i);
        }
        target[host.length()] = ':';
        int value = targetPort;
        for (int i = length - 1; i > host.length(); i--) {
            target[i] = (byte) ('0' + value % 10);
            value = value / 10;
        }
        return length;
    }

    /**
     * The URI a redirect's {@code Location} names, read against this one: an absolute {@code http}/{@code https} URI,
     * a scheme-relative {@code //host/path}, or an absolute path {@code /path} on this URI's host and port.
     * Board-side only; the client calls it while following redirects.
     *
     * @throws IllegalArgumentException for a relative path without {@code /}, or a URI outside the class limits
     */
    public URI resolveRedirect(String location) {
        if (startsWith(location, HTTPS_PREFIX) || startsWith(location, HTTP_PREFIX)) {
            return new URI(location);
        }
        if (startsWith(location, "//")) {
            return new URI(getScheme() + ":" + location);
        }
        if (startsWith(location, "/")) {
            String origin = port >= 0 ? getScheme() + "://" + host + ":" + port : getScheme() + "://" + host;
            return new URI(origin + location);
        }
        throw new IllegalArgumentException("Unsupported redirect location: " + location);
    }

    /** Where the authority starts: just after {@code https://} or {@code http://}. */
    private static int authorityStart(String text, boolean secure) {
        if (secure) {
            return HTTPS_PREFIX.length();
        }
        if (startsWith(text, HTTP_PREFIX)) {
            return HTTP_PREFIX.length();
        }
        throw new IllegalArgumentException("Only http and https URIs are supported");
    }

    /** Where the authority ends: at the first {@code /}, {@code ?} or {@code #}, or at the end. */
    private static int authorityEnd(String text, int from) {
        int end = from;
        while (end < text.length() && !endsAuthority(text.charAt(end))) {
            end++;
        }
        return end;
    }

    /** Where the host starts: after the last {@code @} of the authority, which ends the user info. */
    private static int hostStart(String text, int authorityStart, int authorityEnd) {
        int start = authorityStart;
        for (int i = authorityStart; i < authorityEnd; i++) {
            if (text.charAt(i) == '@') {
                start = i + 1;
            }
        }
        return start;
    }

    /** Where the host ends: at the {@code :} before a port, or at the end of the authority. */
    private static int hostEnd(String text, int hostStart, int authorityEnd) {
        int end = hostStart;
        while (end < authorityEnd && text.charAt(end) != ':') {
            end++;
        }
        return end;
    }

    /** Where the path and query end: at the {@code #} of a fragment, or at the end. */
    private static int fragmentStart(String text, int from) {
        int end = from;
        while (end < text.length() && text.charAt(end) != '#') {
            end++;
        }
        return end;
    }

    private static String copyHost(String text, int start, int end) {
        if (end == start || end - start > MAX_HOST_LENGTH) {
            throw new IllegalArgumentException("The host must have 1 to 253 characters");
        }
        // Sized to this host, not to the longest one, so a short host costs the arena only its own bytes.
        StringBuilder name = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            name.append(text.charAt(i));
        }
        return name.toString();
    }

    /** The port written between {@code start} and {@code end}; none when that is empty, as in the JDK. */
    private static int parsePort(String text, int start, int end) {
        if (start == end) {
            return NO_PORT;
        }
        int value = 0;
        for (int i = start; i < end; i++) {
            char digit = text.charAt(i);
            if (!isDigit(digit)) {
                throw new IllegalArgumentException("The port must be a number");
            }
            value = value * 10 + (digit - '0');
            if (value > MAX_PORT) {
                throw new IllegalArgumentException("The port must be at most 65535");
            }
        }
        return value;
    }

    /**
     * Copies the path and query between {@code start} and {@code end} into {@code target}, starting it with
     * {@code /} when the URI has no path, and returns how many bytes it wrote.
     */
    private static int copyPath(String text, int start, int end, byte[] target) {
        int length = 0;
        if (start == end || text.charAt(start) != '/') {
            target[0] = '/';
            length = 1;
        }
        for (int i = start; i < end; i++) {
            if (length == MAX_PATH_LENGTH) {
                throw new IllegalArgumentException("The path and query must have at most 255 characters");
            }
            target[length] = (byte) text.charAt(i);
            length++;
        }
        return length;
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

    private static boolean endsAuthority(char character) {
        return character == '/' || character == '?' || character == '#';
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}

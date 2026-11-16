package io.github.jabrena.juno.jdk.net;

import org.jspecify.annotations.Nullable;

import static io.github.jabrena.juno.jdk.net.UriText.NONE;
import static io.github.jabrena.juno.jdk.net.UriText.copy;
import static io.github.jabrena.juno.jdk.net.UriText.decode;
import static io.github.jabrena.juno.jdk.net.UriText.indexOfAny;
import static io.github.jabrena.juno.jdk.net.UriText.regionMatchesIgnoreCase;
import static io.github.jabrena.juno.jdk.net.UriText.startsWith;

/**
 * The board-side {@code java.net.URI}: the compiler reads this class as {@code java.net.URI}, so a program that calls
 * {@code URI.create(...)} or {@code new URI(...)} against the JDK runs this code on the board.
 *
 * <p>It parses a URI reference as RFC 3986 does: an optional scheme, then either an opaque part
 * ({@code mailto:a@b.org}) or a hierarchical one with an optional authority ({@code //user@host:port}), a path, an
 * optional query and an optional fragment. A URI without a scheme is a relative reference, which
 * {@link #resolve(URI)} reads against a base URI. Any scheme is accepted; {@code java.net.http} only sends
 * {@code http} and {@code https} ones.
 *
 * <p>The URI keeps its text and where each component starts and ends, so it costs the arena the text it was created
 * from, its host and, for an {@code http} or {@code https} URI, the request target ({@code path?query}) the client
 * sends; none of them has a length limit. The accessors copy their component when they are called. As in the JDK,
 * the {@code getRaw...} accessors return a component as written and the others decode its {@code %XX} escapes. A
 * runtime {@code String} holds bytes, so an escape decodes to one byte: UTF-8 stays UTF-8.
 *
 * <p>Deviations from the JDK: a malformed URI is rejected with {@link IllegalArgumentException} by
 * {@code new URI(...)} as well as by {@code create} (the board has no {@code URISyntaxException}); a port above
 * 65535 is rejected; {@link #resolve(URI)} follows RFC 3986, so a reference that is only a query ({@code ?q}) keeps
 * the base path, where the JDK's RFC 2396 rules drop its last segment, and an absolute-path reference loses its
 * {@code .} and {@code ..} segments, which the JDK keeps; and {@link #equals(Object)} compares {@code %XX} escapes
 * as written.
 */
public final class URI {
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    // The text and its component bounds are read by UriResolution, UriEquality and UriRequestTarget.
    final String text;
    /** Where the scheme's {@code :} is, or {@link UriText#NONE} for a relative reference. */
    final int schemeEnd;
    /** Where the authority starts, just after {@code //}, or {@link UriText#NONE} when there is none. */
    final int authorityStart;
    final int authorityEnd;
    /** Where the host starts and ends: equal when the authority names no host. */
    final int hostStart;
    final int hostEnd;
    final int port;
    /** The path, or for an opaque URI the scheme-specific part, from here to {@link #pathEnd}. */
    final int pathStart;
    final int pathEnd;
    /** Where the query starts, just after {@code ?}, or {@link UriText#NONE}; it ends at {@link #queryEnd}. */
    final int queryStart;
    final int queryEnd;
    /** Where the fragment starts, just after {@code #}, or {@link UriText#NONE}; it ends at the end of the text. */
    final int fragmentStart;
    final boolean opaque;
    final @Nullable String host;
    /** The request target, {@code path?query}, of an {@code http} or {@code https} URI with a host; else empty. */
    private final String target;

    private URI(String text) {
        this.text = text;
        UriSyntax.validateCharacters(text);
        schemeEnd = UriSyntax.schemeEnd(text);
        int start = schemeEnd + 1;
        opaque = schemeEnd != NONE && (start == text.length() || text.charAt(start) != '/');
        if (opaque && (start == text.length() || text.charAt(start) == '#')) {
            throw new IllegalArgumentException("Expected a scheme-specific part: " + text);
        }
        if (!opaque && startsWith(text, start, "//")) {
            authorityStart = start + 2;
            authorityEnd = indexOfAny(text, authorityStart, "/?#");
            hostStart = UriSyntax.hostStart(text, authorityStart, authorityEnd);
            hostEnd = UriSyntax.hostEnd(text, hostStart, authorityEnd);
            port = hostEnd < authorityEnd ? UriSyntax.parsePort(text, hostEnd + 1, authorityEnd) : NONE;
            start = authorityEnd;
        } else {
            authorityStart = NONE;
            authorityEnd = NONE;
            hostStart = NONE;
            hostEnd = NONE;
            port = NONE;
        }
        fragmentStart = UriSyntax.fragmentStart(text, start);
        int fragmentMark = fragmentStart == NONE ? text.length() : fragmentStart - 1;
        pathStart = start;
        pathEnd = opaque ? fragmentMark : indexOfAny(text, start, "?#");
        queryStart = !opaque && pathEnd < fragmentMark ? pathEnd + 1 : NONE;
        queryEnd = queryStart == NONE ? NONE : fragmentMark;
        host = hostStart < hostEnd ? copy(text, hostStart, hostEnd) : null;
        target = isHttp() && host != null ? UriRequestTarget.of(this) : "";
    }

    /**
     * Parses {@code text} as a URI; see the class description.
     *
     * @throws IllegalArgumentException if {@code text} is not a URI
     */
    public static URI create(String text) {
        return new URI(text);
    }

    /** The scheme as written, or {@code null} for a relative reference. */
    public @Nullable String getScheme() {
        if (schemeEnd == NONE) {
            return null;
        }
        // The two schemes a request names, written in lower case, come back as literals: reading them allocates nothing.
        if (schemeEnd == 5 && startsWith(text, 0, "https")) {
            return "https";
        }
        if (schemeEnd == 4 && startsWith(text, 0, "http")) {
            return "http";
        }
        return copy(text, 0, schemeEnd);
    }

    /** Whether the URI has a scheme. */
    public boolean isAbsolute() {
        return schemeEnd != NONE;
    }

    /** Whether the URI is absolute with a part that does not start with {@code /}, as {@code mailto:a@b.org}. */
    public boolean isOpaque() {
        return opaque;
    }

    /** The decoded part after the scheme and before the fragment. */
    public String getSchemeSpecificPart() {
        return decode(text, schemeEnd + 1, schemeSpecificEnd());
    }

    /** The part after the scheme and before the fragment, as written. */
    public String getRawSchemeSpecificPart() {
        return copy(text, schemeEnd + 1, schemeSpecificEnd());
    }

    /** The decoded authority, or {@code null} when there is none. */
    public @Nullable String getAuthority() {
        return authorityStart < authorityEnd ? decode(text, authorityStart, authorityEnd) : null;
    }

    /** The authority as written, or {@code null} when there is none. */
    public @Nullable String getRawAuthority() {
        return authorityStart < authorityEnd ? copy(text, authorityStart, authorityEnd) : null;
    }

    /** The decoded user info, before the host's {@code @}, or {@code null} when there is none. */
    public @Nullable String getUserInfo() {
        return hasUserInfo() ? decode(text, authorityStart, hostStart - 1) : null;
    }

    /** The user info as written, or {@code null} when there is none. */
    public @Nullable String getRawUserInfo() {
        return hasUserInfo() ? copy(text, authorityStart, hostStart - 1) : null;
    }

    /** The host, without the user info or port, or {@code null} when there is none. */
    public @Nullable String getHost() {
        return host;
    }

    /** The port written in the URI, or {@code -1} when it names none. */
    public int getPort() {
        return port;
    }

    /** The decoded path: empty when a hierarchical URI has none, {@code null} for an opaque one. */
    public @Nullable String getPath() {
        return opaque ? null : decode(text, pathStart, pathEnd);
    }

    /** The path as written: empty when a hierarchical URI has none, {@code null} for an opaque one. */
    public @Nullable String getRawPath() {
        return opaque ? null : copy(text, pathStart, pathEnd);
    }

    /** The decoded query, after {@code ?}, or {@code null} when there is none. */
    public @Nullable String getQuery() {
        return queryStart == NONE ? null : decode(text, queryStart, queryEnd);
    }

    /** The query as written, or {@code null} when there is none. */
    public @Nullable String getRawQuery() {
        return queryStart == NONE ? null : copy(text, queryStart, queryEnd);
    }

    /** The decoded fragment, after {@code #}, or {@code null} when there is none. */
    public @Nullable String getFragment() {
        return fragmentStart == NONE ? null : decode(text, fragmentStart, text.length());
    }

    /** The fragment as written, or {@code null} when there is none. */
    public @Nullable String getRawFragment() {
        return fragmentStart == NONE ? null : copy(text, fragmentStart, text.length());
    }

    /** Parses {@code reference} and resolves it against this URI; see {@link #resolve(URI)}. */
    public URI resolve(String reference) {
        return resolve(new URI(reference));
    }

    /**
     * Resolves {@code reference} against this URI as RFC 3986 section 5.2 does: an absolute reference is returned as
     * it is; a reference with an authority keeps only this URI's scheme; any other takes this URI's scheme and
     * authority, and its path is merged with this URI's and has its {@code .} and {@code ..} segments removed.
     * Against an opaque base URI, the reference is returned as it is, as in the JDK.
     */
    public URI resolve(URI reference) {
        return UriResolution.resolve(this, reference);
    }

    /** The text the URI was created from. */
    @Override
    public String toString() {
        return text;
    }

    /**
     * Whether {@code other} is a URI with the same components: the scheme and host compared ignoring case, the others
     * exactly.
     */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof URI that && UriEquality.equal(this, that);
    }

    /** A hash code consistent with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return UriEquality.hash(this);
    }

    /** Whether the URI uses {@code https}, in any case. Board-side only: the JDK class has no such method. */
    public boolean isSecure() {
        return isScheme("https");
    }

    /** Whether the URI's scheme is {@code http} or {@code https}, in any case. Board-side only. */
    public boolean isHttp() {
        return isScheme("http") || isScheme("https");
    }

    /** The port to connect to: the one written in the URI, or the scheme's default. Board-side only. */
    public int connectPort() {
        if (port >= 0) {
            return port;
        }
        return isSecure() ? HTTPS_PORT : HTTP_PORT;
    }

    /**
     * The request target an HTTP client sends, {@code path?query}, with {@code /} for an empty path; empty unless the
     * URI is {@code http} or {@code https} with a host. Board-side only.
     */
    public String requestTarget() {
        return target;
    }

    /**
     * {@code host:port}, the request target of a {@code CONNECT}, with the scheme's default port when the URI names
     * none. Board-side only.
     *
     * @throws IllegalArgumentException if the URI has no host
     */
    public String authorityTarget() {
        return UriRequestTarget.authority(this);
    }

    private boolean isScheme(String scheme) {
        return schemeEnd == scheme.length() && regionMatchesIgnoreCase(text, 0, scheme, scheme.length());
    }

    boolean hasUserInfo() {
        return authorityStart != NONE && hostStart > authorityStart;
    }

    private int schemeSpecificEnd() {
        if (opaque || queryStart == NONE) {
            return pathEnd;
        }
        return queryEnd;
    }
}

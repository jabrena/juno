package io.github.jabrena.juno.jdk.net;

import static io.github.jabrena.juno.jdk.net.UriText.NONE;
import static io.github.jabrena.juno.jdk.net.UriText.append;

/** The request targets an HTTP client sends for a {@link URI}: {@code path?query}, and {@code host:port}. */
final class UriRequestTarget {

    private UriRequestTarget() {
    }

    /** {@code path?query}, sized to it, with {@code /} for an empty path. */
    static String of(URI uri) {
        boolean emptyPath = uri.pathStart == uri.pathEnd;
        int end = uri.queryStart == NONE ? uri.pathEnd : uri.queryEnd;
        StringBuilder target = new StringBuilder((emptyPath ? 1 : 0) + end - uri.pathStart);
        if (emptyPath) {
            target.append('/');
        }
        append(target, uri.text, uri.pathStart, end);
        return target.toString();
    }

    /** See {@link URI#authorityTarget()}. */
    static String authority(URI uri) {
        String name = uri.host;
        if (name == null) {
            throw new IllegalArgumentException("The URI has no host");
        }
        return name + ":" + uri.connectPort();
    }
}

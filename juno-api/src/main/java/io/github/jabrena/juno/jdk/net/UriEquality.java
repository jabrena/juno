package io.github.jabrena.juno.jdk.net;

import static io.github.jabrena.juno.jdk.net.UriText.NONE;
import static io.github.jabrena.juno.jdk.net.UriText.toLower;

/** {@link URI#equals(Object)} and {@link URI#hashCode()}: the scheme and host ignore case, the others do not. */
final class UriEquality {

    private UriEquality() {
    }

    static boolean equal(URI uri, URI that) {
        return uri.opaque == that.opaque && uri.port == that.port
                && same(uri, schemeStart(uri), uri.schemeEnd, that, schemeStart(that), that.schemeEnd, true)
                && same(uri, uri.hostStart, uri.hostEnd, that, that.hostStart, that.hostEnd, true)
                && same(uri, userInfoStart(uri), uri.hostStart - 1, that, userInfoStart(that), that.hostStart - 1,
                        false)
                && same(uri, uri.pathStart, uri.pathEnd, that, that.pathStart, that.pathEnd, false)
                && same(uri, uri.queryStart, uri.queryEnd, that, that.queryStart, that.queryEnd, false)
                && same(uri, uri.fragmentStart, uri.text.length(), that, that.fragmentStart, that.text.length(),
                        false);
    }

    static int hash(URI uri) {
        String text = uri.text;
        int hash = UriText.hash(1, text, schemeStart(uri), uri.schemeEnd, true);
        hash = UriText.hash(hash, text, uri.hostStart, uri.hostEnd, true);
        hash = UriText.hash(hash, text, uri.pathStart, uri.pathEnd, false);
        hash = UriText.hash(hash, text, uri.queryStart, uri.queryEnd, false);
        hash = UriText.hash(hash, text, uri.fragmentStart, text.length(), false);
        return 31 * hash + uri.port;
    }

    private static int schemeStart(URI uri) {
        return uri.schemeEnd == NONE ? NONE : 0;
    }

    private static int userInfoStart(URI uri) {
        return uri.hasUserInfo() ? uri.authorityStart : NONE;
    }

    /** Whether {@code uri}'s {@code [start, end)} equals {@code that}'s {@code [otherStart, otherEnd)}. */
    private static boolean same(URI uri, int start, int end, URI that, int otherStart, int otherEnd,
            boolean ignoreCase) {
        if (start == NONE || otherStart == NONE) {
            return start == otherStart;
        }
        if (end - start != otherEnd - otherStart) {
            return false;
        }
        for (int i = 0; i < end - start; i++) {
            char mine = uri.text.charAt(start + i);
            char theirs = that.text.charAt(otherStart + i);
            if (ignoreCase ? toLower(mine) != toLower(theirs) : mine != theirs) {
                return false;
            }
        }
        return true;
    }
}

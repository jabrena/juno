package io.github.jabrena.juno.jdk.net;

import static io.github.jabrena.juno.jdk.net.UriText.NONE;
import static io.github.jabrena.juno.jdk.net.UriText.append;

/** Resolves a reference against a base {@link URI}, as RFC 3986 section 5.2 does. */
final class UriResolution {

    private UriResolution() {
    }

    /** See {@link URI#resolve(URI)}. */
    static URI resolve(URI base, URI reference) {
        if (reference.isAbsolute() || base.opaque) {
            return reference;
        }
        String text = base.text;
        String referenceText = reference.text;
        StringBuilder resolved = new StringBuilder(text.length() + referenceText.length() + 1);
        append(resolved, text, 0, base.schemeEnd + 1);
        if (reference.authorityStart != NONE) {
            append(resolved, referenceText, reference.authorityStart - 2, reference.pathStart);
            appendNormalized(resolved, referenceText, reference.pathStart, reference.pathEnd);
        } else {
            if (base.authorityStart != NONE) {
                append(resolved, text, base.authorityStart - 2, base.authorityEnd);
            }
            appendPath(resolved, base, reference);
        }
        appendQueryAndFragment(resolved, reference);
        return URI.create(resolved.toString());
    }

    /** Appends the path, and the base query when the reference has neither a path nor a query of its own. */
    private static void appendPath(StringBuilder resolved, URI base, URI reference) {
        String referenceText = reference.text;
        if (reference.pathStart == reference.pathEnd) {
            append(resolved, base.text, base.pathStart, base.pathEnd);
            if (reference.queryStart == NONE && base.queryStart != NONE) {
                resolved.append('?');
                append(resolved, base.text, base.queryStart, base.queryEnd);
            }
        } else if (referenceText.charAt(reference.pathStart) == '/') {
            appendNormalized(resolved, referenceText, reference.pathStart, reference.pathEnd);
        } else {
            String merged = merge(base, reference);
            appendNormalized(resolved, merged, 0, merged.length());
        }
    }

    /** The path a relative-path reference resolves to before its dot segments are removed: RFC 3986's merge. */
    private static String merge(URI base, URI reference) {
        String text = base.text;
        int baseEnd = base.pathEnd;
        while (baseEnd > base.pathStart && text.charAt(baseEnd - 1) != '/') {
            baseEnd--;
        }
        boolean rootOnly = base.authorityStart != NONE && base.pathStart == base.pathEnd;
        StringBuilder merged = new StringBuilder((rootOnly ? 1 : baseEnd - base.pathStart)
                + reference.pathEnd - reference.pathStart);
        if (rootOnly) {
            merged.append('/');
        } else {
            append(merged, text, base.pathStart, baseEnd);
        }
        append(merged, reference.text, reference.pathStart, reference.pathEnd);
        return merged.toString();
    }

    private static void appendQueryAndFragment(StringBuilder target, URI reference) {
        String source = reference.text;
        if (reference.queryStart != NONE) {
            target.append('?');
            append(target, source, reference.queryStart, reference.queryEnd);
        }
        if (reference.fragmentStart != NONE) {
            target.append('#');
            append(target, source, reference.fragmentStart, source.length());
        }
    }

    /**
     * Appends {@code path}'s {@code [start, end)} without its {@code .} and {@code ..} segments: RFC 3986's
     * remove_dot_segments. The board has no arrays of a runtime length, so instead of a stack of segments each one is
     * kept unless the {@code ..} segments after it remove it.
     */
    private static void appendNormalized(StringBuilder target, String path, int start, int end) {
        boolean absolute = start < end && path.charAt(start) == '/';
        if (absolute) {
            target.append('/');
        }
        boolean emitted = false;
        boolean lastEmittedEmpty = false;
        boolean trailingSlash = false;
        int segment = absolute ? start + 1 : start;
        while (segment <= end) {
            int segmentEnd = segment;
            while (segmentEnd < end && path.charAt(segmentEnd) != '/') {
                segmentEnd++;
            }
            if (dots(path, segment, segmentEnd) > 0) {
                trailingSlash = segmentEnd == end;
            } else {
                trailingSlash = false;
                // An empty segment, as in a//b, is kept too: a final one is the trailing slash.
                if (survives(path, segmentEnd, end)) {
                    if (emitted) {
                        target.append('/');
                    }
                    append(target, path, segment, segmentEnd);
                    emitted = true;
                    lastEmittedEmpty = segment == segmentEnd;
                }
            }
            segment = segmentEnd + 1;
        }
        if (trailingSlash && emitted && !lastEmittedEmpty) {
            target.append('/');
        }
    }

    /**
     * Whether the segment ending at {@code from} survives the {@code ..} segments after it: read from the end, each
     * {@code ..} removes the nearest segment before it that no other {@code ..} has removed.
     */
    private static boolean survives(String path, int from, int end) {
        int skip = 0;
        int segmentEnd = end;
        while (segmentEnd > from) {
            int segmentStart = segmentEnd;
            while (segmentStart > from + 1 && path.charAt(segmentStart - 1) != '/') {
                segmentStart--;
            }
            int dots = dots(path, segmentStart, segmentEnd);
            if (dots == 2) {
                skip++;
            } else if (dots == 0 && skip > 0) {
                skip--;
            }
            segmentEnd = segmentStart - 1;
        }
        return skip == 0;
    }

    /** 1 for a {@code .} segment, 2 for a {@code ..} one, else 0. */
    private static int dots(String path, int start, int end) {
        for (int i = start; i < end; i++) {
            if (path.charAt(i) != '.') {
                return 0;
            }
        }
        int length = end - start;
        return length == 1 || length == 2 ? length : 0;
    }
}

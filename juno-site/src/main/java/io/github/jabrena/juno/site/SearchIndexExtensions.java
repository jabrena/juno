package io.github.jabrena.juno.site;

import io.quarkus.qute.TemplateExtension;

/** Template helpers for the search index; they run while the site is generated and never ship to the browser. */
@TemplateExtension
public final class SearchIndexExtensions {

    private SearchIndexExtensions() {
    }

    /** Escapes text for a double-quoted JavaScript string, e.g. {text.jsEscape}. */
    public static String jsEscape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}

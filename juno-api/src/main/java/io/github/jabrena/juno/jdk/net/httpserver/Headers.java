package io.github.jabrena.juno.jdk.net.httpserver;

/** Response headers supported by Juno's bounded HTTP server subset. */
public final class Headers {
    private static final int TEXT_PLAIN = 0;
    private static final int APPLICATION_JSON = 1;
    private static final int PROBLEM_JSON = 2;
    private int contentType = TEXT_PLAIN;

    public void set(String name, String value) {
        if (!name.equals("Content-Type")) return;
        if (value.equals("application/json")) {
            contentType = APPLICATION_JSON;
        } else if (value.equals("application/problem+json")) {
            contentType = PROBLEM_JSON;
        } else {
            contentType = TEXT_PLAIN;
        }
    }

    int contentType() {
        return contentType;
    }
}

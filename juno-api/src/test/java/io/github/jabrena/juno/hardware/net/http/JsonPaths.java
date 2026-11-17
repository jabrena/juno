package io.github.jabrena.juno.hardware.net.http;

import java.nio.charset.StandardCharsets;

/** Test helper: walks a {@code "users[1].name"}-style path with {@link JsonView#get} steps, keeping cases short. */
final class JsonPaths {
    private JsonPaths() {
    }

    static JsonView at(byte[] buffer, int length, String path) {
        return at(Json.view(buffer, length), path);
    }

    static JsonView at(String json, String path) {
        return at(Json.view(json), path);
    }

    static JsonView at(JsonView root, String path) {
        JsonView current = root;
        int p = 0;
        while (p < path.length()) {
            if (path.charAt(p) == '[') {
                int close = path.indexOf(']', p);
                current = current.get(Integer.parseInt(path.substring(p + 1, close)));
                p = close + 1;
            } else {
                int end = p;
                while (end < path.length() && path.charAt(end) != '.' && path.charAt(end) != '[') {
                    end++;
                }
                current = current.get(path.substring(p, end));
                p = end;
            }
            if (p < path.length() && path.charAt(p) == '.') {
                p++;
            }
        }
        return current;
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}

package io.github.jabrena.juno.usecases.supabase.commons;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Sends a {@link SupabaseClient} request with the JDK's {@code java.net.http} client: builds it from the client's path,
 * header-line and body buffers, and copies the reply's body, and its {@code Content-Range} header, back into the
 * {@link SupabaseResponse} buffers.
 */
final class SupabaseTransport {
    private final HttpClient client = HttpClient.newHttpClient();

    /** Sends the request and returns the body bytes copied into {@code response}, or -1 when it failed. */
    int send(String method, String host, byte[] path, int pathLength, byte[] headers, int headersLength,
            byte[] body, int bodyLength, SupabaseResponse response) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://" + host + text(path, 0, pathLength)));
        addHeaders(builder, headers, headersLength);
        if (bodyLength < 0) {
            builder = method.equals("DELETE") ? builder.DELETE() : builder.GET();
        } else {
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArray(body, 0, bodyLength);
            builder = method.equals("POST") ? builder.POST(publisher) : builder.method(method, publisher);
        }
        int[] status = response.statusAndHeadersLength();
        try {
            HttpResponse<String> reply = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            status[0] = reply.statusCode();
            String range = reply.headers().firstValue("content-range").orElse(null);
            // Written where SupabaseContentRange reads it.
            status[1] = range == null ? 0
                    : copy("Content-Range: " + range + "\r\n", response.headersBuffer(), response.headersCapacity());
            return copy(reply.body(), response.bodyBuffer(), response.bodyCapacity());
        } catch (IOException | InterruptedException e) {
            // The board never interrupts a request; either way there is no response.
            status[0] = 0;
            status[1] = 0;
            return -1;
        }
    }

    /** Adds each {@code Name: value} line of {@code headers} to {@code builder}. */
    private static void addHeaders(HttpRequest.Builder builder, byte[] headers, int length) {
        int line = 0;
        while (line < length) {
            int colon = line;
            while (colon < length && headers[colon] != ':') {
                colon = colon + 1;
            }
            int end = colon;
            while (end < length && headers[end] != '\r') {
                end = end + 1;
            }
            if (colon + 2 <= end) {
                builder.header(text(headers, line, colon), text(headers, colon + 2, end));
            }
            line = end + 2;
        }
    }

    /** Copies at most {@code capacity} characters of {@code text} into {@code target} and returns how many. */
    private static int copy(String text, byte[] target, int capacity) {
        int length = text.length() < capacity ? text.length() : capacity;
        for (int index = 0; index < length; index++) {
            target[index] = (byte) text.charAt(index);
        }
        return length;
    }

    private static String text(byte[] buffer, int start, int end) {
        StringBuilder text = new StringBuilder(end - start);
        for (int index = start; index < end; index++) {
            text.append((char) buffer[index]);
        }
        return text.toString();
    }
}

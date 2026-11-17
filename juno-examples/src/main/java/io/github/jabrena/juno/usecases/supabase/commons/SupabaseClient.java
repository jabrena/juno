package io.github.jabrena.juno.usecases.supabase.commons;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Example bounded client for Supabase's PostgREST data API. One instance is created at startup with caller-owned path,
 * request-header, request-body, and response buffers; fluent operations mutate those buffers and {@link #execute()}
 * returns the same {@link SupabaseResponse} instance every time. The request itself goes through the JDK's
 * {@code java.net.http} client: {@link #execute()} builds it from the buffers, and copies the reply's body, and its
 * {@code Content-Range} header, back into the response buffers.
 *
 * <p>{@code projectHost} is the project's DNS host, such as {@code "abcdefgh.supabase.co"}, without an
 * {@code https://} prefix or path. The key belongs in the {@code apikey} header. Firmware is a public client, so use
 * a Supabase publishable key protected by Row Level Security, never a secret or legacy {@code service_role} key. A
 * signed-in user's JWT can be added separately with {@link #authorize(String)}.
 *
 * <p>Table names may contain ASCII letters, digits, underscores, and hyphens; column and parameter names are limited
 * to letters, digits, and underscores. Query values and select expressions are percent-encoded from ASCII. JSON
 * supplied as a {@link String} must be ASCII; use the byte-buffer overloads for pre-encoded UTF-8. Buffer exhaustion
 * makes the current request invalid, so {@link #execute()} returns a rejected response without connecting.
 */
public final class SupabaseClient {
    /** Standard Supabase HTTPS port. */
    public static final int HTTPS_PORT = 443;

    private static final int NONE = 0;
    private static final int SELECT = 1;
    private static final int INSERT = 2;
    private static final int UPDATE = 3;
    private static final int DELETE = 4;

    private final String projectHost;
    private final SupabasePathBuilder path;
    private final byte[] requestHeadersBuffer;
    private final int requestHeadersCapacity;
    private final byte[] requestBodyBuffer;
    private final int requestBodyCapacity;
    private final SupabaseResponse response;
    private final HttpClient client = HttpClient.newHttpClient();

    private int baseHeadersLength;
    private int requestHeadersLength;
    private int requestBodyLength;
    private int operation;
    private int filterCount;
    private boolean queryStarted;
    private boolean headersValid;
    private boolean requestValid;
    private boolean authorizationAdded;

    /**
     * Creates a reusable client. Capacities are explicit because Juno requires array lengths to remain known at the
     * call site; each must be no greater than its corresponding caller-owned buffer.
     */
    public SupabaseClient(
            String projectHost,
            String publishableKey,
            byte[] pathBuffer,
            int pathCapacity,
            byte[] requestHeadersBuffer,
            int requestHeadersCapacity,
            byte[] requestBodyBuffer,
            int requestBodyCapacity,
            SupabaseResponse response) {
        this.projectHost = projectHost;
        path = new SupabasePathBuilder(pathBuffer, pathCapacity);
        this.requestHeadersBuffer = requestHeadersBuffer;
        this.requestHeadersCapacity = requestHeadersCapacity;
        this.requestBodyBuffer = requestBodyBuffer;
        this.requestBodyCapacity = requestBodyCapacity;
        this.response = response;
        requestHeadersLength = SupabaseHeaders.standard(
                requestHeadersBuffer, requestHeadersCapacity, publishableKey);
        baseHeadersLength = requestHeadersLength;
        headersValid = requestHeadersLength >= 0;
    }

    /**
     * Adds an authenticated user's JWT as an {@code Authorization: Bearer} header. Call at most once, during startup.
     * The token is not the publishable key; current Supabase keys belong only in {@code apikey}.
     */
    public SupabaseClient authorize(String accessToken) {
        if (authorizationAdded) {
            headersValid = false;
            return this;
        }
        authorizationAdded = true;
        requestHeadersLength = SupabaseHeaders.appendAuthorization(
                requestHeadersBuffer, requestHeadersCapacity, requestHeadersLength, accessToken);
        baseHeadersLength = requestHeadersLength;
        headersValid = requestHeadersLength >= 0;
        return this;
    }

    /** Starts a request against {@code /rest/v1/table}. */
    public SupabaseClient from(String table) {
        requestBodyLength = 0;
        requestHeadersLength = baseHeadersLength;
        operation = NONE;
        filterCount = 0;
        queryStarted = false;
        requestValid = headersValid;
        path.reset(requestValid);
        path.appendAscii("/rest/v1/");
        path.appendTable(table);
        requestValid = path.valid();
        return this;
    }

    /** Requests an exact total in the response's {@code Content-Range} header for the current select. */
    public SupabaseClient countExact() {
        if (operation != SELECT) {
            requestValid = false;
            return this;
        }
        requestHeadersLength = SupabaseHeaders.appendExactCount(
                requestHeadersBuffer, requestHeadersCapacity, requestHeadersLength);
        requestValid = requestHeadersLength >= 0;
        return this;
    }

    /** Selects the comma-separated columns or PostgREST select expression. */
    public SupabaseClient select(String columns) {
        if (operation != NONE) {
            requestValid = false;
            return this;
        }
        operation = SELECT;
        path.appendParameterName("select", queryStarted);
        path.appendEncoded(columns);
        queryStarted = true;
        requestValid = path.valid();
        return this;
    }

    /** Inserts one JSON object or array supplied as ASCII text. */
    public SupabaseClient insert(String json) {
        return bodyOperation(INSERT, json);
    }

    /** Inserts the first {@code jsonLength} bytes of a caller-owned UTF-8 JSON buffer. */
    public SupabaseClient insert(byte[] json, int jsonLength) {
        return bodyOperation(INSERT, json, jsonLength);
    }

    /** Updates rows matching the filters added after this call, using an ASCII JSON object. */
    public SupabaseClient update(String json) {
        return bodyOperation(UPDATE, json);
    }

    /** Updates rows matching the filters added after this call from a caller-owned UTF-8 JSON buffer. */
    public SupabaseClient update(byte[] json, int jsonLength) {
        return bodyOperation(UPDATE, json, jsonLength);
    }

    /** Deletes rows matching filters added after this call. At least one filter is required before execution. */
    public SupabaseClient delete() {
        if (operation != NONE) {
            requestValid = false;
        } else {
            operation = DELETE;
        }
        return this;
    }

    /** Adds an equality filter, equivalent to {@code column=eq.value}. */
    public SupabaseClient eq(String column, String value) {
        return filter(column, "eq", value);
    }

    /**
     * Adds a PostgREST filter. Supported operators are {@code eq}, {@code neq}, {@code gt}, {@code gte}, {@code lt},
     * {@code lte}, {@code like}, {@code ilike}, and {@code is}.
     */
    public SupabaseClient filter(String column, String operator, String value) {
        if (operation != SELECT && operation != UPDATE && operation != DELETE) {
            requestValid = false;
            return this;
        }
        if (!SupabaseSyntax.supportsOperator(operator)) {
            requestValid = false;
            return this;
        }
        path.appendParameterName(column, queryStarted);
        path.appendAscii(operator);
        path.appendAscii(".");
        path.appendEncoded(value);
        queryStarted = true;
        filterCount = filterCount + 1;
        requestValid = path.valid();
        return this;
    }

    /**
     * Adds a PostgREST filter whose value is the first {@code valueLength} ASCII bytes in caller-owned storage.
     * This supports values calculated on the board without creating a runtime {@link String}.
     */
    public SupabaseClient filter(String column, String operator, byte[] value, int valueLength) {
        if (operation != SELECT && operation != UPDATE && operation != DELETE) {
            requestValid = false;
            return this;
        }
        if (!SupabaseSyntax.supportsOperator(operator) || valueLength < 0) {
            requestValid = false;
            return this;
        }
        path.appendParameterName(column, queryStarted);
        path.appendAscii(operator);
        path.appendAscii(".");
        path.appendEncoded(value, valueLength);
        queryStarted = true;
        filterCount = filterCount + 1;
        requestValid = path.valid();
        return this;
    }

    /** Orders the selected rows ascending by one column, as keyset pagination needs. */
    public SupabaseClient order(String column) {
        if (operation != SELECT) {
            requestValid = false;
            return this;
        }
        path.appendParameterName("order", queryStarted);
        path.appendEncoded(column);
        path.appendAscii(".asc");
        queryStarted = true;
        requestValid = path.valid();
        return this;
    }

    /** Adds a positive PostgREST row limit. */
    public SupabaseClient limit(int rows) {
        if (operation != SELECT || rows <= 0) {
            requestValid = false;
            return this;
        }
        path.appendParameterName("limit", queryStarted);
        path.appendPositiveInt(rows);
        queryStarted = true;
        requestValid = path.valid();
        return this;
    }

    /** Adds a non-negative PostgREST row offset after the configured ordering. */
    public SupabaseClient offset(int rows) {
        if (operation != SELECT || rows < 0) {
            requestValid = false;
            return this;
        }
        path.appendParameterName("offset", queryStarted);
        path.appendPositiveInt(rows);
        queryStarted = true;
        requestValid = path.valid();
        return this;
    }

    /**
     * Executes the built request over HTTPS and returns the reusable response. Update and delete operations require at
     * least one filter as a guard against accidentally changing an entire table.
     */
    public SupabaseResponse execute() {
        if (!requestValid || operation == NONE || ((operation == UPDATE || operation == DELETE) && filterCount == 0)) {
            response.rejected();
            return response;
        }
        int length;
        if (operation == SELECT) {
            length = request("GET", -1);
        } else if (operation == INSERT) {
            length = request("POST", requestBodyLength);
        } else if (operation == UPDATE) {
            length = request("PATCH", requestBodyLength);
        } else {
            length = request("DELETE", -1);
        }
        response.completed(length);
        return response;
    }

    /** Whether the current request fits all buffers and is structurally executable. */
    public boolean ready() {
        return requestValid && operation != NONE && !((operation == UPDATE || operation == DELETE) && filterCount == 0);
    }

    /** The current request-path length, useful for diagnostics without creating a String. */
    public int pathLength() {
        return path.length();
    }

    /** The current request-body length. */
    public int requestBodyLength() {
        return requestBodyLength;
    }

    /** The request-header length prepared at startup. */
    public int requestHeadersLength() {
        return requestHeadersLength;
    }

    /** Sends the built request and returns the body bytes copied into the response, or -1 when it failed. */
    private int request(String method, int bodyLength) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("https://" + projectHost + text(path.buffer(), path.length())));
        addHeaders(builder);
        if (bodyLength < 0) {
            builder = method.equals("DELETE") ? builder.DELETE() : builder.GET();
        } else {
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofByteArray(requestBodyBuffer, 0, bodyLength);
            builder = method.equals("POST") ? builder.POST(body) : builder.method(method, body);
        }
        int[] status = response.statusAndHeadersLength();
        try {
            HttpResponse<String> reply = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            status[0] = reply.statusCode();
            status[1] = copyContentRange(reply.headers().firstValue("content-range").orElse(null));
            return copy(reply.body(), response.bodyBuffer(), response.bodyCapacity());
        } catch (IOException | InterruptedException e) {
            // The board never interrupts a request; either way there is no response.
            status[0] = 0;
            status[1] = 0;
            return -1;
        }
    }

    /** Adds each {@code Name: value} line of the request-header buffer to {@code builder}. */
    private void addHeaders(HttpRequest.Builder builder) {
        int line = 0;
        while (line < requestHeadersLength) {
            int colon = line;
            while (colon < requestHeadersLength && requestHeadersBuffer[colon] != ':') {
                colon = colon + 1;
            }
            int end = colon;
            while (end < requestHeadersLength && requestHeadersBuffer[end] != '\r') {
                end = end + 1;
            }
            if (colon + 2 <= end) {
                builder.header(text(requestHeadersBuffer, line, colon), text(requestHeadersBuffer, colon + 2, end));
            }
            line = end + 2;
        }
    }

    /** Writes {@code Content-Range: value} into the response-header buffer, where SupabaseContentRange reads it. */
    private int copyContentRange(String value) {
        if (value == null) {
            return 0;
        }
        return copy("Content-Range: " + value + "\r\n", response.headersBuffer(), response.headersCapacity());
    }

    /** Copies at most {@code capacity} characters of {@code text} into {@code target} and returns how many. */
    private static int copy(String text, byte[] target, int capacity) {
        int length = text.length() < capacity ? text.length() : capacity;
        for (int index = 0; index < length; index++) {
            target[index] = (byte) text.charAt(index);
        }
        return length;
    }

    private static String text(byte[] buffer, int length) {
        return text(buffer, 0, length);
    }

    private static String text(byte[] buffer, int start, int end) {
        StringBuilder text = new StringBuilder(end - start);
        for (int index = start; index < end; index++) {
            text.append((char) buffer[index]);
        }
        return text.toString();
    }

    private SupabaseClient bodyOperation(int nextOperation, String json) {
        if (operation != NONE) {
            requestValid = false;
            return this;
        }
        operation = nextOperation;
        requestBodyLength = 0;
        int length = json.length();
        int index = 0;
        while (index < length && requestValid) {
            int value = json.charAt(index);
            if (value > 127) {
                requestValid = false;
            } else {
                appendBodyByte(value);
            }
            index = index + 1;
        }
        return this;
    }

    private SupabaseClient bodyOperation(int nextOperation, byte[] json, int jsonLength) {
        if (operation != NONE || jsonLength < 0 || jsonLength > requestBodyCapacity) {
            requestValid = false;
            return this;
        }
        operation = nextOperation;
        requestBodyLength = 0;
        int index = 0;
        while (index < jsonLength) {
            appendBodyByte(json[index]);
            index = index + 1;
        }
        return this;
    }

    private void appendBodyByte(int value) {
        if (requestBodyLength >= requestBodyCapacity) {
            requestValid = false;
            return;
        }
        requestBodyBuffer[requestBodyLength] = (byte) value;
        requestBodyLength = requestBodyLength + 1;
    }
}

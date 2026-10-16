package io.github.jabrena.juno.usecases.supabase;

import io.github.jabrena.juno.api.net.http.HttpClient;

/**
 * Example allocation-free, bounded client for Supabase's PostgREST data API. One instance is created at startup with
 * caller-owned path, request-header, request-body, and response buffers; fluent operations mutate those buffers and
 * {@link #execute()} returns the same {@link SupabaseResponse} instance every time.
 *
 * <p>{@code projectHost} is the project's DNS host, such as {@code "abcdefgh.supabase.co"}, without an
 * {@code https://} prefix or path. The key belongs in the {@code apikey} header. Firmware is a public client, so use
 * a Supabase publishable key protected by Row Level Security, never a secret or legacy {@code service_role} key. A
 * signed-in user's JWT can be added separately with {@link #authorize(String)}.
 *
 * <p>Table and column names are limited to ASCII letters, digits, and underscores. Query values and select
 * expressions are percent-encoded from ASCII. JSON supplied as a {@link String} must be ASCII; use the byte-buffer
 * overloads for pre-encoded UTF-8. Buffer exhaustion makes the current request invalid, so {@link #execute()} returns
 * a rejected response without connecting. No method allocates after startup.
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
    private final byte[] pathBuffer;
    private final int pathCapacity;
    private final byte[] requestHeadersBuffer;
    private final int requestHeadersCapacity;
    private final byte[] requestBodyBuffer;
    private final int requestBodyCapacity;
    private final SupabaseResponse response;

    private int pathLength;
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
        this.pathBuffer = pathBuffer;
        this.pathCapacity = pathCapacity;
        this.requestHeadersBuffer = requestHeadersBuffer;
        this.requestHeadersCapacity = requestHeadersCapacity;
        this.requestBodyBuffer = requestBodyBuffer;
        this.requestBodyCapacity = requestBodyCapacity;
        this.response = response;
        requestHeadersLength = SupabaseHeaders.standard(
                requestHeadersBuffer, requestHeadersCapacity, publishableKey);
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
        headersValid = requestHeadersLength >= 0;
        return this;
    }

    /** Starts a request against {@code /rest/v1/table}. */
    public SupabaseClient from(String table) {
        pathLength = 0;
        requestBodyLength = 0;
        operation = NONE;
        filterCount = 0;
        queryStarted = false;
        requestValid = headersValid;
        appendPathAscii("/rest/v1/");
        appendIdentifier(table);
        return this;
    }

    /** Selects the comma-separated columns or PostgREST select expression. */
    public SupabaseClient select(String columns) {
        if (operation != NONE) {
            requestValid = false;
            return this;
        }
        operation = SELECT;
        appendParameterName("select");
        appendEncoded(columns);
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
        appendParameterName(column);
        appendPathAscii(operator);
        appendPathByte('.');
        appendEncoded(value);
        filterCount = filterCount + 1;
        return this;
    }

    /** Adds a positive PostgREST row limit. */
    public SupabaseClient limit(int rows) {
        if (operation != SELECT || rows <= 0) {
            requestValid = false;
            return this;
        }
        appendParameterName("limit");
        appendPositiveInt(rows);
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
        return pathLength;
    }

    /** The current request-body length. */
    public int requestBodyLength() {
        return requestBodyLength;
    }

    /** The request-header length prepared at startup. */
    public int requestHeadersLength() {
        return requestHeadersLength;
    }

    private int request(String method, int bodyLength) {
        return HttpClient.requestHttps(
                projectHost,
                HTTPS_PORT,
                method,
                pathBuffer,
                pathLength,
                requestHeadersBuffer,
                requestHeadersLength,
                requestBodyBuffer,
                bodyLength,
                response.bodyBuffer(),
                response.bodyCapacity(),
                response.headersBuffer(),
                response.headersCapacity(),
                response.statusAndHeadersLength());
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

    private void appendIdentifier(String identifier) {
        int length = identifier.length();
        if (length == 0) {
            requestValid = false;
            return;
        }
        int index = 0;
        while (index < length && requestValid) {
            int character = identifier.charAt(index);
            if (!SupabaseSyntax.identifierCharacter(character)) {
                requestValid = false;
            } else {
                appendPathByte(character);
            }
            index = index + 1;
        }
    }

    private void appendParameterName(String name) {
        appendPathByte(queryStarted ? '&' : '?');
        queryStarted = true;
        appendIdentifier(name);
        appendPathByte('=');
    }

    private void appendEncoded(String value) {
        int length = value.length();
        int index = 0;
        while (index < length && requestValid) {
            int character = value.charAt(index);
            if (character > 127) {
                requestValid = false;
            } else if (SupabaseSyntax.unreserved(character)) {
                appendPathByte(character);
            } else {
                appendPathByte('%');
                appendPathByte(SupabaseSyntax.hex(character >> 4));
                appendPathByte(SupabaseSyntax.hex(character & 15));
            }
            index = index + 1;
        }
    }

    private void appendPositiveInt(int value) {
        int divisor = 1;
        while (value / divisor >= 10 && divisor <= 100_000_000) {
            divisor = divisor * 10;
        }
        while (divisor > 0) {
            appendPathByte('0' + value / divisor % 10);
            divisor = divisor / 10;
        }
    }

    private void appendPathAscii(String value) {
        int length = value.length();
        int index = 0;
        while (index < length && requestValid) {
            appendPathByte(value.charAt(index));
            index = index + 1;
        }
    }

    private void appendPathByte(int value) {
        if (pathLength >= pathCapacity) {
            requestValid = false;
            return;
        }
        pathBuffer[pathLength] = (byte) value;
        pathLength = pathLength + 1;
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

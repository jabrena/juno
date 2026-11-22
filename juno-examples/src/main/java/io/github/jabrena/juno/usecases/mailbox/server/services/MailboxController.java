package io.github.jabrena.juno.usecases.mailbox.server.services;

import com.sun.net.httpserver.HttpExchange;
import io.github.jabrena.juno.hardware.net.http.HttpMethod;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.net.http.JsonWriter;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.mailbox.server.commons.MailboxUiState;
import io.github.jabrena.juno.usecases.mailbox.server.security.MailboxSecurity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * The mailbox HTTPS controller, the {@code HttpsServer}'s one handler: translates requests into model operations and
 * renders HTTP responses.
 */
public final class MailboxController {
    private static final String MAILBOX_PATH = "/api/v1/mailbox/";
    private static final String INFO_PATH = "/actuator/info";
    private static final String INFO = "{\"info\":\"Juno Mailbox\"}";
    private static final String JSON = "application/json";
    private static final String PROBLEM_JSON = "application/problem+json";

    private final MailboxModel model;
    private final MailboxSecurity security;
    private final MailboxUiState uiState;
    /** An RFC 7807 problem body, written again for each error response. */
    private final JsonWriter problem = new JsonWriter(new byte[160]);
    /** The request body, read once per request from the exchange. */
    private final byte[] requestBody = new byte[MailboxModel.MAX_REQUEST_BYTES];
    private final Clock clock;
    /** The exchange being answered; set for the duration of {@link #handle(HttpExchange)}. */
    private HttpExchange exchange;

    public MailboxController(MailboxModel model, MailboxSecurity security, MailboxUiState uiState, Clock clock) {
        this.model = model;
        this.security = security;
        this.uiState = uiState;
        this.clock = clock;
    }

    /** Answers one request; one Serial line per request gives the time to answer it, to find where clients wait. */
    public void handle(HttpExchange current) throws IOException {
        int started = clock.millis();
        exchange = current;
        String method = current.getRequestMethod();
        int bodyLength = readBody(current.getRequestBody());
        route(method, current.getRequestURI().toString(), contentLength(), bodyLength);
        Serial.print("HTTPS ");
        Serial.print(method);
        Serial.print(" handle ms ");
        Serial.println(clock.millis() - started);
    }

    /** The declared {@code Content-Length}, or 0 when the request has none. */
    private int contentLength() {
        String declared = exchange.getRequestHeaders().getFirst("Content-Length");
        if (declared == null) {
            return 0;
        }
        int value = 0;
        for (int i = 0; i < declared.length(); i++) {
            char digit = declared.charAt(i);
            if (digit < '0' || digit > '9') {
                return 0;
            }
            value = value * 10 + (digit - '0');
        }
        return value;
    }

    private int readBody(InputStream body) throws IOException {
        int length = 0;
        while (length < requestBody.length) {
            int count = body.read(requestBody, length, requestBody.length - length);
            if (count <= 0) {
                break;
            }
            length += count;
        }
        return length;
    }

    private void route(String method, String path, int contentLength, int bodyLength) throws IOException {
        // accept() truncates a body larger than its buffer; the declared Content-Length reveals it before parsing.
        if (contentLength > requestBody.length) {
            respondProblem(HttpStatus.CONTENT_TOO_LARGE, "Request body is larger than the mailbox accepts");
            return;
        }
        if (path.equals(INFO_PATH)) {
            if (method.equals(HttpMethod.GET)) {
                respond(HttpStatus.OK, JSON, INFO);
            } else {
                methodNotAllowed();
            }
            return;
        }
        if (!startsWith(path, MAILBOX_PATH)) {
            respondProblem(HttpStatus.NOT_FOUND, "No mailbox message at the requested path");
            return;
        }
        if (!model.restoreComplete()) {
            respondProblem(HttpStatus.SERVICE_UNAVAILABLE, "Mailbox messages are still loading");
            return;
        }
        synchronized (security) {
            if (!security.matches(copyKey(exchange.getRequestHeaders().getFirst(MailboxSecurity.HEADER)))) {
                respondProblem(HttpStatus.UNAUTHORIZED, "Missing or wrong Mailbox-API-Key header");
                return;
            }
        }
        if (path.equals(MAILBOX_PATH)) {
            if (method.equals(HttpMethod.GET)) {
                respondWithList();
            } else if (method.equals(HttpMethod.POST)) {
                createMessage(requestBody, bodyLength);
            } else {
                methodNotAllowed();
            }
            return;
        }

        int index = model.findMessage(path, MAILBOX_PATH);
        if (index < 0) {
            respondProblem(HttpStatus.NOT_FOUND, "No mailbox message at the requested path");
        } else if (method.equals(HttpMethod.GET)) {
            respondWithMessage(HttpStatus.OK, index);
        } else if (method.equals(HttpMethod.DELETE)) {
            deleteMessage(index);
        } else {
            methodNotAllowed();
        }
    }

    /** Copies the header value into the key candidate buffer; its length, or -1 when the request has no header. */
    private int copyKey(String value) {
        if (value == null) {
            return -1;
        }
        byte[] key = security.candidate();
        int length = value.length() < key.length ? value.length() : key.length;
        for (int i = 0; i < length; i++) {
            key[i] = (byte) value.charAt(i);
        }
        return length;
    }

    private boolean startsWith(String path, String prefix) {
        if (path.length() < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (path.charAt(i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private void createMessage(byte[] requestBody, int bodyLength) throws IOException {
        int status = model.createMessage(requestBody, bodyLength);
        if (status != HttpStatus.CREATED) {
            respondProblem(status, model.errorMessage());
            return;
        }
        uiState.publishMessageCount(model.messageCount());
        respondWithMessage(status, model.messageCount() - 1);
    }

    private void respondWithList() throws IOException {
        StringBuilder response = new StringBuilder(208);
        response.append('[');
        for (int i = 0; i < model.messageCount(); i++) {
            if (i > 0) {
                response.append(',');
            }
            response.append("{\"uuid\":\"");
            appendUuid(response, model.messageUuid(i));
            response.append("\"}");
        }
        response.append(']');
        respond(HttpStatus.OK, JSON, response.toString());
    }

    private void appendUuid(StringBuilder response, UUID uuid) {
        appendHex(response, uuid.getMostSignificantBits() >>> 32, 8);
        response.append('-');
        appendHex(response, uuid.getMostSignificantBits() >>> 16, 4);
        response.append('-');
        appendHex(response, uuid.getMostSignificantBits(), 4);
        response.append('-');
        appendHex(response, uuid.getLeastSignificantBits() >>> 48, 4);
        response.append('-');
        appendHex(response, uuid.getLeastSignificantBits(), 12);
    }

    private void appendHex(StringBuilder response, long value, int digits) {
        for (int shift = (digits - 1) * 4; shift >= 0; shift -= 4) {
            int digit = (int) (value >>> shift) & 0x0f;
            response.append((char) (digit < 10 ? '0' + digit : 'a' + digit - 10));
        }
    }

    private void respondWithMessage(int status, int index) throws IOException {
        StringBuilder response = new StringBuilder(MailboxModel.STORED_MESSAGE_CAPACITY);
        if (!model.appendStoredMessage(response, index)) {
            respondProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read mailbox message");
            return;
        }
        respond(status, JSON, response.toString());
    }

    private void deleteMessage(int index) throws IOException {
        if (!model.deleteMessage(index)) {
            respondProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Could not delete mailbox message");
            return;
        }
        uiState.publishMessageCount(model.messageCount());
        exchange.getResponseHeaders().set("Content-Type", JSON);
        exchange.sendResponseHeaders(HttpStatus.NO_CONTENT, -1);
        exchange.getResponseBody().close();
    }

    private void methodNotAllowed() throws IOException {
        respondProblem(HttpStatus.METHOD_NOT_ALLOWED, "Method is not allowed for this mailbox resource");
    }

    private void respondProblem(int status, String detail) throws IOException {
        problem.reset().beginObject().name("type").value("about:blank").name("title").value(detail)
                .name("status").value(status).endObject();
        respond(status, PROBLEM_JSON, problem.toString());
    }

    private void respond(int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            for (int i = 0; i < body.length(); i++) {
                output.write(body.charAt(i));
            }
        }
    }
}

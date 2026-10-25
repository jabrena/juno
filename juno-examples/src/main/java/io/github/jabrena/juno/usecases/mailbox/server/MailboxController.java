package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.net.http.HttpMethod;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;

import java.util.UUID;

/** The mailbox HTTP controller: translates requests into model operations and renders HTTP responses. */
public final class MailboxController {
    private static final String MAILBOX_PATH = "/api/v1/mailbox/";
    private static final String JSON = "application/json";
    private static final String PROBLEM_JSON = "application/problem+json";

    private final MailboxModel model;
    private final MailboxView view;

    public MailboxController(MailboxModel model, MailboxView view) {
        this.model = model;
        this.view = view;
    }

    void handle(String method, String path, int contentLength, byte[] requestBody, int bodyLength) {
        // accept() truncates a body larger than its buffer; the declared Content-Length reveals it before parsing.
        if (contentLength > requestBody.length) {
            respondProblem(HttpStatus.CONTENT_TOO_LARGE, "Request body is larger than the mailbox accepts");
            return;
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

    private void createMessage(byte[] requestBody, int bodyLength) {
        int status = model.createMessage(requestBody, bodyLength);
        if (status != HttpStatus.CREATED) {
            respondProblem(status, model.errorMessage());
            return;
        }
        view.drawMessageCount(model.messageCount());
        respondWithMessage(status, model.messageCount() - 1);
    }

    private void respondWithList() {
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
        HttpServer.respond(HttpStatus.OK, JSON, response);
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

    private void respondWithMessage(int status, int index) {
        StringBuilder response = new StringBuilder(MailboxModel.STORED_MESSAGE_CAPACITY);
        if (!model.appendStoredMessage(response, index)) {
            respondProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read mailbox message");
            return;
        }
        HttpServer.respond(status, JSON, response);
    }

    private void deleteMessage(int index) {
        if (!model.deleteMessage(index)) {
            respondProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Could not delete mailbox message");
            return;
        }
        view.drawMessageCount(model.messageCount());
        HttpServer.respond(HttpStatus.NO_CONTENT, JSON, "");
    }

    private void methodNotAllowed() {
        respondProblem(HttpStatus.METHOD_NOT_ALLOWED, "Method is not allowed for this mailbox resource");
    }

    private void respondProblem(int status, String detail) {
        StringBuilder response = new StringBuilder(160);
        response.append("{\"type\":\"about:blank\",\"title\":");
        appendJsonString(response, detail);
        response.append(",\"status\":");
        appendInt(response, status);
        response.append('}');
        HttpServer.respond(status, PROBLEM_JSON, response);
    }

    private void appendJsonString(StringBuilder response, String value) {
        response.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"' || character == '\\') {
                response.append('\\');
            } else if (character == '\n') {
                response.append('\\').append('n');
                continue;
            } else if (character == '\r') {
                response.append('\\').append('r');
                continue;
            } else if (character == '\t') {
                response.append('\\').append('t');
                continue;
            }
            response.append(character);
        }
        response.append('"');
    }

    private void appendInt(StringBuilder response, int value) {
        String text = String.valueOf(value);
        for (int i = 0; i < text.length(); i++) {
            response.append(text.charAt(i));
        }
    }
}

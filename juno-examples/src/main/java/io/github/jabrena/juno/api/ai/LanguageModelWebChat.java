package io.github.jabrena.juno.api.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

/**
 * The web chat with the on-board JLM1 model, shared by the access-point and the Secret Store examples: {@code GET /}
 * serves the chat page, {@code POST /api/chat} with the question as the body streams the answer back one character at
 * a time as the model generates it, and {@code GET /api/status} reports the model. Whatever the network, the model
 * runs on the board.
 */
public final class LanguageModelWebChat {
    private static final LanguageModel MODEL = MCU.getLanguageModel();
    private static final byte[] QUESTION = new byte[LanguageModel.MAX_QUESTION];
    private static final byte[] RESOURCE_BUFFER = new byte[512];

    private LanguageModelWebChat() {
    }

    /** Adds the chat page and API to {@code server}; start the server afterwards. */
    public static void register(HttpServer server) {
        server.createContext("/api/chat", LanguageModelWebChat::chat);
        server.createContext("/api/status", LanguageModelWebChat::status);
        server.createContext("/ai/language-model-web-chat.css", LanguageModelWebChat::style);
        server.createContext("/ai/language-model-web-chat.js", LanguageModelWebChat::script);
        server.createContext("/", LanguageModelWebChat::page);
    }

    private static void page(HttpExchange exchange) throws IOException {
        try (InputStream resource = LanguageModelWebChat.class.getResourceAsStream(
                "/ai/language-model-web-chat.html")) {
            sendResource(exchange, resource, "text/html; charset=utf-8");
        }
    }

    private static void style(HttpExchange exchange) throws IOException {
        try (InputStream resource = LanguageModelWebChat.class.getResourceAsStream(
                "/ai/language-model-web-chat.css")) {
            sendResource(exchange, resource, "text/css; charset=utf-8");
        }
    }

    private static void script(HttpExchange exchange) throws IOException {
        try (InputStream resource = LanguageModelWebChat.class.getResourceAsStream(
                "/ai/language-model-web-chat.js")) {
            sendResource(exchange, resource, "text/javascript; charset=utf-8");
        }
    }

    private static void sendResource(HttpExchange exchange, InputStream resource, String contentType) throws IOException {
        if (resource == null) {
            send(exchange, 404, "text/plain; charset=utf-8", "resource not found");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(HTTP_OK, resource.available());
        try (OutputStream output = exchange.getResponseBody()) {
            int count;
            while ((count = resource.read(RESOURCE_BUFFER, 0, RESOURCE_BUFFER.length)) >= 0) {
                if (count > 0) output.write(RESOURCE_BUFFER, 0, count);
            }
        }
    }

    private static void status(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(HTTP_OK, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            writeText(output, MODEL.isLoaded() ? "{\"loaded\":true,\"layers\":" : "{\"loaded\":false,\"layers\":");
            writeNumber(output, MODEL.layers());
            writeText(output, ",\"width\":");
            writeNumber(output, MODEL.width());
            writeText(output, "}");
        }
    }

    /** Streams the answer to the question in the request body as plain text, each character as soon as it exists. */
    private static void chat(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, HTTP_BAD_METHOD, "text/plain", "POST a question");
            return;
        }
        int length = readBody(exchange.getRequestBody());
        if (MODEL.ask(QUESTION, length, 0) != ModelStatus.OK) {
            send(exchange, HTTP_UNAVAILABLE, "text/plain", "The model cannot answer that.");
            return;
        }
        Serial.print("[CHAT] ");
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(HTTP_OK, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            for (int c = MODEL.nextChar(); c != LanguageModel.END; c = MODEL.nextChar()) {
                output.write(c);
                Serial.write(c);
            }
        }
        Serial.println("");
    }

    private static int readBody(InputStream body) throws IOException {
        int length = 0;
        while (length < QUESTION.length) {
            int count = body.read(QUESTION, length, QUESTION.length - length);
            if (count <= 0) break;
            length += count;
        }
        return length;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            writeText(output, body);
        }
    }

    private static void writeText(OutputStream output, String text) throws IOException {
        for (int i = 0; i < text.length(); i++) output.write(text.charAt(i));
    }

    private static void writeNumber(OutputStream output, int value) throws IOException {
        if (value >= 10) writeNumber(output, value / 10);
        output.write('0' + value % 10);
    }
}

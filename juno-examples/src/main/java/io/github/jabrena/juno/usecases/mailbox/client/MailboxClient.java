package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.mvc.Task;

/** Allocation-free, retrying HTTP client for the mailbox API. */
public final class MailboxClient implements Task {
    private static final int HTTP_PORT = 80;
    private static final int REFRESH_MILLIS = 5 * 60 * 1000;
    private static final int RESPONSE_CAPACITY = 1536;
    private static final int HEADERS_CAPACITY = 256;
    private static final int PATH_CAPACITY = 256;
    private static final int REQUEST_CAPACITY = 430;
    private static final int UUID_LENGTH = 36;

    private final MailboxModel model;
    private final Clock clock;
    private final byte[] path = new byte[PATH_CAPACITY];
    private final byte[] requestHeaders = new byte[1];
    private final byte[] requestBody = new byte[REQUEST_CAPACITY];
    private final byte[] response = new byte[RESPONSE_CAPACITY];
    private final byte[] headers = new byte[HEADERS_CAPACITY];
    private final int[] statusAndHeadersLength = new int[2];
    private String host;
    private int nextRefresh;
    private boolean refreshNow;

    public MailboxClient(MailboxModel model, String host) {
        this(model, host, ArduinoUno.getClock());
    }

    MailboxClient(MailboxModel model, String host, Clock clock) {
        this.model = model;
        this.host = host;
        this.clock = clock;
        refreshNow = true;
    }

    public void host(String value) {
        host = value;
        requestRefresh();
    }

    public void requestRefresh() {
        refreshNow = true;
    }

    @Override
    public void step() {
        int now = clock.millis();
        if (refreshNow || now - nextRefresh >= 0) {
            refreshNow = false;
            nextRefresh = now + REFRESH_MILLIS;
            refresh();
        }
    }

    /** Reads the list and then every message so the inbox can display titles immediately. */
    public boolean refresh() {
        if (host == null || host.length() == 0) {
            model.status("Set mailbox IP in Settings");
            return false;
        }
        Serial.println("Mailbox client: GET /api/v1/mailbox/");
        int listLength = getPath("/api/v1/mailbox/", 16);
        Serial.print("Mailbox client: list status ");
        Serial.print(statusAndHeadersLength[0]);
        Serial.print(" bytes ");
        Serial.println(listLength);
        if (listLength < 0 || statusAndHeadersLength[0] != HttpStatus.OK) {
            Serial.println("Mailbox client: list request failed");
            model.status("Mailbox unavailable; retrying");
            return false;
        }
        int count = Json.arraySize(response, listLength, "");
        Serial.print("Mailbox client: list count ");
        Serial.println(count);
        if (count < 0 || count > MailboxModel.MAX_MESSAGES) {
            Serial.println("Mailbox client: invalid list response");
            model.status("Invalid mailbox response");
            return false;
        }
        model.clear();
        // Every detail request reuses the response buffer, so take all the uuids from the list before the first one.
        for (int index = 0; index < count; index++) {
            int uuidLength = uuidAt(listLength, index);
            if (uuidLength != UUID_LENGTH) {
                Serial.print("Mailbox client: invalid UUID length ");
                Serial.println(uuidLength);
                model.status("Invalid message UUID; retrying");
                return false;
            }
        }
        for (int index = 0; index < count; index++) {
            if (!readMessage(index)) {
                Serial.print("Mailbox client: detail request failed for index ");
                Serial.println(index);
                model.status("Message read failed; retrying");
                return false;
            }
        }
        model.status(count == 0 ? "Mailbox is empty" : "Updated just now");
        model.finishRefresh();
        return true;
    }

    /** Copies the list's uuid at {@code index} into the model's uuid slot for that message. */
    private int uuidAt(int listLength, int index) {
        byte[] uuid = model.uuid(index);
        if (index == 0) {
            return Json.getString(response, listLength, "[0].uuid", uuid, UUID_LENGTH);
        }
        if (index == 1) {
            return Json.getString(response, listLength, "[1].uuid", uuid, UUID_LENGTH);
        }
        if (index == 2) {
            return Json.getString(response, listLength, "[2].uuid", uuid, UUID_LENGTH);
        }
        return Json.getString(response, listLength, "[3].uuid", uuid, UUID_LENGTH);
    }

    private boolean readMessage(int index) {
        byte[] uuid = model.uuid(index);
        int pathLength = copyMessagePath(uuid, UUID_LENGTH);
        int bodyLength = getPath(path, pathLength);
        Serial.print("Mailbox client: detail status ");
        Serial.print(statusAndHeadersLength[0]);
        Serial.print(" bytes ");
        Serial.println(bodyLength);
        if (bodyLength < 0 || statusAndHeadersLength[0] != HttpStatus.OK) {
            return false;
        }
        int titleLength = Json.getString(
                response, bodyLength, "title", model.title(index), MailboxModel.MAX_TITLE_BYTES);
        int messageBodyLength = Json.getString(
                response, bodyLength, "body", model.body(index), MailboxModel.MAX_BODY_BYTES);
        model.add(uuid, UUID_LENGTH, titleLength, messageBodyLength);
        return true;
    }

    /** Deletes the message with this uuid and refreshes the inbox when the board confirms it. */
    public boolean delete(byte[] uuid, int uuidLength) {
        if (host == null || host.length() == 0) {
            model.status("Set mailbox IP in Settings");
            return false;
        }
        int pathLength = copyMessagePath(uuid, uuidLength);
        Serial.println("Mailbox client: DELETE message");
        int responseLength = HttpClient.requestHttp(host, HTTP_PORT, "DELETE", path, pathLength,
                requestHeaders, 0, null, -1, response, response.length,
                headers, headers.length, statusAndHeadersLength);
        Serial.print("Mailbox client: delete status ");
        Serial.println(statusAndHeadersLength[0]);
        boolean deleted = responseLength >= 0 && statusAndHeadersLength[0] == HttpStatus.NO_CONTENT;
        model.status(deleted ? "Message deleted" : "Delete failed");
        if (deleted) {
            requestRefresh();
        }
        return deleted;
    }

    public boolean send(char[] title, int titleLength, char[] body, int bodyLength) {
        if (host == null || host.length() == 0) {
            model.status("Set mailbox IP in Settings");
            return false;
        }
        int requestLength = encodeMessage(title, titleLength, body, bodyLength);
        if (requestLength < 0) {
            model.status("Message is too large");
            return false;
        }
        int pathLength = copyPath("/api/v1/mailbox/");
        int responseLength = HttpClient.requestHttp(host, HTTP_PORT, "POST", path, pathLength,
                requestHeaders, 0, requestBody, requestLength, response, response.length,
                headers, headers.length, statusAndHeadersLength);
        boolean sent = responseLength >= 0 && statusAndHeadersLength[0] == HttpStatus.CREATED;
        model.status(sent ? "Message sent" : "Send failed; retrying");
        if (sent) {
            requestRefresh();
        }
        return sent;
    }

    private int getPath(String requestPath, int pathLength) {
        int length = copyPath(requestPath);
        return getPath(path, length);
    }

    private int getPath(byte[] requestPath, int pathLength) {
        return HttpClient.requestHttp(host, HTTP_PORT, "GET", requestPath, pathLength,
                requestHeaders, 0, null, -1, response, response.length,
                headers, headers.length, statusAndHeadersLength);
    }

    private int copyMessagePath(byte[] uuid, int uuidLength) {
        int length = copyPath("/api/v1/mailbox/");
        if (length < 0 || uuidLength < 0 || length + uuidLength > path.length) {
            return -1;
        }
        for (int index = 0; index < uuidLength; index++) {
            path[length] = uuid[index];
            length = length + 1;
        }
        return length;
    }

    private int copyPath(String value) {
        if (value.length() >= path.length) {
            return -1;
        }
        for (int index = 0; index < value.length(); index++) {
            path[index] = (byte) value.charAt(index);
        }
        return value.length();
    }

    private int encodeMessage(char[] title, int titleLength, char[] body, int bodyLength) {
        int position = 0;
        position = appendAscii(position, "{\"title\":\"");
        position = appendJson(position, title, titleLength);
        position = appendAscii(position, "\",\"body\":\"");
        position = appendJson(position, body, bodyLength);
        position = appendAscii(position, "\"}");
        return position < 0 ? -1 : position;
    }

    private int appendJson(int position, char[] value, int length) {
        for (int index = 0; index < length; index++) {
            char character = value[index];
            if (character == '"' || character == '\\') {
                position = appendByte(position, '\\');
            } else if (character == '\n') {
                position = appendAscii(position, "\\n");
                continue;
            } else if (character == '\r') {
                position = appendAscii(position, "\\r");
                continue;
            }
            position = appendByte(position, character);
        }
        return position;
    }

    private int appendAscii(int position, String value) {
        for (int index = 0; index < value.length(); index++) {
            position = appendByte(position, value.charAt(index));
        }
        return position;
    }

    private int appendByte(int position, int value) {
        if (position < 0 || position >= requestBody.length) {
            return -1;
        }
        requestBody[position] = (byte) value;
        return position + 1;
    }
}

package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.mvc.Task;

/**
 * Allocation-free, retrying HTTP client for the mailbox API. Every mailbox request carries the {@link MailboxKey}'s
 * header; only the discovery probe of {@code /actuator/info}, which the board leaves open, goes without it.
 */
public final class MailboxClient implements MailboxApi, Task {
    /** The mailbox board can be slow to accept a connection while it serves another client or writes the SD card. */
    public static final int REQUEST_TIMEOUT_MILLIS = 30000;
    /**
     * How long a probe waits for one address. The first connection to a host on the network needs an ARP lookup and a
     * TCP handshake, which with both boards' Wi-Fi power saving took 619 ms to the R4 (400 ms missed it). An absent
     * address usually fails sooner than this, once its ARP lookup does.
     */
    private static final int PROBE_TIMEOUT_MILLIS = 2000;
    private static final String SERVICE_NAME = "Juno Mailbox";
    /** One byte longer than {@link #SERVICE_NAME}, so a longer "info" value is told apart from it. */
    private static final int INFO_CAPACITY = 13;
    private static final int HTTP_PORT = 80;
    private static final int REFRESH_MILLIS = 5 * 60 * 1000;
    private static final int RETRY_MILLIS = 5 * 1000;
    private static final int RESPONSE_CAPACITY = 1536;
    private static final int HEADERS_CAPACITY = 256;
    private static final int PATH_CAPACITY = 256;
    private static final int UUID_LENGTH = 36;

    private static final String WRONG_KEY = "Wrong API key; check Settings";

    private final MailboxModel model;
    private final MailboxConnection connection;
    private final MailboxKey key;
    private final Clock clock;
    private final byte[] path = new byte[PATH_CAPACITY];
    private final byte[] noHeaders = new byte[1];
    private final MessageRequest request = new MessageRequest();
    private final byte[] response = new byte[RESPONSE_CAPACITY];
    private final byte[] headers = new byte[HEADERS_CAPACITY];
    private final int[] statusAndHeadersLength = new int[2];
    private final byte[] info = new byte[INFO_CAPACITY];
    private String host;
    private int nextRefresh;
    private boolean refreshNow;

    /** A client with no mailbox yet; the Settings view supplies the host with {@link #host(String)}. */
    public MailboxClient(MailboxModel model, MailboxConnection connection, MailboxKey key) {
        this(model, connection, key, ArduinoUno.getClock());
    }

    MailboxClient(MailboxModel model, MailboxConnection connection, MailboxKey key, Clock clock) {
        this.model = model;
        this.connection = connection;
        this.key = key;
        this.clock = clock;
        refreshNow = true;
    }

    @Override
    public void host(String value) {
        host = value;
        requestRefresh();
    }

    @Override
    public void requestRefresh() {
        refreshNow = true;
    }

    /** Follows the Wi-Fi join first; refreshes wait for it, so a refresh asked for meanwhile runs once it is up. */
    @Override
    public void step() {
        connection.step();
        if (!connection.isConnected()) {
            boolean configured = connection.state() != MailboxConnection.NO_SETTINGS;
            model.status(configured ? "Waiting for WiFi" : "No WiFi settings on SD");
            return;
        }
        int now = clock.millis();
        if (refreshNow || now - nextRefresh >= 0) {
            refreshNow = false;
            // A failed refresh leaves the inbox empty, so it is retried soon rather than at the next periodic refresh.
            boolean refreshed = refresh();
            nextRefresh = clock.millis() + (refreshed ? REFRESH_MILLIS : RETRY_MILLIS);
        }
    }

    /** Reads the list and then every message so the inbox can display titles immediately. */
    public boolean refresh() {
        if (host == null || host.length() == 0) {
            model.status("Set mailbox IP in Settings");
            return false;
        }
        Serial.print("Mailbox client: GET host [");
        Serial.print(host);
        Serial.println("]");
        int listLength = getPath("/api/v1/mailbox/", 16);
        Serial.print("Mailbox client: list status ");
        Serial.print(statusAndHeadersLength[0]);
        Serial.print(" bytes ");
        Serial.println(listLength);
        // Retrying cannot fix a wrong key, so this waits for the periodic refresh; changing the key refreshes at once.
        if (listLength >= 0 && statusAndHeadersLength[0] == HttpStatus.UNAUTHORIZED) {
            Serial.println("Mailbox client: API key rejected");
            model.clear();
            model.status(WRONG_KEY);
            model.finishRefresh();
            return true;
        }
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

    @Override
    public boolean probe(String candidate) {
        int pathLength = copyPath("/actuator/info");
        int started = clock.millis();
        HttpClient.setTimeout(PROBE_TIMEOUT_MILLIS);
        int length = HttpClient.requestHttp(candidate, HTTP_PORT, "GET", path, pathLength,
                noHeaders, 0, null, -1, response, response.length,
                headers, headers.length, statusAndHeadersLength);
        HttpClient.setTimeout(REQUEST_TIMEOUT_MILLIS);
        Serial.print("Mailbox client: probe result ");
        Serial.print(length);
        Serial.print(" status ");
        Serial.print(statusAndHeadersLength[0]);
        Serial.print(" ms ");
        Serial.println(clock.millis() - started);
        if (length < 0 || statusAndHeadersLength[0] != HttpStatus.OK) {
            return false;
        }
        int infoLength = Json.getString(response, length, "info", info, info.length);
        if (infoLength != SERVICE_NAME.length()) {
            return false;
        }
        for (int index = 0; index < infoLength; index++) {
            if (info[index] != SERVICE_NAME.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    /** Also refreshes the inbox once the board confirms the delete. */
    @Override
    public boolean delete(byte[] uuid, int uuidLength) {
        if (host == null || host.length() == 0) {
            model.status("Set mailbox IP in Settings");
            return false;
        }
        int pathLength = copyMessagePath(uuid, uuidLength);
        Serial.println("Mailbox client: DELETE message");
        int responseLength = HttpClient.requestHttp(host, HTTP_PORT, "DELETE", path, pathLength,
                key.header(), key.headerLength(), null, -1, response, response.length,
                headers, headers.length, statusAndHeadersLength);
        Serial.print("Mailbox client: delete status ");
        Serial.println(statusAndHeadersLength[0]);
        boolean deleted = responseLength >= 0 && statusAndHeadersLength[0] == HttpStatus.NO_CONTENT;
        model.status(deleted ? "Message deleted" : failure(responseLength, "Delete failed"));
        if (deleted) {
            requestRefresh();
        }
        return deleted;
    }

    @Override
    public boolean send(char[] title, int titleLength, char[] body, int bodyLength) {
        if (host == null || host.length() == 0) {
            model.status("Set mailbox IP in Settings");
            return false;
        }
        int requestLength = request.encode(title, titleLength, body, bodyLength);
        if (requestLength < 0) {
            model.status("Message is too large");
            return false;
        }
        int pathLength = copyPath("/api/v1/mailbox/");
        int responseLength = HttpClient.requestHttp(host, HTTP_PORT, "POST", path, pathLength,
                key.header(), key.headerLength(), request.body(), requestLength, response, response.length,
                headers, headers.length, statusAndHeadersLength);
        boolean sent = responseLength >= 0 && statusAndHeadersLength[0] == HttpStatus.CREATED;
        model.status(sent ? "Message sent" : failure(responseLength, "Send failed; retrying"));
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
                key.header(), key.headerLength(), null, -1, response, response.length,
                headers, headers.length, statusAndHeadersLength);
    }

    /** The status to show for a failed request: the board's 401 means the key, anything else {@code otherwise}. */
    private String failure(int responseLength, String otherwise) {
        return responseLength >= 0 && statusAndHeadersLength[0] == HttpStatus.UNAUTHORIZED ? WRONG_KEY : otherwise;
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
}

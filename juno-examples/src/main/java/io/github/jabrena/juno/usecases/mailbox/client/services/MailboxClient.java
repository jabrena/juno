package io.github.jabrena.juno.usecases.mailbox.client.services;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.mailbox.client.commons.MailboxModel;
import io.github.jabrena.juno.util.mvc.Task;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.net.ssl.SSLContext;

/**
 * Retrying HTTPS client for the mailbox API, with the JDK's {@code java.net.http} client. Every mailbox request
 * carries the {@link MailboxKey}'s header; only the probe of {@code /actuator/info}, which the board leaves open, goes
 * without it. The client trusts the mailbox's own CA, given as an {@link SSLContext}.
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
    private static final String MAILBOX_PATH = "/api/v1/mailbox/";
    private static final int MINUTE_MILLIS = 60 * 1000;
    private static final int RETRY_MILLIS = 5 * 1000;
    private static final int UUID_LENGTH = 36;

    private static final String WRONG_KEY = "Wrong API key; check Settings";
    /** {@link #readList()} failed; the periodic refresh retries soon. */
    private static final int FAILED = -1;
    /** {@link #readList()} was answered 401: the inbox shows the wrong key and retrying cannot help. */
    private static final int REJECTED = -2;

    private final MailboxModel model;
    private final MailboxConnection connection;
    private final MailboxKey key;
    private final int pollingMillis;
    private final Clock clock;
    private final HttpClient client;
    private final MessageRequest request = new MessageRequest();
    private String host;
    /** The status of the last request, or 0 when it got no response. */
    private int status;
    private int nextRefresh;
    private boolean refreshNow;
    private boolean foregroundRefresh;

    /**
     * A client with no mailbox yet; the Settings view supplies the host with {@link #host(String)}. {@code trust} is
     * the mailbox CA's context, or {@code null} when it could not be loaded, which leaves every request to fail.
     */
    public MailboxClient(MailboxModel model, MailboxConnection connection, MailboxKey key, MailboxPolling polling,
            SSLContext trust) {
        this.model = model;
        this.connection = connection;
        this.key = key;
        this.pollingMillis = polling.minutes() * MINUTE_MILLIS;
        this.clock = MCU.getClock();
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_MILLIS));
        if (trust != null) {
            builder.sslContext(trust);
        }
        this.client = builder.build();
        refreshNow = true;
    }

    @Override
    public void host(String value) {
        host = value;
    }

    @Override
    public void beginRefresh() {
        foregroundRefresh = true;
        refreshNow = false;
    }

    @Override
    public void requestRefresh() {
        foregroundRefresh = false;
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
        if (foregroundRefresh) {
            return;
        }
        int now = clock.millis();
        if (refreshNow || now - nextRefresh >= 0) {
            refreshNow = false;
            // A failed refresh leaves the inbox empty, so it is retried soon rather than at the next periodic refresh.
            boolean refreshed = refresh();
            nextRefresh = clock.millis() + (refreshed ? pollingMillis : RETRY_MILLIS);
        }
    }

    /** Reads the list and then every message so the inbox can display titles immediately. */
    public boolean refresh() {
        int count = readList();
        if (count == REJECTED) {
            return true;
        }
        if (count < 0) {
            return false;
        }
        for (int index = 0; index < count; index++) {
            if (!readMessage(index)) {
                return false;
            }
        }
        finishRefresh(count);
        return true;
    }

    @Override
    public int readList() {
        if (host == null || host.length() == 0) {
            model.status("Set mailbox IP in Settings");
            return FAILED;
        }
        Serial.print("Mailbox client: GET host [");
        Serial.print(host);
        Serial.println("]");
        String list = send(withKey(MAILBOX_PATH).GET().build());
        Serial.print("Mailbox client: list status ");
        Serial.print(status);
        Serial.print(" bytes ");
        Serial.println(list == null ? -1 : list.length());
        // Retrying cannot fix a wrong key, so this waits for the periodic refresh; changing the key refreshes at once.
        if (status == HttpStatus.UNAUTHORIZED) {
            Serial.println("Mailbox client: API key rejected");
            model.clear();
            model.status(WRONG_KEY);
            model.finishRefresh();
            return REJECTED;
        }
        if (list == null || status != HttpStatus.OK) {
            Serial.println("Mailbox client: list request failed");
            model.status("Mailbox unavailable; retrying");
            return FAILED;
        }
        int count = Json.arraySize(list, "");
        Serial.print("Mailbox client: list count ");
        Serial.println(count);
        if (count < 0 || count > MailboxModel.MAX_MESSAGES) {
            Serial.println("Mailbox client: invalid list response");
            model.status("Invalid mailbox response");
            return FAILED;
        }
        model.clear();
        for (int index = 0; index < count; index++) {
            int uuidLength = Json.getString(list, "[" + index + "].uuid", model.uuid(index), UUID_LENGTH);
            if (uuidLength != UUID_LENGTH) {
                Serial.print("Mailbox client: invalid UUID length ");
                Serial.println(uuidLength);
                model.status("Invalid message UUID; retrying");
                return FAILED;
            }
        }
        return count;
    }

    @Override
    public void finishRefresh(int count) {
        foregroundRefresh = false;
        nextRefresh = clock.millis() + pollingMillis;
        model.status(count == 0 ? "Mailbox is empty" : "Updated just now");
        model.finishRefresh();
    }

    @Override
    public boolean readMessage(int index) {
        byte[] uuid = model.uuid(index);
        String message = send(withKey(messagePath(uuid, UUID_LENGTH)).GET().build());
        Serial.print("Mailbox client: detail status ");
        Serial.print(status);
        Serial.print(" bytes ");
        Serial.println(message == null ? -1 : message.length());
        if (message == null || status != HttpStatus.OK) {
            Serial.print("Mailbox client: detail request failed for index ");
            Serial.println(index);
            model.status("Message read failed; retrying");
            return false;
        }
        int titleLength = Json.getString(message, "title", model.title(index), MailboxModel.MAX_TITLE_BYTES);
        int messageBodyLength = Json.getString(message, "body", model.body(index), MailboxModel.MAX_BODY_BYTES);
        model.add(uuid, UUID_LENGTH, titleLength, messageBodyLength);
        return true;
    }

    @Override
    public boolean probe(String candidate) {
        int started = clock.millis();
        String info = send(HttpRequest.newBuilder(URI.create("https://" + candidate + "/actuator/info"))
                .timeout(Duration.ofMillis(PROBE_TIMEOUT_MILLIS)).GET().build());
        Serial.print("Mailbox client: probe status ");
        Serial.print(status);
        Serial.print(" ms ");
        Serial.println(clock.millis() - started);
        return info != null && status == HttpStatus.OK && SERVICE_NAME.equals(Json.getString(info, "info"));
    }

    @Override
    public boolean delete(byte[] uuid, int uuidLength) {
        if (host == null || host.length() == 0) {
            model.status("Set mailbox IP in Settings");
            return false;
        }
        Serial.println("Mailbox client: DELETE message");
        String reply = send(withKey(messagePath(uuid, uuidLength)).DELETE().build());
        Serial.print("Mailbox client: delete status ");
        Serial.println(status);
        boolean deleted = reply != null && status == HttpStatus.NO_CONTENT;
        model.status(deleted ? "Message deleted" : failure("Delete failed"));
        return deleted;
    }

    @Override
    public boolean send(char[] title, int titleLength, char[] body, int bodyLength) {
        if (host == null || host.length() == 0) {
            model.status("Set mailbox IP in Settings");
            return false;
        }
        String json = request.encode(title, titleLength, body, bodyLength);
        if (json == null) {
            model.status("Message is too large");
            return false;
        }
        String reply = send(withKey(MAILBOX_PATH).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
        boolean sent = reply != null && status == HttpStatus.CREATED;
        model.status(sent ? "Message sent" : failure("Send failed; check IP"));
        return sent;
    }

    /** A request to {@code path} on the mailbox, with the key header. */
    private HttpRequest.Builder withKey(String path) {
        return HttpRequest.newBuilder(URI.create("https://" + host + path)).header(MailboxKey.HEADER, key.value());
    }

    /** Sends {@code request} and returns its body, or {@code null} when no response came; {@link #status} follows. */
    private String send(HttpRequest request) {
        try {
            HttpResponse<String> reply = client.send(request, HttpResponse.BodyHandlers.ofString());
            status = reply.statusCode();
            return reply.body();
        } catch (IOException | InterruptedException e) {
            // The board never interrupts a request; either way there is no response.
            status = 0;
            return null;
        }
    }

    /** The status to show for a failed request: the board's 401 means the key, anything else {@code otherwise}. */
    private String failure(String otherwise) {
        return status == HttpStatus.UNAUTHORIZED ? WRONG_KEY : otherwise;
    }

    /** {@code /api/v1/mailbox/<uuid>}. */
    private static String messagePath(byte[] uuid, int uuidLength) {
        StringBuilder path = new StringBuilder(MAILBOX_PATH.length() + uuidLength);
        path.append(MAILBOX_PATH);
        for (int index = 0; index < uuidLength; index++) {
            path.append((char) uuid[index]);
        }
        return path.toString();
    }
}

package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

/** The mailbox model: owns the bounded index and all SD-card persistence. */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class MailboxModel {
    private static final String SD_MAILBOX_DIRECTORY = "/mailbox";
    private static final int MAX_MESSAGES = 4;
    private static final int DIRECTORY_ENTRY_CAPACITY = 48;
    /** The longest title, in UTF-8 bytes, a message may carry. */
    static final int MAX_TITLE_BYTES = 64;
    /** The longest body, in UTF-8 bytes, a message may carry. */
    static final int MAX_BODY_BYTES = 512;
    /** The largest POST body accepted: both fields fully escaped, plus a uuid, createAt and the JSON around them. */
    static final int MAX_REQUEST_BYTES = 2 * (MAX_TITLE_BYTES + MAX_BODY_BYTES) + 128;
    private static final int CREATE_AT_CAPACITY = 32;
    private static final int UUID_CAPACITY = 40;
    /**
     * The largest stored file, and so the largest message response: every text byte escaped, plus the uuid and the
     * JSON around them. A board {@code StringBuilder} drops what exceeds its capacity, so responses are sized by it.
     */
    static final int STORED_MESSAGE_CAPACITY = 2 * (MAX_TITLE_BYTES + MAX_BODY_BYTES + CREATE_AT_CAPACITY) + 128;

    private final SdCard sdCard;
    private final MailboxMessage[] messages = new MailboxMessage[MAX_MESSAGES];
    private final byte[] directoryEntry = new byte[DIRECTORY_ENTRY_CAPACITY];
    private final byte[] storedMessage = new byte[STORED_MESSAGE_CAPACITY];
    private final byte[] decodedUuid = new byte[UUID_CAPACITY];
    // One text buffer per mailbox slot, aligned with messages[]; each is one byte over its limit, so a value that
    // does not fit is detected instead of silently cut.
    private final byte[][] titles = new byte[MAX_MESSAGES][MAX_TITLE_BYTES + 1];
    private final byte[][] bodies = new byte[MAX_MESSAGES][MAX_BODY_BYTES + 1];
    private final byte[][] createAts = new byte[MAX_MESSAGES][CREATE_AT_CAPACITY];
    private int messageCount;
    private boolean storageReady;
    private volatile boolean restoreComplete;
    private String errorMessage;

    public MailboxModel(SdCard sdCard) {
        this.sdCard = sdCard;
    }

    void begin() {
        beginStorage();
        restore();
    }

    void beginStorage() {
        boolean cardReady = sdCard.begin();
        Serial.println(cardReady ? "SD begin ok" : "SD begin failed");
        boolean directoryReady = cardReady && sdCard.createDirectory(SD_MAILBOX_DIRECTORY);
        Serial.println(directoryReady ? "SD directory ok" : "SD directory failed");
        storageReady = directoryReady;
    }

    void restore() {
        if (storageReady) {
            restoreMessages();
            restoreComplete = true;
        }
    }

    boolean storageReady() {
        return storageReady;
    }

    boolean restoreComplete() {
        return restoreComplete;
    }

    int messageCount() {
        return messageCount;
    }

    UUID messageUuid(int index) {
        return messages[index].uuid();
    }

    String errorMessage() {
        return errorMessage;
    }

    int createMessage(byte[] requestBody, int bodyLength) {
        if (!storageReady) {
            return fail(HttpStatus.SERVICE_UNAVAILABLE, "Mailbox SD card is unavailable");
        }
        if (messageCount == MAX_MESSAGES) {
            return fail(HttpStatus.CONFLICT, "Mailbox is full");
        }
        if (Json.type(requestBody, bodyLength, "title") != Json.TYPE_STRING
                || Json.type(requestBody, bodyLength, "body") != Json.TYPE_STRING) {
            return fail(HttpStatus.BAD_REQUEST, "title and body must be JSON strings");
        }
        int uuidType = Json.type(requestBody, bodyLength, "uuid");
        int createAtType = Json.type(requestBody, bodyLength, "createAt");
        if (!isOptionalStringType(uuidType) || !isOptionalStringType(createAtType)) {
            return fail(HttpStatus.BAD_REQUEST, "uuid and createAt must be JSON strings");
        }

        // Fields are decoded straight into the free slot's buffers; they only count once messageCount moves on.
        byte[] title = titles[messageCount];
        byte[] body = bodies[messageCount];
        byte[] createAt = createAts[messageCount];
        int titleLength = Json.getString(requestBody, bodyLength, "title", title, title.length);
        int bodyTextLength = Json.getString(requestBody, bodyLength, "body", body, body.length);
        if (titleLength > MAX_TITLE_BYTES || bodyTextLength > MAX_BODY_BYTES) {
            return fail(HttpStatus.CONTENT_TOO_LARGE, "title or body is too long");
        }
        UUID uuid = UUID.randomUUID();
        if (uuidType == Json.TYPE_STRING) {
            uuid = uuidFromBytes(decodedUuid,
                    Json.getString(requestBody, bodyLength, "uuid", decodedUuid, decodedUuid.length));
            if (uuid == null) {
                return fail(HttpStatus.BAD_REQUEST, "uuid must be a valid UUID");
            }
        }
        int createAtLength = createAtType == Json.TYPE_STRING
                ? Json.getString(requestBody, bodyLength, "createAt", createAt, createAt.length)
                : copyText(LocalDateTime.now().toString(), createAt);
        if (containsMessage(uuid)) {
            return fail(HttpStatus.CONFLICT, "A mailbox message with this uuid already exists");
        }

        MailboxMessage message = new MailboxMessage(uuid, new MailboxText(title, titleLength),
                new MailboxText(body, bodyTextLength), new MailboxText(createAt, createAtLength), mailboxDate());
        if (!sdCard.createDirectory(mailboxDirectory(message.directoryDate())) || !storeMessage(message)) {
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save mailbox message");
        }
        messages[messageCount] = message;
        messageCount = messageCount + 1;
        return HttpStatus.CREATED;
    }

    int findMessage(String path, String mailboxPath) {
        if (path.length() <= mailboxPath.length()) {
            return -1;
        }
        for (int i = 0; i < mailboxPath.length(); i++) {
            if (path.charAt(i) != mailboxPath.charAt(i)) {
                return -1;
            }
        }
        int uuidLength = path.length() - mailboxPath.length();
        for (int message = 0; message < messageCount; message++) {
            String uuid = messages[message].uuid().toString();
            if (uuid.length() != uuidLength) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < uuidLength; i++) {
                if (uuid.charAt(i) != path.charAt(mailboxPath.length() + i)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return message;
            }
        }
        return -1;
    }

    boolean appendStoredMessage(StringBuilder response, int index) {
        MailboxMessage message = messages[index];
        InputStream file = sdCard.open(mailboxDirectory(message.directoryDate()), message.uuid().toString());
        if (file == null) {
            return false;
        }
        try {
            while (true) {
                int count = file.read(storedMessage, 0, storedMessage.length);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    continue;
                }
                for (int bufferIndex = 0; bufferIndex < count; bufferIndex++) {
                    response.append((char) (storedMessage[bufferIndex] & 0xff));
                }
            }
            file.close();
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    boolean deleteMessage(int index) {
        MailboxMessage message = messages[index];
        if (!sdCard.remove(mailboxDirectory(message.directoryDate()), message.uuid().toString())) {
            return false;
        }
        byte[] freeTitle = titles[index];
        byte[] freeBody = bodies[index];
        byte[] freeCreateAt = createAts[index];
        for (int i = index; i < messageCount - 1; i++) {
            messages[i] = messages[i + 1];
            titles[i] = titles[i + 1];
            bodies[i] = bodies[i + 1];
            createAts[i] = createAts[i + 1];
        }
        int last = messageCount - 1;
        messages[last] = null;
        titles[last] = freeTitle;
        bodies[last] = freeBody;
        createAts[last] = freeCreateAt;
        messageCount = last;
        return true;
    }

    private int fail(int status, String message) {
        errorMessage = message;
        return status;
    }

    private long mailboxDate() {
        LocalDateTime now = LocalDateTime.now();
        return now.getYear() * 10000000000L + now.getMonthValue() * 100000000L
                + now.getDayOfMonth() * 1000000L + now.getHour() * 10000L + now.getMinute() * 100L
                + now.getSecond();
    }

    private String mailboxDirectory(long date) {
        StringBuilder directory = new StringBuilder(24);
        directory.append(SD_MAILBOX_DIRECTORY).append('/');
        int width = date < 10000000000L ? 8 : date < 1000000000000L ? 12 : 14;
        appendNumber(directory, date, width);
        return directory.toString();
    }

    private void appendNumber(StringBuilder target, long value, int width) {
        long divisor = 1L;
        for (int i = 1; i < width; i++) {
            divisor *= 10L;
        }
        for (int i = 0; i < width; i++) {
            target.append((char) ('0' + value / divisor % 10L));
            divisor /= 10L;
        }
    }

    private boolean storeMessage(MailboxMessage message) {
        long directoryDate = message.directoryDate();
        String uuid = message.uuid().toString();
        int uuidLength = copyText(uuid, decodedUuid);
        sdCard.remove(mailboxDirectory(directoryDate), uuid);
        return appendStoredText(directoryDate, uuid, "{\"uuid\":")
                && appendStoredJsonString(directoryDate, uuid, new MailboxText(decodedUuid, uuidLength))
                && appendStoredText(directoryDate, uuid, ",\"title\":")
                && appendStoredJsonString(directoryDate, uuid, message.title())
                && appendStoredText(directoryDate, uuid, ",\"body\":")
                && appendStoredJsonString(directoryDate, uuid, message.body())
                && appendStoredText(directoryDate, uuid, ",\"createAt\":")
                && appendStoredJsonString(directoryDate, uuid, message.createAt())
                && appendStoredText(directoryDate, uuid, "}");
    }

    private int copyText(String text, byte[] target) {
        int length = Math.min(text.length(), target.length);
        for (int i = 0; i < length; i++) {
            target[i] = (byte) text.charAt(i);
        }
        return length;
    }

    private void restoreMessages() {
        int directoryIndex = 0;
        int directoriesScanned = 0;
        int directoriesRemoved = 0;
        while (true) {
            int directoryLength = sdCard.list(SD_MAILBOX_DIRECTORY, directoryIndex, directoryEntry, directoryEntry.length);
            if (directoryLength <= 0) {
                Serial.print("Mailbox restore scanned ");
                Serial.print(directoriesScanned);
                Serial.print(" removed ");
                Serial.println(directoriesRemoved);
                return;
            }
            directoriesScanned++;
            String directoryName = entryName(directoryLength);
            if (!isTimestampDirectory(directoryName)) {
                directoryIndex++;
                Thread.yield();
                continue;
            }
            long date = directoryDate(directoryName);
            if (!restoreDirectory(date)) {
                if (sdCard.removeDirectory(mailboxDirectory(date))) {
                    directoriesRemoved++;
                    if (directoriesRemoved % 10 == 0) {
                        Serial.print("Mailbox cleanup removed ");
                        Serial.println(directoriesRemoved);
                    }
                    Delay.millis(10);
                    continue;
                }
            }
            directoryIndex++;
            Thread.yield();
        }
    }

    private long directoryDate(String value) {
        long date = 0L;
        for (int i = 0; i < value.length(); i++) {
            date = date * 10 + value.charAt(i) - '0';
        }
        return date;
    }

    /** Restores supported files and reports whether the directory has any child at all. */
    private boolean restoreDirectory(long directoryDate) {
        for (int fileIndex = 0; ; fileIndex++) {
            int fileLength = sdCard.list(mailboxDirectory(directoryDate), fileIndex, directoryEntry, directoryEntry.length);
            if (fileLength <= 0) {
                return fileIndex > 0;
            }
            if (messageCount == MAX_MESSAGES) {
                return true;
            }
            if (!hasTextExtension(fileLength)) {
                Thread.yield();
                continue;
            }
            UUID uuid = entryUuid(fileLength);
            if (uuid == null || containsMessage(uuid)) {
                Thread.yield();
                continue;
            }
            Serial.print("Mailbox restore file ");
            Serial.println(fileIndex);
            MailboxMessage message = readMessage(directoryDate, uuid);
            if (message != null) {
                messages[messageCount] = message;
                messageCount++;
            }
            Thread.yield();
        }
    }

    private MailboxMessage readMessage(long directoryDate, UUID uuid) {
        InputStream file = sdCard.open(mailboxDirectory(directoryDate), uuid.toString());
        if (file == null) {
            return null;
        }
        int length = 0;
        try {
            while (true) {
                if (length == storedMessage.length) {
                    if (file.available() > 0) {
                        file.close();
                        return null;
                    }
                    break;
                }
                int count = file.read(storedMessage, length, storedMessage.length - length);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    file.close();
                    return null;
                }
                length += count;
            }
            file.close();
        } catch (IOException exception) {
            return null;
        }
        return parseMessage(length, uuid, directoryDate);
    }

    private MailboxMessage parseMessage(int length, UUID uuid, long directoryDate) {
        if (Json.type(storedMessage, length, "uuid") != Json.TYPE_STRING
                || Json.type(storedMessage, length, "title") != Json.TYPE_STRING
                || Json.type(storedMessage, length, "body") != Json.TYPE_STRING
                || Json.type(storedMessage, length, "createAt") != Json.TYPE_STRING) {
            return null;
        }
        byte[] title = titles[messageCount];
        byte[] body = bodies[messageCount];
        byte[] createAt = createAts[messageCount];
        int uuidLength = Json.getString(storedMessage, length, "uuid", decodedUuid, decodedUuid.length);
        int titleLength = Json.getString(storedMessage, length, "title", title, title.length);
        int bodyLength = Json.getString(storedMessage, length, "body", body, body.length);
        int createAtLength = Json.getString(storedMessage, length, "createAt", createAt, createAt.length);
        if (!uuid.equals(uuidFromBytes(decodedUuid, uuidLength))
                || titleLength > MAX_TITLE_BYTES || bodyLength > MAX_BODY_BYTES) {
            return null;
        }
        return new MailboxMessage(uuid, new MailboxText(title, titleLength), new MailboxText(body, bodyLength),
                new MailboxText(createAt, createAtLength), directoryDate);
    }

    private String entryName(int length) {
        StringBuilder name = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            name.append((char) (directoryEntry[i] & 0xff));
        }
        return name.toString();
    }

    private boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private boolean isTimestampDirectory(String value) {
        return value.length() == 14 && isDigits(value);
    }

    private boolean hasTextExtension(int length) {
        return length > 4 && directoryEntry[length - 4] == '.'
                && directoryEntry[length - 3] == 't'
                && directoryEntry[length - 2] == 'x'
                && directoryEntry[length - 1] == 't';
    }

    private UUID entryUuid(int length) {
        return length == 40 ? uuidFromBytes(directoryEntry, 36) : null;
    }

    private UUID uuidFromBytes(byte[] value, int length) {
        if (length != 36) {
            return null;
        }
        long most = 0L;
        long least = 0L;
        int digits = 0;
        for (int i = 0; i < 36; i++) {
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                if (value[i] != '-') {
                    return null;
                }
            } else if (!isHexDigit(value[i])) {
                return null;
            } else if (digits < 16) {
                most = (most << 4) | hexDigit(value[i]);
                digits++;
            } else {
                least = (least << 4) | hexDigit(value[i]);
                digits++;
            }
        }
        return digits == 32 ? new UUID(most, least) : null;
    }

    private boolean isHexDigit(byte value) {
        return value >= '0' && value <= '9' || value >= 'a' && value <= 'f' || value >= 'A' && value <= 'F';
    }

    private int hexDigit(byte value) {
        return value <= '9' ? value - '0' : value <= 'F' ? value - 'A' + 10 : value - 'a' + 10;
    }

    private boolean containsMessage(UUID uuid) {
        for (int i = 0; i < messageCount; i++) {
            if (messages[i].uuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    private boolean appendStoredText(long directoryDate, String fileStem, String text) {
        return sdCard.appendText(mailboxDirectory(directoryDate), fileStem, text);
    }

    private boolean appendStoredJsonString(long directoryDate, String fileStem, MailboxText text) {
        byte[] value = text.bytes();
        int length = text.length();
        if (!appendStoredText(directoryDate, fileStem, "\"")) {
            return false;
        }
        StringBuilder chunk = new StringBuilder(28);
        int chunkLength = 0;
        for (int i = 0; i < length; i++) {
            char character = (char) (value[i] & 0xff);
            if (character == '"' || character == '\\') {
                chunk.append('\\').append(character);
                chunkLength = chunkLength + 2;
            } else if (character == '\n') {
                chunk.append('\\').append('n');
                chunkLength = chunkLength + 2;
            } else if (character == '\r') {
                chunk.append('\\').append('r');
                chunkLength = chunkLength + 2;
            } else if (character == '\t') {
                chunk.append('\\').append('t');
                chunkLength = chunkLength + 2;
            } else {
                chunk.append(character);
                chunkLength = chunkLength + 1;
            }
            if (chunkLength >= 24) {
                if (!appendStoredText(directoryDate, fileStem, chunk.toString())) {
                    return false;
                }
                chunk = new StringBuilder(28);
                chunkLength = 0;
            }
        }
        return (chunkLength == 0 || appendStoredText(directoryDate, fileStem, chunk.toString()))
                && appendStoredText(directoryDate, fileStem, "\"");
    }

    private boolean isOptionalStringType(int type) {
        return type == Json.TYPE_MISSING || type == Json.TYPE_NULL || type == Json.TYPE_STRING;
    }
}

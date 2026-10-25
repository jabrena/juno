package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.usecases.zelda.common.ux.OptionListItems;

/** Bounded Cardputer-side mailbox state shared by the views and the HTTP client. */
public final class MailboxModel implements OptionListItems {
    public static final int MAX_MESSAGES = 4;
    public static final int UUID_LENGTH = 36;
    public static final int MAX_TITLE_BYTES = 64;
    public static final int MAX_BODY_BYTES = 512;
    public static final int MAX_COMPOSE_CHARS = 280;

    private final byte[][] uuids = new byte[MAX_MESSAGES][UUID_LENGTH];
    private final int[] uuidLengths = new int[MAX_MESSAGES];
    private final byte[][] titles = new byte[MAX_MESSAGES][MAX_TITLE_BYTES];
    private final int[] titleLengths = new int[MAX_MESSAGES];
    private final byte[][] bodies = new byte[MAX_MESSAGES][MAX_BODY_BYTES];
    private final int[] bodyLengths = new int[MAX_MESSAGES];
    private int messageCount;
    private int selectedIndex;
    private int revision;
    private String status = "Waiting for mailbox";

    public int messageCount() {
        return messageCount;
    }

    public int revision() {
        return revision;
    }

    public String status() {
        return status;
    }

    /** A new status counts as a change, so the inbox redraws it even when no message changed. */
    public void status(String message) {
        if (!message.equals(status)) {
            status = message;
            revision = revision + 1;
        }
    }

    public void clear() {
        messageCount = 0;
        revision = revision + 1;
    }

    public void add(byte[] uuid, int uuidLength, int titleLength, int bodyLength) {
        int index = messageCount;
        for (int position = 0; position < uuidLength; position++) {
            uuids[index][position] = uuid[position];
        }
        uuidLengths[index] = uuidLength;
        titleLengths[index] = titleLength;
        bodyLengths[index] = bodyLength;
        messageCount = messageCount + 1;
    }

    public byte[] uuid(int index) {
        return uuids[index];
    }

    public int uuidLength(int index) {
        return uuidLengths[index];
    }

    /** Returns the index of the message with this uuid, or {@code -1} when the inbox no longer holds it. */
    public int indexOf(byte[] uuid, int uuidLength) {
        for (int index = 0; index < messageCount; index++) {
            if (sameUuid(index, uuid, uuidLength)) {
                return index;
            }
        }
        return -1;
    }

    private boolean sameUuid(int index, byte[] uuid, int uuidLength) {
        if (uuidLengths[index] != uuidLength) {
            return false;
        }
        for (int position = 0; position < uuidLength; position++) {
            if (uuids[index][position] != uuid[position]) {
                return false;
            }
        }
        return true;
    }

    public byte[] title(int index) {
        return titles[index];
    }

    public void select(int index) {
        selectedIndex = index;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public byte[] body(int index) {
        return bodies[index];
    }

    public int bodyLength(int index) {
        return bodyLengths[index];
    }

    public void finishRefresh() {
        revision = revision + 1;
    }

    @Override
    public int count() {
        return messageCount;
    }

    @Override
    public int labelLength(int index) {
        return titleLengths[index];
    }

    @Override
    public char labelCharacter(int index, int position) {
        return (char) (titles[index][position] & 0xff);
    }

    @Override
    public int titleLength(int index) {
        return titleLengths[index];
    }

    @Override
    public char titleCharacter(int index, int position) {
        return (char) (titles[index][position] & 0xff);
    }
}

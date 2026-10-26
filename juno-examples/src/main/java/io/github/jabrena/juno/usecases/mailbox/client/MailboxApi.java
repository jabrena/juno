package io.github.jabrena.juno.usecases.mailbox.client;

/** What the views ask of the mailbox board; {@link MailboxClient} answers over HTTP, and a test can mock it. */
public interface MailboxApi {
    /** Uses the mailbox at {@code value} from now on and refreshes the inbox from it. */
    void host(String value);

    void requestRefresh();

    /** Asks {@code candidate} for {@code /actuator/info}; true when it answers as the Juno Mailbox. */
    boolean probe(String candidate);

    /** Deletes the message with this uuid; true when the board confirmed it. The inbox is not refreshed. */
    boolean delete(byte[] uuid, int uuidLength);

    /** Sends a new message; true when the board stored it. The inbox is not refreshed. */
    boolean send(char[] title, int titleLength, char[] body, int bodyLength);

    /**
     * The first step of a refresh, one request at a time: reads the list and empties the inbox for it. Returns how many
     * messages {@link #readMessage} should read, or a negative value when the list could not be read.
     */
    int readList();

    /** Reads the message at {@code index} of the list just read into the inbox; false when the request failed. */
    boolean readMessage(int index);

    /** Ends a refresh of {@code count} messages, so the inbox shows them. */
    void finishRefresh(int count);
}

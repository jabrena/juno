package io.github.jabrena.juno.usecases.mailbox.client;

/** What the views ask of the mailbox board; {@link MailboxClient} answers over HTTP, and a test can mock it. */
public interface MailboxApi {
    /** Uses the mailbox at {@code value} from now on and refreshes the inbox from it. */
    void host(String value);

    void requestRefresh();

    /** Asks {@code candidate} for {@code /actuator/info}; true when it answers as the Juno Mailbox. */
    boolean probe(String candidate);

    /** Deletes the message with this uuid; true when the board confirmed it. */
    boolean delete(byte[] uuid, int uuidLength);

    /** Sends a new message; true when the board stored it. */
    boolean send(char[] title, int titleLength, char[] body, int bodyLength);
}

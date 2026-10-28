package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/**
 * Sends or deletes a message and then refreshes the inbox, or only refreshes it, one HTTP request per frame, with a spinner that turns
 * between requests; then shows the inbox. Each request's time and the total from entering the view go to the serial
 * log. A failed send or delete stays here with the reason until Esc goes back to the view that asked for it.
 */
public final class ProgressView implements KeyboardView {
    private static final int PROGRESS_VIEW = 6;
    private static final int INBOX_VIEW = 1;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';

    private static final int SEND = 0;
    private static final int DELETE = 1;
    private static final int REFRESH = 2;

    private static final int REQUEST = 0;
    private static final int LIST = 1;
    private static final int MESSAGES = 2;
    private static final int DONE = 3;
    private static final int FAILED = 4;

    private static final int WIDTH = 240;
    private static final int HEADER_HEIGHT = 28;
    private static final int CONTENT_HEIGHT = 107 - HEADER_HEIGHT;
    private static final int TEXT_SIZE = 2;
    private static final int CHARACTER_WIDTH = 6 * TEXT_SIZE;
    private static final int SPINNER_X = 120;
    private static final int SPINNER_Y = 54;
    private static final int DOT_SIZE = 5;
    private static final int[] DOT_X = {0, 10, 14, 10, 0, -10, -14, -10};
    private static final int[] DOT_Y = {-14, -10, 0, 10, 14, 10, 0, -10};
    /** The head of the spinner and the two dots trailing it; the rest stay dim. */
    private static final int[] TAIL_COLORS = {Display.WHITE, 0xAD55, 0x632C};
    private static final int DIM_DOT = 0x2104;
    private static final int LABEL_Y = 80;
    private static final int LABEL_HEIGHT = 8 * TEXT_SIZE + 2;
    /** "Message n of n". */
    private static final int MESSAGE_LABEL_COLUMNS = 14;

    private final Navigator navigator;
    private final Display display;
    private final MailboxApi client;
    private final MailboxModel model;
    private final MailboxConnection connection;
    private final Footer footer;
    private final Clock clock;
    private int operation;
    private char[] title = new char[0];
    private int titleLength;
    private char[] body = new char[0];
    private int bodyLength;
    private byte[] uuid = new byte[0];
    private int uuidLength;
    private int phase;
    private int count;
    private int index;
    private int head;
    private int enteredAt;

    public ProgressView(Navigator navigator, Display display, MailboxApi client, MailboxModel model,
            MailboxConnection connection, Footer footer) {
        this(navigator, display, client, model, connection, footer, ArduinoUno.getClock());
    }

    ProgressView(Navigator navigator, Display display, MailboxApi client, MailboxModel model, Footer footer,
            Clock clock) {
        this(navigator, display, client, model, null, footer, clock);
    }

    ProgressView(Navigator navigator, Display display, MailboxApi client, MailboxModel model,
            MailboxConnection connection, Footer footer, Clock clock) {
        this.navigator = navigator;
        this.display = display;
        this.client = client;
        this.model = model;
        this.connection = connection;
        this.footer = footer;
        this.clock = clock;
    }

    /** Shows this view to send the message; the caller keeps the buffers unchanged until the inbox is shown. */
    public void send(char[] messageTitle, int messageTitleLength, char[] messageBody, int messageBodyLength) {
        client.beginRefresh();
        operation = SEND;
        title = messageTitle;
        titleLength = messageTitleLength;
        body = messageBody;
        bodyLength = messageBodyLength;
        navigator.show(PROGRESS_VIEW);
    }

    /** Shows this view to delete the message with this uuid; the caller keeps the buffer unchanged meanwhile. */
    public void delete(byte[] messageUuid, int messageUuidLength) {
        client.beginRefresh();
        operation = DELETE;
        uuid = messageUuid;
        uuidLength = messageUuidLength;
        navigator.show(PROGRESS_VIEW);
    }

    /** Shows this view to read the inbox again. */
    public void refresh() {
        client.beginRefresh();
        operation = REFRESH;
        navigator.show(PROGRESS_VIEW);
    }

    @Override
    public void enter() {
        enteredAt = clock.millis();
        phase = operation == REFRESH ? LIST : REQUEST;
        head = 0;
        display.fillScreen(Display.BLACK);
        display.fillRect(0, 0, WIDTH, HEADER_HEIGHT, Display.BLUE);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setTextSize(TEXT_SIZE);
        display.setCursor(8, 6);
        display.print(header());
        footer.draw("Please wait");
        for (int dot = 0; dot < DOT_X.length; dot++) {
            drawDot(dot, DIM_DOT);
        }
        drawSpinner();
        drawLabel(connection != null && !connection.isConnected() ? "Waiting for WiFi" : firstLabel());
    }

    private String header() {
        if (operation == SEND) {
            return "SENDING";
        }
        return operation == DELETE ? "DELETING" : "REFRESHING";
    }

    private String firstLabel() {
        if (operation == SEND) {
            return "Posting message";
        }
        return operation == DELETE ? "Deleting message" : "Reading inbox";
    }

    /** One request per frame, so the spinner turns and the screen says what comes next between them. */
    @Override
    public void render() {
        if (phase == DONE || phase == FAILED) {
            return;
        }
        if (connection != null && !connection.isConnected()) {
            drawLabel(connection.state() == MailboxConnection.NO_SETTINGS ? "No WiFi settings" : "Waiting for WiFi");
            head = (head + 1) % DOT_X.length;
            drawSpinner();
            return;
        }
        if (phase == REQUEST) {
            request();
        } else if (phase == LIST) {
            readList();
        } else {
            readMessage();
        }
        if (phase != FAILED) {
            head = (head + 1) % DOT_X.length;
            drawSpinner();
        }
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (phase == FAILED && (key == ESCAPE || key == CARDPUTER_ESCAPE)) {
            navigator.back();
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    private void request() {
        int started = clock.millis();
        boolean done = operation == SEND
                ? client.send(title, titleLength, body, bodyLength)
                : client.delete(uuid, uuidLength);
        log("request ms ", started);
        if (!done) {
            phase = FAILED;
            display.fillRect(0, HEADER_HEIGHT, WIDTH, CONTENT_HEIGHT, Display.BLACK);
            drawLabel(model.status());
            footer.draw("Esc: back");
            return;
        }
        phase = LIST;
        drawLabel("Reading inbox");
    }

    private void readList() {
        int started = clock.millis();
        count = client.readList();
        log("list ms ", started);
        if (count < 0) {
            // A message was sent or deleted all the same; the inbox shows why it is stale until a retry succeeds.
            client.requestRefresh();
            showInbox();
            return;
        }
        phase = MESSAGES;
        index = 0;
        nextMessage();
    }

    private void readMessage() {
        int started = clock.millis();
        boolean read = client.readMessage(index);
        log("message ms ", started);
        if (!read) {
            client.requestRefresh();
            showInbox();
            return;
        }
        index = index + 1;
        nextMessage();
    }

    /** Says which message is read next, or ends the refresh when they all are. */
    private void nextMessage() {
        if (index == count) {
            client.finishRefresh(count);
            showInbox();
            return;
        }
        drawLabel("Message ", MESSAGE_LABEL_COLUMNS);
        display.print((char) ('1' + index));
        display.print(" of ");
        display.print((char) ('0' + count));
    }

    private void showInbox() {
        phase = DONE;
        log("total ms ", enteredAt);
        navigator.show(INBOX_VIEW);
    }

    private void log(String step, int started) {
        if (operation == SEND) {
            Serial.print("Mailbox send: ");
        } else if (operation == DELETE) {
            Serial.print("Mailbox delete: ");
        } else {
            Serial.print("Mailbox refresh: ");
        }
        Serial.print(step);
        Serial.println(clock.millis() - started);
    }

    private void drawSpinner() {
        for (int trail = TAIL_COLORS.length; trail >= 0; trail--) {
            int dot = (head - trail + DOT_X.length) % DOT_X.length;
            drawDot(dot, trail == TAIL_COLORS.length ? DIM_DOT : TAIL_COLORS[trail]);
        }
    }

    private void drawDot(int dot, int color) {
        display.fillRect(SPINNER_X + DOT_X[dot] - DOT_SIZE / 2, SPINNER_Y + DOT_Y[dot] - DOT_SIZE / 2,
                DOT_SIZE, DOT_SIZE, color);
    }

    private void drawLabel(String text) {
        drawLabel(text, text.length());
    }

    /**
     * Clears the label row and prints {@code text} at the left of a centered line {@code columns} wide, so the caller
     * can print more after it; a text too long for large type is printed small.
     */
    private void drawLabel(String text, int columns) {
        display.fillRect(0, LABEL_Y, WIDTH, LABEL_HEIGHT, Display.BLACK);
        int size = columns * CHARACTER_WIDTH <= WIDTH ? TEXT_SIZE : 1;
        display.setTextSize(size);
        display.setTextColor(phase == FAILED ? Display.RED : Display.CYAN, Display.BLACK);
        display.setCursor((WIDTH - columns * 6 * size) / 2, LABEL_Y);
        display.print(text);
    }
}

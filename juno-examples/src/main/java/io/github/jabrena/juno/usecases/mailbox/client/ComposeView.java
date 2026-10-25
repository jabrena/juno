package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/** Tweet-sized mailbox composer: each field is typed on the row of its label, in large text. */
public final class ComposeView implements KeyboardView {
    private static final int INBOX_VIEW = 1;
    private static final int TITLE_CAPACITY = 64;
    private static final int BODY_CAPACITY = MailboxModel.MAX_COMPOSE_CHARS;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int HEADER_HEIGHT = 28;
    private static final int CONTENT_HEIGHT = 107 - HEADER_HEIGHT;
    private static final int TEXT_SIZE = 2;
    private static final int TEXT_LEFT = 4;
    private static final int CHARACTER_WIDTH = 6 * TEXT_SIZE;
    private static final int LINE_HEIGHT = 9 * TEXT_SIZE;
    private static final int COLUMNS = 18;
    private static final int TITLE_Y = 32;
    private static final int BODY_Y = 52;
    private static final int BODY_ROWS = 3;
    private static final String TITLE_LABEL = "Title:";
    private static final String BODY_LABEL = "Body:";
    private static final int TITLE_COLUMNS = COLUMNS - TITLE_LABEL.length() - 1;
    private static final int BODY_FIRST_COLUMNS = COLUMNS - BODY_LABEL.length() - 1;
    private static final char CURSOR = '_';
    private static final String LEGEND = "Ctrl+S: send | Ctrl+R: reset | Esc: back";

    private final Navigator navigator;
    private final Display display;
    private final Keyboard keyboard;
    private final MailboxClient client;
    private final Footer footer;
    private final char[] title = new char[TITLE_CAPACITY];
    private final char[] body = new char[BODY_CAPACITY];
    private int titleLength;
    private int bodyLength;
    private boolean bodyFocused;
    private boolean footerMessageShown;

    public ComposeView(
            Navigator navigator, Display display, Keyboard keyboard, MailboxClient client, Footer footer) {
        this.navigator = navigator;
        this.display = display;
        this.keyboard = keyboard;
        this.client = client;
        this.footer = footer;
    }

    @Override
    public void enter() {
        draw();
    }

    @Override
    public void render() {
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (keyboard.isControlPressed()) {
            if (key == 's' || key == 'S') {
                send();
            } else if (key == 'r' || key == 'R') {
                reset();
            }
            return;
        }
        if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            navigator.back();
        } else if (key == Keyboard.TAB || (key == Keyboard.ENTER && !bodyFocused)) {
            bodyFocused = !bodyFocused || key != Keyboard.TAB;
            drawFields();
        } else if (key == Keyboard.BACKSPACE) {
            erase();
        } else if (key == Keyboard.ENTER && bodyFocused) {
            append('\n');
        } else if (key >= ' ' && key <= '~') {
            append((char) key);
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    private void send() {
        if (titleLength == 0 || bodyLength == 0) {
            showFooterMessage("Title and body are required");
        } else if (client.send(title, titleLength, body, bodyLength)) {
            navigator.show(INBOX_VIEW);
        } else {
            showFooterMessage("Send failed; check IP");
        }
    }

    private void reset() {
        titleLength = 0;
        bodyLength = 0;
        bodyFocused = false;
        draw();
    }

    private void append(char character) {
        if (bodyFocused) {
            if (bodyLength < body.length) {
                body[bodyLength] = character;
                bodyLength = bodyLength + 1;
                drawFields();
            }
        } else if (titleLength < title.length) {
            title[titleLength] = character;
            titleLength = titleLength + 1;
            drawFields();
        }
    }

    private void erase() {
        if (bodyFocused && bodyLength > 0) {
            bodyLength = bodyLength - 1;
        } else if (!bodyFocused && titleLength > 0) {
            titleLength = titleLength - 1;
        }
        drawFields();
    }

    private void draw() {
        display.fillScreen(Display.BLACK);
        display.fillRect(0, 0, 240, HEADER_HEIGHT, Display.BLUE);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setTextSize(TEXT_SIZE);
        display.setCursor(8, 6);
        display.print("NEW MESSAGE");
        footer.draw(LEGEND);
        footerMessageShown = false;
        drawFields();
    }

    private void drawFields() {
        if (footerMessageShown) {
            footer.draw(LEGEND);
            footerMessageShown = false;
        }
        drawCounter();
        display.fillRect(0, HEADER_HEIGHT, 240, CONTENT_HEIGHT, Display.BLACK);
        display.setTextSize(TEXT_SIZE);
        drawTitle();
        drawBody();
    }

    /** Shows the focused field's length against its capacity at the right of the header. */
    private void drawCounter() {
        display.fillRect(160, 0, 80, HEADER_HEIGHT, Display.BLUE);
        display.setTextSize(1);
        display.setTextColor(Display.CYAN, Display.BLUE);
        display.setCursor(176, 10);
        printNumber(bodyFocused ? bodyLength : titleLength);
        display.print('/');
        printNumber(bodyFocused ? BODY_CAPACITY : TITLE_CAPACITY);
    }

    /** Draws the label and, on the same row, the end of the title that fits after it. */
    private void drawTitle() {
        display.setTextColor(bodyFocused ? Display.CYAN : Display.YELLOW, Display.BLACK);
        display.setCursor(TEXT_LEFT, TITLE_Y);
        display.print(TITLE_LABEL);
        display.setTextColor(Display.WHITE, Display.BLACK);
        display.setCursor(TEXT_LEFT + (TITLE_LABEL.length() + 1) * CHARACTER_WIDTH, TITLE_Y);
        int shown = bodyFocused ? titleLength : titleLength + 1;
        int start = shown > TITLE_COLUMNS ? shown - TITLE_COLUMNS : 0;
        for (int position = start; position < titleLength; position++) {
            display.print(title[position]);
        }
        if (!bodyFocused) {
            display.print(CURSOR);
        }
    }

    /**
     * Draws the body from the row of its label, wrapping onto full-width rows below it. When it is longer than the
     * visible rows, the last rows are shown so the cursor stays on screen.
     */
    private void drawBody() {
        int shown = bodyFocused ? bodyLength + 1 : bodyLength;
        int lineCount = walkBody(shown, Integer.MAX_VALUE);
        int firstLine = lineCount > BODY_ROWS ? lineCount - BODY_ROWS : 0;
        if (firstLine == 0) {
            display.setTextColor(bodyFocused ? Display.YELLOW : Display.CYAN, Display.BLACK);
            display.setCursor(TEXT_LEFT, BODY_Y);
            display.print(BODY_LABEL);
        }
        display.setTextColor(Display.WHITE, Display.BLACK);
        walkBody(shown, firstLine);
    }

    /**
     * Splits the first {@code shown} body characters into rows, drawing the rows from {@code firstLine} on, and
     * returns how many rows there are. The rows are walked twice instead of stored, to keep the arena free.
     */
    private int walkBody(int shown, int firstLine) {
        int line = 0;
        int column = 0;
        int capacity = BODY_FIRST_COLUMNS;
        startRow(line, firstLine);
        for (int position = 0; position < shown; position++) {
            boolean newline = position < bodyLength && body[position] == '\n';
            if (column == capacity || newline) {
                line = line + 1;
                column = 0;
                capacity = COLUMNS;
                startRow(line, firstLine);
            }
            if (!newline) {
                if (line >= firstLine) {
                    display.print(position == bodyLength ? CURSOR : body[position]);
                }
                column = column + 1;
            }
        }
        return line + 1;
    }

    private void startRow(int line, int firstLine) {
        if (line >= firstLine) {
            int column = line == 0 ? BODY_LABEL.length() + 1 : 0;
            display.setCursor(TEXT_LEFT + column * CHARACTER_WIDTH, BODY_Y + (line - firstLine) * LINE_HEIGHT);
        }
    }

    private void printNumber(int value) {
        if (value >= 100) {
            display.print((char) ('0' + value / 100));
            value = value % 100;
            display.print((char) ('0' + value / 10));
            display.print((char) ('0' + value % 10));
        } else if (value >= 10) {
            display.print((char) ('0' + value / 10));
            display.print((char) ('0' + value % 10));
        } else {
            display.print((char) ('0' + value));
        }
    }

    private void showFooterMessage(String message) {
        footer.draw(message);
        footerMessageShown = true;
    }
}

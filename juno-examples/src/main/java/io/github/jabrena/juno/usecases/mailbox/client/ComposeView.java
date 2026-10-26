package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/**
 * Tweet-sized mailbox composer: each field is typed on the row of its label, in large text. Fn with the up and down
 * arrows scrolls a body longer than its rows (see {@link ComposeBody}).
 */
public final class ComposeView implements KeyboardView {
    private static final int INBOX_VIEW = 1;
    private static final int TITLE_CAPACITY = 64;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int UP = ';';
    private static final int DOWN = '.';
    private static final int HEADER_HEIGHT = 28;
    private static final int CONTENT_HEIGHT = 107 - HEADER_HEIGHT;
    private static final int TEXT_SIZE = 2;
    private static final int TEXT_LEFT = 4;
    private static final int CHARACTER_WIDTH = 6 * TEXT_SIZE;
    private static final int COLUMNS = 18;
    private static final int TITLE_Y = 32;
    private static final String TITLE_LABEL = "Title:";
    private static final int TITLE_COLUMNS = COLUMNS - TITLE_LABEL.length() - 1;
    private static final char CURSOR = '_';
    private static final String LEGEND = "Esc | Ctrl+s | Ctrl+r";

    private final Navigator navigator;
    private final Display display;
    private final Keyboard keyboard;
    private final MailboxApi client;
    private final Footer footer;
    private final ComposeBody body;
    private final char[] title = new char[TITLE_CAPACITY];
    private int titleLength;
    private boolean bodyFocused;
    private boolean footerMessageShown;

    public ComposeView(
            Navigator navigator, Display display, Keyboard keyboard, MailboxApi client, Footer footer) {
        this.navigator = navigator;
        this.display = display;
        this.keyboard = keyboard;
        this.client = client;
        this.footer = footer;
        body = new ComposeBody(display);
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
        if (fnPressed && (key == UP || key == DOWN)) {
            if (body.scroll(key == UP ? 1 : -1, bodyFocused)) {
                drawFields();
            }
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
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
        if (titleLength == 0 || body.length() == 0) {
            showFooterMessage("Title and body are required");
        } else if (client.send(title, titleLength, body.text(), body.length())) {
            navigator.show(INBOX_VIEW);
        } else {
            showFooterMessage("Send failed; check IP");
        }
    }

    private void reset() {
        titleLength = 0;
        body.clear();
        bodyFocused = false;
        draw();
    }

    private void append(char character) {
        if (bodyFocused) {
            if (body.append(character)) {
                drawFields();
            }
        } else if (titleLength < title.length) {
            title[titleLength] = character;
            titleLength = titleLength + 1;
            drawFields();
        }
    }

    private void erase() {
        if (bodyFocused) {
            body.erase();
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
        body.draw(bodyFocused);
    }

    /** Shows the focused field's length against its capacity at the right of the header. */
    private void drawCounter() {
        display.fillRect(160, 0, 80, HEADER_HEIGHT, Display.BLUE);
        display.setTextSize(1);
        display.setTextColor(Display.CYAN, Display.BLUE);
        display.setCursor(176, 10);
        printNumber(bodyFocused ? body.length() : titleLength);
        display.print('/');
        printNumber(bodyFocused ? ComposeBody.capacity() : TITLE_CAPACITY);
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

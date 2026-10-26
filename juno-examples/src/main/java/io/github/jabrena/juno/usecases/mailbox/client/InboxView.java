package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/** The periodically refreshed inbox: open, and with Ctrl compose, refresh (in the {@link ProgressView}), and settings. */
public final class InboxView implements KeyboardView {
    private static final int DETAIL_VIEW = 2;
    private static final int COMPOSE_VIEW = 3;
    private static final int SETTINGS_VIEW = 0;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int UP = ';';
    private static final int DOWN = '.';
    private static final String LEGEND = "Enter | Ctrl+c | Ctrl+r | Ctrl+s";
    private static final int DISPLAY_WIDTH = 240;
    private static final int STATUS_TEXT_SIZE = 2;
    private static final int STATUS_CHAR_WIDTH = 6 * STATUS_TEXT_SIZE;
    private static final int STATUS_COLUMNS = DISPLAY_WIDTH / STATUS_CHAR_WIDTH;
    private static final int STATUS_Y = 60;
    private static final int STATUS_LINE_HEIGHT = 20;

    private final Navigator navigator;
    private final Display display;
    private final Keyboard keyboard;
    private final MailboxModel model;
    private final ProgressView progress;
    private final NumberedList messages;
    private final Footer footer;
    private int renderedRevision = -1;

    public InboxView(Navigator navigator, Display display, Keyboard keyboard, MailboxModel model,
            ProgressView progress, NumberedList messages, Footer footer) {
        this.navigator = navigator;
        this.display = display;
        this.keyboard = keyboard;
        this.model = model;
        this.progress = progress;
        this.messages = messages;
        this.footer = footer;
    }

    @Override
    public void enter() {
        renderedRevision = -1;
        footer.draw(LEGEND);
        renderMessages();
    }

    @Override
    public void render() {
        if (renderedRevision != model.revision()) {
            renderMessages();
        } else {
            messages.render();
        }
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (key == UP) {
            messages.move(-1);
        } else if (key == DOWN) {
            messages.move(1);
        } else if (key == Keyboard.ENTER && messages.hasSelection()) {
            model.select(messages.selectedIndex());
            navigator.show(DETAIL_VIEW);
        } else if (keyboard.isControlPressed()) {
            control(key);
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            navigator.show(SETTINGS_VIEW);
        }
    }

    private void control(int key) {
        if (key == 'c' || key == 'C') {
            navigator.show(COMPOSE_VIEW);
        } else if (key == 'r' || key == 'R') {
            progress.refresh();
        } else if (key == 's' || key == 'S') {
            navigator.show(SETTINGS_VIEW);
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    private void renderMessages() {
        renderedRevision = model.revision();
        messages.select(model.messageCount() == 0 ? -1
                : Math.min(messages.hasSelection() ? messages.selectedIndex() : 0, model.messageCount() - 1));
        footer.draw(LEGEND);
        drawEmptyStatus();
    }

    /** The status under "No messages", centered at text size 2 and wrapped on a space onto a second line. */
    private void drawEmptyStatus() {
        if (model.messageCount() > 0) {
            return;
        }
        String status = model.status();
        display.setTextSize(STATUS_TEXT_SIZE);
        display.setTextColor(Display.CYAN, Display.BLACK);
        int end = lineBreak(status);
        drawCentered(status, 0, end, STATUS_Y);
        int next = end < status.length() && status.charAt(end) == ' ' ? end + 1 : end;
        if (next < status.length()) {
            drawCentered(status, next, Math.min(status.length(), next + STATUS_COLUMNS), STATUS_Y + STATUS_LINE_HEIGHT);
        }
    }

    private int lineBreak(String text) {
        if (text.length() <= STATUS_COLUMNS) {
            return text.length();
        }
        for (int position = STATUS_COLUMNS; position > 0; position--) {
            if (text.charAt(position) == ' ') {
                return position;
            }
        }
        return STATUS_COLUMNS;
    }

    private void drawCentered(String text, int start, int end, int y) {
        display.setCursor((DISPLAY_WIDTH - (end - start) * STATUS_CHAR_WIDTH) / 2, y);
        for (int position = start; position < end; position++) {
            display.print(text.charAt(position));
        }
    }
}

package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.usecases.zelda.common.ux.ScrollingText;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/** Shows the selected message: its title in the header and its scrollable body below. */
public final class MessageView implements KeyboardView {
    private static final int COMPOSE_VIEW = 3;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int UP = ';';
    private static final int DOWN = '.';
    private static final int HEADER_HEIGHT = 28;
    private static final int HEADER_COLUMNS = 18;
    private static final String LEGEND = "Esc | Ctrl+c | Ctrl+d";

    private final Navigator navigator;
    private final Display display;
    private final Keyboard keyboard;
    private final MailboxModel model;
    private final MailboxClient client;
    private final ScrollingText body;
    private final Footer footer;
    private final byte[] uuid = new byte[MailboxModel.UUID_LENGTH];
    private int uuidLength;
    private int messageIndex = -1;
    private int renderedRevision;

    public MessageView(Navigator navigator, Display display, Keyboard keyboard, MailboxModel model,
            MailboxClient client, ScrollingText body, Footer footer) {
        this.navigator = navigator;
        this.display = display;
        this.keyboard = keyboard;
        this.model = model;
        this.client = client;
        this.body = body;
        this.footer = footer;
    }

    @Override
    public void enter() {
        messageIndex = model.selectedIndex() < model.messageCount() ? model.selectedIndex() : -1;
        uuidLength = 0;
        if (messageIndex >= 0) {
            uuidLength = model.uuidLength(messageIndex);
            for (int position = 0; position < uuidLength; position++) {
                uuid[position] = model.uuid(messageIndex)[position];
            }
        }
        renderedRevision = model.revision();
        draw();
    }

    /** A background refresh can reorder or drop messages; follow this one by uuid, or leave when it is gone. */
    @Override
    public void render() {
        if (renderedRevision == model.revision() || messageIndex < 0) {
            return;
        }
        renderedRevision = model.revision();
        int index = model.indexOf(uuid, uuidLength);
        if (index < 0) {
            messageIndex = -1;
            navigator.back();
        } else if (index != messageIndex) {
            messageIndex = index;
            draw();
        }
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (keyboard.isControlPressed()) {
            if (key == 'c' || key == 'C') {
                navigator.show(COMPOSE_VIEW);
            } else if ((key == 'd' || key == 'D') && messageIndex >= 0) {
                delete();
            }
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            navigator.back();
        } else if (key == UP) {
            body.scroll(-1);
        } else if (key == DOWN) {
            body.scroll(1);
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    private void delete() {
        footer.draw("Deleting...");
        if (client.delete(uuid, uuidLength)) {
            navigator.back();
        } else {
            footer.draw("Delete failed | Esc");
        }
    }

    private void draw() {
        display.fillScreen(Display.BLACK);
        display.fillRect(0, 0, 240, HEADER_HEIGHT, Display.BLUE);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setTextSize(2);
        display.setCursor(8, 6);
        if (messageIndex < 0) {
            display.print("MESSAGE");
            footer.draw("Esc | Ctrl+c");
            return;
        }
        drawTitle();
        body.show(model.body(messageIndex), model.bodyLength(messageIndex));
        footer.draw(LEGEND);
    }

    private void drawTitle() {
        int length = model.titleLength(messageIndex);
        boolean truncated = length > HEADER_COLUMNS;
        int shown = truncated ? HEADER_COLUMNS - 2 : length;
        for (int position = 0; position < shown; position++) {
            display.print(model.titleCharacter(messageIndex, position));
        }
        if (truncated) {
            display.print("..");
        }
    }
}

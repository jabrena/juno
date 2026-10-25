package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/** The periodically refreshed inbox: open, compose, refresh, and settings. */
public final class InboxView implements KeyboardView {
    private static final int DETAIL_VIEW = 2;
    private static final int COMPOSE_VIEW = 3;
    private static final int SETTINGS_VIEW = 0;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int UP = ';';
    private static final int DOWN = '.';
    private static final String LEGEND = "Enter | C | R | S";

    private final Navigator navigator;
    private final Display display;
    private final MailboxModel model;
    private final MailboxClient client;
    private final NumberedList messages;
    private final Footer footer;
    private int renderedRevision = -1;

    public InboxView(Navigator navigator, Display display, MailboxModel model, MailboxClient client,
            NumberedList messages, Footer footer) {
        this.navigator = navigator;
        this.display = display;
        this.model = model;
        this.client = client;
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
        } else if (key == 'c' || key == 'C') {
            navigator.show(COMPOSE_VIEW);
        } else if (key == 'r' || key == 'R') {
            footer.draw("Refreshing...");
            client.requestRefresh();
            renderedRevision = -1;
        } else if (key == 's' || key == 'S' || key == ESCAPE || key == CARDPUTER_ESCAPE) {
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

    private void drawEmptyStatus() {
        if (model.messageCount() > 0) {
            return;
        }
        display.setTextSize(1);
        display.setTextColor(Display.CYAN, Display.BLACK);
        display.setCursor(8, 72);
        display.print(model.status());
    }
}

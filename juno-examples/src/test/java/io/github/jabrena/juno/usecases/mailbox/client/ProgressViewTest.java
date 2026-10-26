package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.Navigator;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgressViewTest {
    private static final int INBOX_VIEW = 1;
    private static final int PROGRESS_VIEW = 6;

    private final Navigator navigator = mock(Navigator.class);
    private final Display display = mock(Display.class);
    private final MailboxApi client = mock(MailboxApi.class);
    private final MailboxModel model = new MailboxModel();
    private final ProgressView view =
            new ProgressView(navigator, display, client, model, new Footer(display), mock(Clock.class));
    private final char[] title = {'t'};
    private final char[] body = {'b'};

    @Test
    void sendsThenReadsTheListAndEachMessageOnePerFrameBeforeShowingTheInbox() {
        // Given a mailbox that stores the message and then lists two
        when(client.send(title, 1, body, 1)).thenReturn(true);
        when(client.readList()).thenReturn(2);
        when(client.readMessage(anyInt())).thenReturn(true);
        view.send(title, 1, body, 1);
        view.enter();

        // When four frames pass
        view.render();
        view.render();
        view.render();
        verify(navigator, never()).show(INBOX_VIEW);
        view.render();

        // Then
        InOrder order = inOrder(client, navigator);
        order.verify(navigator).show(PROGRESS_VIEW);
        order.verify(client).send(title, 1, body, 1);
        order.verify(client).readList();
        order.verify(client).readMessage(0);
        order.verify(client).readMessage(1);
        order.verify(client).finishRefresh(2);
        order.verify(navigator).show(INBOX_VIEW);
    }

    @Test
    void aFailedSendStaysUntilEscGoesBack() {
        // Given
        when(client.send(title, 1, body, 1)).thenReturn(false);
        view.send(title, 1, body, 1);
        view.enter();

        // When
        view.render();
        view.render();

        // Then nothing more is asked of the board, and Esc returns to the composer
        verify(client, never()).readList();
        verify(navigator, never()).show(INBOX_VIEW);
        view.key(27, false);
        verify(navigator).back();
    }

    @Test
    void aDeleteWithAnUnreadableListStillShowsTheInboxAndAsksForARetry() {
        // Given
        byte[] uuid = new byte[MailboxModel.UUID_LENGTH];
        when(client.delete(uuid, uuid.length)).thenReturn(true);
        when(client.readList()).thenReturn(-1);
        view.delete(uuid, uuid.length);
        view.enter();

        // When
        view.render();
        view.render();

        // Then
        verify(client).requestRefresh();
        verify(navigator).show(INBOX_VIEW);
    }

    @Test
    void aRefreshStartsWithTheList() {
        // Given an empty mailbox
        when(client.readList()).thenReturn(0);
        view.refresh();
        view.enter();

        // When
        view.render();

        // Then
        verify(client, never()).send(title, 1, body, 1);
        verify(client).finishRefresh(0);
        verify(navigator).show(INBOX_VIEW);
    }
}

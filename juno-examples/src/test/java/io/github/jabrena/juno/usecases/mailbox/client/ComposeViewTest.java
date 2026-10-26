package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.Navigator;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComposeViewTest {
    private final Display display = mock(Display.class);
    private final Navigator navigator = mock(Navigator.class);
    private final Keyboard keyboard = mock(Keyboard.class);
    private final ProgressView progress = new ProgressView(
            navigator, display, mock(MailboxApi.class), new MailboxModel(), new Footer(display), mock(Clock.class));
    private final ComposeView view = new ComposeView(navigator, display, keyboard, progress, new Footer(display));

    @Test
    void fnArrowsScrollALongBodyAndMarkTheHiddenRows() {
        // Given a body of five rows, of which the last three are shown
        view.enter();
        view.key(Keyboard.TAB, false);
        for (int row = 0; row < 5; row++) {
            view.key('x', false);
            view.key(Keyboard.ENTER, false);
        }
        verify(display, atLeastOnce()).print("^");

        // When scrolled up to the top: the cursor's row and two above it are hidden rows to scroll back over
        view.key(';', true);
        view.key(';', true);
        clearInvocations(display);
        view.key(';', true);

        // Then the rows below are marked and the first row is the label's again
        verify(display, never()).print("^");
        verify(display, atLeastOnce()).print("v");
        verify(display, atLeastOnce()).print("Body:");
    }

    @Test
    void anFnArrowIsNotTypedIntoTheBody() {
        // Given
        view.enter();
        view.key(Keyboard.TAB, false);
        clearInvocations(display);

        // When
        view.key('.', true);

        // Then
        verify(display, never()).print('.');
    }

    @Test
    void ctrlSHandsTheMessageToTheProgressView() {
        // Given a title and a body
        view.enter();
        view.key('t', false);
        view.key(Keyboard.TAB, false);
        view.key('b', false);

        // When
        when(keyboard.isControlPressed()).thenReturn(true);
        view.key('s', false);

        // Then the progress view, registered as view 6, sends it
        verify(navigator).show(6);
    }
}

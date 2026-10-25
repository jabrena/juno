package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.Navigator;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ComposeViewTest {
    private final Display display = mock(Display.class);
    private final ComposeView view = new ComposeView(
            mock(Navigator.class), display, mock(Keyboard.class), mock(MailboxApi.class), new Footer(display));

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
}

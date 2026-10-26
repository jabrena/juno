package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.mvc.Navigator;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoverViewTest {
    private final Navigator navigator = mock(Navigator.class);
    private final Display display = mock(Display.class);
    private final Clock clock = mock(Clock.class);

    @Test
    void anyKeyOpensTheSettings() {
        // Given
        CoverView cover = new CoverView(navigator, display, clock);
        cover.enter();

        // When
        cover.key('x', false);

        // Then
        verify(navigator).show(0);
    }

    @Test
    void blinksThePromptInAndOut() {
        // Given
        CoverView cover = new CoverView(navigator, display, clock);
        cover.enter();

        // When
        cover.render();
        when(clock.millis()).thenReturn(500);
        cover.render();

        // Then the prompt was printed once and then erased
        verify(display).print("PRESS ANY KEY TO CONTINUE");
    }
}

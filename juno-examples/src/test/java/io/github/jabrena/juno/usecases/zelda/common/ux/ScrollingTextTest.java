package io.github.jabrena.juno.usecases.zelda.common.ux;

import io.github.jabrena.juno.hardware.display.Display;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ScrollingTextTest {
    private static final int TOP = 32;
    private static final int HEIGHT = 74;
    private static final String LONG_TEXT =
            "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen";

    @Test
    void wrapsOnWordsAndShowsOnlyTheVisibleLines() {
        // Given
        Display display = mock(Display.class);
        ScrollingText text = new ScrollingText(display, TOP, HEIGHT);

        // When
        text.show(bytes(LONG_TEXT), LONG_TEXT.length());

        // Then
        assertThat(lineStartsDrawn(display)).containsExactly(TOP, TOP + 18, TOP + 36, TOP + 54);
        verify(display).print("v");
        verify(display, never()).print("^");
    }

    @Test
    void scrollsOneLineAndStopsAtTheEnd() {
        // Given
        Display display = mock(Display.class);
        ScrollingText text = new ScrollingText(display, TOP, HEIGHT);
        text.show(bytes(LONG_TEXT), LONG_TEXT.length());
        clearInvocations(display);

        // When
        for (int step = 0; step < 10; step++) {
            text.scroll(1);
        }

        // Then
        verify(display, atLeastOnce()).print("^");
        clearInvocations(display);
        text.scroll(1);
        verify(display, never()).fillRect(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void keepsShortTextOnOnePageWithoutIndicators() {
        // Given
        Display display = mock(Display.class);
        ScrollingText text = new ScrollingText(display, TOP, HEIGHT);

        // When
        text.show(bytes("Hello\nWorld"), 11);

        // Then
        assertThat(lineStartsDrawn(display)).containsExactly(TOP, TOP + 18);
        verify(display, never()).print("v");
    }

    private static List<Integer> lineStartsDrawn(Display display) {
        ArgumentCaptor<Integer> y = ArgumentCaptor.forClass(Integer.class);
        verify(display, atLeastOnce()).setCursor(eq(4), y.capture());
        return y.getAllValues();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}

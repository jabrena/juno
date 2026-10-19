package io.github.jabrena.juno.api.hid;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.hardware.internal.NativeMouse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Mouse")
class MouseTest {
    @Test
    @DisplayName("The board's USB mouse is one shared instance")
    void shouldHandOutOneInstanceForTheBoard() {
        // When & Then
        assertThat(MCU.getMouse()).isSameAs(MCU.getMouse()).isSameAs(NativeMouse.INSTANCE);
    }

    @Test
    @DisplayName("A whole-program emulator can replace the board's USB mouse")
    void shouldHandOutTheReplacementMouse() {
        // Given
        Mouse replacement = mock(Mouse.class);

        // When
        NativeMouse.setMouse(replacement);

        // Then
        try {
            assertThat(MCU.getMouse()).isSameAs(replacement);
        } finally {
            NativeMouse.setMouse(NativeMouse.INSTANCE);
        }
    }
}

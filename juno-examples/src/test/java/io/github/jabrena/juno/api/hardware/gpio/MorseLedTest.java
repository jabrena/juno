package io.github.jabrena.juno.api.hardware.gpio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("Morse LED")
class MorseLedTest {
    private static final int PIN = Gpio.getD13();

    private final Gpio port = mock(Gpio.class);
    private final Gpio gpio = port;

    @Test
    @DisplayName("Starts with the pin as an output and the LED off")
    void shouldSetThePinUpAndTurnTheLedOff() {
        // When
        new MorseLed(gpio, PIN, new byte[] {1, 0});

        // Then
        InOrder order = inOrder(port);
        order.verify(port).pinMode(PIN, Gpio.getOutput());
        order.verify(port).digitalWrite(PIN, false);
        verifyNoMoreInteractions(port);
    }

    @Test
    @DisplayName("Writes the pin only when the LED changes")
    void shouldWriteThePinOnlyWhenTheLedChanges() {
        // Given
        MorseLed led = new MorseLed(gpio, PIN, new byte[] {1, 1, 1, 0, 0, 1});
        clearInvocations(port);

        // When
        for (int unit = 0; unit < 6; unit++) {
            led.tick();
        }

        // Then
        InOrder order = inOrder(port);
        order.verify(port).digitalWrite(PIN, true);
        order.verify(port).digitalWrite(PIN, false);
        order.verify(port).digitalWrite(PIN, true);
        verifyNoMoreInteractions(port);
    }

    @Test
    @DisplayName("Starts the pattern again after its last unit")
    void shouldLoopThePattern() {
        // Given
        MorseLed led = new MorseLed(gpio, PIN, new byte[] {1, 0});
        led.tick();
        led.tick();
        clearInvocations(port);

        // When
        led.tick();

        // Then
        verify(port).digitalWrite(PIN, true);
        verifyNoMoreInteractions(port);
    }
}

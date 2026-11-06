package io.github.jabrena.juno.api.hardware.gpio;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Morse LED")
class MorseLedTest {
    private final Gpio port = mock(Gpio.class);
    private final Gpio gpio = port;

    @Test
    @DisplayName("Starts with the pin as an output and the LED off")
    void shouldSetThePinUpAndTurnTheLedOff() {
        // When
        int pin = digitalPin();
        int output = gpio.getOutput();
        clearInvocations(port);
        new MorseLed(gpio, pin, new byte[] {1, 0});

        // Then
        InOrder order = inOrder(port);
        order.verify(port).getOutput();
        order.verify(port).pinMode(pin, output);
        order.verify(port).digitalWrite(pin, false);
        verifyNoMoreInteractions(port);
    }

    @Test
    @DisplayName("Writes the pin only when the LED changes")
    void shouldWriteThePinOnlyWhenTheLedChanges() {
        // Given
        int pin = digitalPin();
        MorseLed led = new MorseLed(gpio, pin, new byte[] {1, 1, 1, 0, 0, 1});
        clearInvocations(port);

        // When
        for (int unit = 0; unit < 6; unit++) {
            led.tick();
        }

        // Then
        InOrder order = inOrder(port);
        order.verify(port).digitalWrite(pin, true);
        order.verify(port).digitalWrite(pin, false);
        order.verify(port).digitalWrite(pin, true);
        verifyNoMoreInteractions(port);
    }

    @Test
    @DisplayName("Starts the pattern again after its last unit")
    void shouldLoopThePattern() {
        // Given
        int pin = digitalPin();
        MorseLed led = new MorseLed(gpio, pin, new byte[] {1, 0});
        led.tick();
        led.tick();
        clearInvocations(port);

        // When
        led.tick();

        // Then
        verify(port).digitalWrite(pin, true);
        verifyNoMoreInteractions(port);
    }

    private int digitalPin() {
        when(gpio.digitalPin("D13")).thenReturn(13);
        return gpio.digitalPin("D13");
    }
}

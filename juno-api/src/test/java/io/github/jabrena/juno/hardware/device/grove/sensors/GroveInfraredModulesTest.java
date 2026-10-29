package io.github.jabrena.juno.hardware.device.grove.sensors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Grove infrared modules")
class GroveInfraredModulesTest {
    @Test
    @DisplayName("keep the emitter and receiver signal pins")
    void shouldKeepTheSignalPins() {
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(14);
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(16);

        assertThat(emitter.signalPin()).isEqualTo(14);
        assertThat(receiver.signalPin()).isEqualTo(16);
    }

    @Test
    @DisplayName("reject a missing emitter or receiver pin")
    void shouldRejectNegativeSignalPins() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroveInfraredEmitter(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> new GroveInfraredReceiver(-1));
    }
}

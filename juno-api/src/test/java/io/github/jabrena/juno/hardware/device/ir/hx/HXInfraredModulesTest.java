package io.github.jabrena.juno.hardware.device.ir.hx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("HX infrared modules")
class HXInfraredModulesTest {
    @BeforeEach
    void resetInfraredDriver() {
        Infrared.reset();
    }

    @Test
    @DisplayName("keep their signal pins and select the HX driver")
    void shouldKeepTheirSignalPinsAndSelectTheHxDriver() {
        HX53InfraredEmitter emitter = new HX53InfraredEmitter(3);
        HXM121InfraredReceiver receiver = new HXM121InfraredReceiver(2);

        emitter.begin(receiver, 2400);

        assertThat(emitter.signalPin()).isEqualTo(3);
        assertThat(receiver.signalPin()).isEqualTo(2);
        assertThat(Infrared.receivePin).isEqualTo(2);
        assertThat(Infrared.transmitPin).isEqualTo(3);
        assertThat(Infrared.baud).isEqualTo(2400);
        assertThat(Infrared.groveDriver).isFalse();
    }

    @Test
    @DisplayName("accept a disabled pin")
    void shouldAcceptADisabledPin() {
        assertThat(new HX53InfraredEmitter(-1).signalPin()).isEqualTo(-1);
        assertThat(new HXM121InfraredReceiver(-1).signalPin()).isEqualTo(-1);
    }

    @Test
    @DisplayName("reject pins below the disabled value")
    void shouldRejectPinsBelowTheDisabledValue() {
        assertThatIllegalArgumentException().isThrownBy(() -> new HX53InfraredEmitter(-2));
        assertThatIllegalArgumentException().isThrownBy(() -> new HXM121InfraredReceiver(-2));
    }
}

package io.github.jabrena.juno.remote.lego.rcx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RcxLightTest {
    @Test
    void shouldAcceptAllMotorOutputs() {
        new RcxLight(RcxLight.PORT_A);
        new RcxLight(RcxLight.PORT_B);
        new RcxLight(RcxLight.PORT_C);
    }

    @Test
    void shouldRejectAnUnknownMotorOutput() {
        assertThatThrownBy(() -> new RcxLight(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RcxLight(3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

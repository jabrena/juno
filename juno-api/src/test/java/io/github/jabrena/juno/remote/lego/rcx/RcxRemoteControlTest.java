package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RCX remote control handset")
class RcxRemoteControlTest {
    private RcxRemoteControl remote;

    @BeforeEach
    void startWithAnIdleReceiver() {
        Wire.resetBoard();
        Infrared.reset();
        remote = new RcxRemoteControl(new HXM121InfraredReceiver(2), SimulatedTime.clock());
    }

    @Test
    @DisplayName("Configures only the receiver at the RCX remote baud rate")
    void shouldConfigureAReceiveOnlyLink() {
        assertThat(Infrared.receivePin).isEqualTo(2);
        assertThat(Infrared.transmitPin).isEqualTo(-1);
        assertThat(Infrared.baud).isEqualTo(2400);
        assertThat(Infrared.groveDriver).isFalse();
    }

    @Test
    @DisplayName("Accepts typed Grove receivers without creating a transmitter")
    void shouldKeepTheGroveReceiverDriver() {
        remote = new RcxRemoteControl(new GroveInfraredReceiver(16), SimulatedTime.clock());

        assertThat(Infrared.receivePin).isEqualTo(16);
        assertThat(Infrared.transmitPin).isEqualTo(-1);
        assertThat(Infrared.groveDriver).isTrue();
    }

    @Test
    @DisplayName("Decodes the 16-bit button mask from a 9738 frame")
    void shouldDecodeButtonMask() {
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xD2, 0x01, 0x80));

        int buttons = remote.buttons();

        assertThat(buttons).isEqualTo(0x0180);
    }

    @Test
    @DisplayName("Expires a held button after the repeat window")
    void shouldExpireHeldButtons() {
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xD2, 0x01, 0x80));
        remote.buttons();
        SimulatedTime.now += 301_000;

        int buttons = remote.buttons();

        assertThat(buttons).isZero();
    }

    @Test
    @DisplayName("Ignores non-remote frames")
    void shouldIgnoreNonRemoteFrames() {
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(RcxFrames.frame(0xF7, 0x2A));

        int buttons = remote.buttons();

        assertThat(buttons).isZero();
    }

    @Test
    @DisplayName("Ignores a remote frame with a bad checksum")
    void shouldIgnoreMalformedRemoteFrames() {
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(List.of(0x55, 0xFF, 0x00, 0xD2, 0x2D, 0x01, 0xFE, 0x80, 0x7F, 0x00, 0xFF));

        int buttons = remote.buttons();

        assertThat(buttons).isZero();
    }

    @Test
    @DisplayName("Rejects a receiver without a signal pin")
    void shouldRejectAReceiverWithoutASignalPin() {
        assertThatThrownBy(() -> new RcxRemoteControl(new HXM121InfraredReceiver(-1), SimulatedTime.clock()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receiver signal pin");
    }
}

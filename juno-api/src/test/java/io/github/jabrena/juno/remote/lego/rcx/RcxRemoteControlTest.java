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
    @DisplayName("Maps handset masks to the button enum")
    void shouldMapButtonMasks() {
        assertThat(RcxRemoteControlButton.fromMask(RcxRemoteControlButton.MESSAGE_1.mask()))
                .isEqualTo(RcxRemoteControlButton.MESSAGE_1);
        assertThat(RcxRemoteControlButton.fromMask(RcxRemoteControlButton.A_UP.mask()))
                .isEqualTo(RcxRemoteControlButton.A_UP);
        assertThat(RcxRemoteControlButton.fromMask(RcxRemoteControlButton.A_DOWN.mask()))
                .isEqualTo(RcxRemoteControlButton.A_DOWN);
        assertThat(RcxRemoteControlButton.fromMask(RcxRemoteControlButton.PROGRAM_5.mask()))
                .isEqualTo(RcxRemoteControlButton.PROGRAM_5);
        assertThat(RcxRemoteControlButton.fromMask(RcxRemoteControlButton.STOP.mask()))
                .isEqualTo(RcxRemoteControlButton.STOP);
        assertThat(RcxRemoteControlButton.fromMask(RcxRemoteControlButton.SOUND.mask()))
                .isEqualTo(RcxRemoteControlButton.SOUND);
        assertThat(RcxRemoteControlButton.fromMask(
                        RcxRemoteControlButton.MESSAGE_1.mask() | RcxRemoteControlButton.MESSAGE_2.mask()))
                .isEqualTo(RcxRemoteControlButton.UNKNOWN);
    }

    @Test
    @DisplayName("Queues distinct button transitions for later consumption")
    void shouldQueueButtonTransitions() {
        Infrared.receivingFrame = true;
        List<Integer> first = RcxFrames.frame(0xD2, 0x00, 0x01);
        first.remove(first.size() - 1);
        List<Integer> second = RcxFrames.frame(0xD2, 0x00, 0x20);
        second.remove(second.size() - 1);
        Infrared.replies.addAll(first);
        Infrared.replies.addAll(second);

        remote.buttons();
        remote.buttons();

        assertThat(remote.pollButtonMask()).isEqualTo(RcxRemoteControlButton.MESSAGE_1.mask());
        assertThat(remote.pollButton()).isEqualTo(RcxRemoteControlButton.C_UP);
        assertThat(remote.pollButton()).isEqualTo(RcxRemoteControlButton.NONE);
    }

    @Test
    @DisplayName("Bounds the transition queue and counts discarded events")
    void shouldCountDroppedButtonTransitions() {
        Infrared.receivingFrame = true;
        for (int mask = 1; mask <= 17; mask++) {
            List<Integer> frame = RcxFrames.frame(0xD2, 0x00, mask);
            frame.remove(frame.size() - 1);
            Infrared.replies.addAll(frame);
        }

        for (int mask = 1; mask <= 17; mask++) {
            remote.buttons();
        }

        assertThat(remote.droppedButtonCount()).isEqualTo(1);
        for (int mask = 1; mask <= 16; mask++) {
            assertThat(remote.pollButtonMask()).isEqualTo(mask);
        }
        assertThat(remote.pollButtonMask()).isZero();
    }

    @Test
    @DisplayName("Queues the same button again when the handset sends a new transmission")
    void shouldQueueARepeatedPressOfTheSameButton() {
        Infrared.receivingFrame = true;
        queueFrame(0xD2, 0x01);
        queueFrame(0xDA, 0x01);

        remote.buttons();
        remote.buttons();

        assertThat(remote.pollButton()).isEqualTo(RcxRemoteControlButton.MESSAGE_1);
        assertThat(remote.pollButton()).isEqualTo(RcxRemoteControlButton.MESSAGE_1);
        assertThat(remote.pollButton()).isEqualTo(RcxRemoteControlButton.NONE);
    }

    @Test
    @DisplayName("Treats a resent frame with the same toggle bit as the same press")
    void shouldIgnoreARepeatedFrameOfTheSameTransmission() {
        Infrared.receivingFrame = true;
        queueFrame(0xD2, 0x01);
        queueFrame(0xD2, 0x01);

        remote.buttons();
        remote.buttons();

        assertThat(remote.pollButton()).isEqualTo(RcxRemoteControlButton.MESSAGE_1);
        assertThat(remote.pollButton()).isEqualTo(RcxRemoteControlButton.NONE);
    }

    private static void queueFrame(int opcode, int lowMask) {
        List<Integer> frame = RcxFrames.frame(opcode, 0x00, lowMask);
        frame.remove(frame.size() - 1);
        Infrared.replies.addAll(frame);
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

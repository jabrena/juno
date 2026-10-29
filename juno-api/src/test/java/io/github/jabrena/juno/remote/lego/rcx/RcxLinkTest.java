package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.InfraredReceiver;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RCX infrared link")
class RcxLinkTest {
    private RcxLink link;

    @BeforeEach
    void startWithAnEmptyLink() {
        Wire.resetBoard();
        Infrared.reset();
        link = new RcxLink(SimulatedTime.clock());
    }

    @Test
    @DisplayName("Send starts each frame with the header")
    void shouldStartEachFrameWithTheHeader() {
        // When
        link.send(0x13, 1, 2, 2);

        // Then
        assertThat(Infrared.written).startsWith(0x55, 0xFF, 0x00);
    }

    @Test
    @DisplayName("Begin accepts Grove infrared modules and keeps their signal mapping")
    void shouldBeginWithGroveInfraredModules() {
        InfraredReceiver receiver = new GroveInfraredReceiver(Gpio.getA2());
        InfraredEmitter emitter = new GroveInfraredEmitter(Gpio.getA0());

        link.begin(receiver, emitter);

        assertThat(Infrared.receivePin).isEqualTo(Gpio.getA2());
        assertThat(Infrared.transmitPin).isEqualTo(Gpio.getA0());
        assertThat(Infrared.baud).isEqualTo(2400);
        assertThat(Infrared.groveDriver).isTrue();
    }

    @Test
    @DisplayName("Begin keeps the HX-53 and HX-M121 pair on the legacy infrared driver")
    void shouldBeginWithHxInfraredModules() {
        HXM121InfraredReceiver receiver = new HXM121InfraredReceiver(Gpio.getD2());
        HX53InfraredEmitter emitter = new HX53InfraredEmitter(Gpio.getD3());

        link.begin(receiver, emitter);

        assertThat(Infrared.receivePin).isEqualTo(Gpio.getD2());
        assertThat(Infrared.transmitPin).isEqualTo(Gpio.getD3());
        assertThat(Infrared.baud).isEqualTo(2400);
        assertThat(Infrared.groveDriver).isFalse();
    }

    @Test
    @DisplayName("Send carries the command and its arguments with complements and a checksum")
    void shouldCarryTheCommandAndArgumentsWithComplementsAndAChecksum() {
        // Given
        link.send(0x13, 1, 2, 2);
        int code = Infrared.written.get(3);

        // When
        List<Integer> body = Infrared.written.subList(3, 11);

        // Then
        assertThat(body).containsExactly(
                code, ~code & 0xFF,
                1, 0xFE,
                2, 0xFD,
                (code + 1 + 2) & 0xFF, ~((code + 1 + 2) & 0xFF) & 0xFF);
    }

    @Test
    @DisplayName("Send masks the toggle bit out of the command code")
    void shouldMaskTheToggleBitOutOfTheCommandCode() {
        // Given
        link.send(0x13, 1, 2, 2);
        int code = Infrared.written.get(3);

        // When
        int command = code & 0xF7;

        // Then
        assertThat(command).isEqualTo(0x13);
    }

    @Test
    @DisplayName("Send toggles repeated command opcodes")
    void shouldToggleRepeatedCommandOpcodes() {
        // Given
        link.send(0x13, 0, 0, 0);
        int first = Infrared.written.get(3);
        Infrared.written.clear();

        // When
        link.send(0x13, 0, 0, 0);
        int second = Infrared.written.get(3);

        // Then
        assertThat(first ^ second).isEqualTo(0x08);
    }

    @Test
    @DisplayName("Channel tone frames encode the channel in the channel-specific opcode")
    void shouldSendAChannelToneFrame() {
        // When
        link.sendChannelTone(2, 440, 10);

        // Then
        assertThat(Infrared.written).containsSubsequence(
                0xB3, 0x4C, 0xB8, 0x47, 0x01, 0xFE, 0x0A, 0xF5, (0xB3 + 0xB8 + 0x01 + 0x0A) & 0xFF);
    }

    @Test
    @DisplayName("Configures a channel's tone indicator motor output")
    void shouldConfigureAChannelToneIndicator() {
        // When
        link.configureChannelIndicator(2, RcxLight.PORT_C);

        // Then
        assertThat(Infrared.written).containsSubsequence(
                0x92, 0x6D, 0x02, 0xFD, 0x02, 0xFD, (0x92 + 2 + 2) & 0xFF);
    }

    @Test
    @DisplayName("Send leaves the toggle clear when the command changes")
    void shouldLeaveTheToggleClearWhenTheCommandChanges() {
        // Given
        link.send(0x13, 0, 0, 0);
        int first = Infrared.written.get(3);
        Infrared.written.clear();

        // When
        link.send(0x21, 0, 0, 0);
        int second = Infrared.written.get(3);

        // Then
        assertThat(first).isEqualTo(0x13);
        assertThat(second).isEqualTo(0x21);
    }

    @Test
    @DisplayName("Remote-control frames always use the fixed 0xD2 opcode")
    void shouldNotToggleTheRemoteControlOpcode() {
        // When
        link.sendButtons(RemoteRcx.SOUND);
        int first = Infrared.written.get(3);
        Infrared.written.clear();
        link.sendButtons(RemoteRcx.SOUND);
        int second = Infrared.written.get(3);

        // Then
        assertThat(first).isEqualTo(0xD2);
        assertThat(second).isEqualTo(0xD2);
    }

    @Test
    @DisplayName("Receive reads a frame and reports it as received")
    void shouldReadAFrameAndReportItAsReceived() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(frame(0xD2, 0x05));

        // When
        int received = link.receive();

        // Then
        assertThat(received).isEqualTo(1);
    }

    @Test
    @DisplayName("Receive exposes the opcode of the frame")
    void shouldExposeTheOpcodeOfTheReceivedFrame() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(frame(0xD2, 0x05));
        link.receive();

        // When
        int opcode = link.opcode;

        // Then
        assertThat(opcode).isEqualTo(0xD2);
    }

    @Test
    @DisplayName("Receive exposes the first argument of the frame")
    void shouldExposeTheFirstArgumentOfTheReceivedFrame() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(frame(0xD2, 0x05));
        link.receive();

        // When
        int argument = link.argument0;

        // Then
        assertThat(argument).isEqualTo(0x05);
    }

    @Test
    @DisplayName("Receive reports nothing while the receiver is idle")
    void shouldReportNothingWhileTheReceiverIsIdle() {
        // Given
        Infrared.receivingFrame = false;

        // When
        int received = link.receive();

        // Then
        assertThat(received).isEqualTo(-1);
    }

    @Test
    @DisplayName("Receive rejects a frame whose checksum does not match")
    void shouldRejectAFrameWhoseChecksumDoesNotMatch() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(List.of(0x55, 0xFF, 0x00, 0xD2, 0x2D, 0x05, 0xFA, 0xD8, 0x27, Infrared.TIMEOUT));

        // When
        int received = link.receive();

        // Then
        assertThat(received).isEqualTo(-1);
    }

    @Test
    @DisplayName("Receive rejects a frame with a complement error")
    void shouldRejectAFrameWithAComplementError() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.add(0x55);
        Infrared.replies.add(0xFF);
        Infrared.replies.add(0x00);
        Infrared.replies.add(0xD2);
        Infrared.replies.add(0xD2);

        // When
        int received = link.receive();

        // Then
        assertThat(received).isEqualTo(-1);
    }

    @Test
    @DisplayName("Receive rejects a frame missing its header")
    void shouldRejectAFrameMissingItsHeader() {
        // Given
        Infrared.receivingFrame = true;
        Infrared.replies.add(0x12);

        // When
        int received = link.receive();

        // Then
        assertThat(received).isEqualTo(-1);
    }

    @Test
    @DisplayName("Request returns the reply to the complement opcode of its command")
    void shouldReturnTheReplyToTheComplementOpcodeOfItsCommand() {
        // Given
        Infrared.replies.addAll(frame(0xE4, 0x07));

        // When
        int reply = link.request(0x13, 1, 2, 2);

        // Then
        assertThat(reply).isEqualTo(1);
    }

    @Test
    @DisplayName("Request exposes the first argument of the reply")
    void shouldExposeTheFirstArgumentOfTheReply() {
        // Given
        Infrared.replies.addAll(frame(0xE4, 0x07));
        link.request(0x13, 1, 2, 2);

        // When
        int argument = link.argument0;

        // Then
        assertThat(argument).isEqualTo(0x07);
    }

    @Test
    @DisplayName("Request skips other frames and reports a timeout when no reply comes")
    void shouldSkipOtherFramesAndReportATimeoutWhenNoReplyComes() {
        // Given
        Infrared.replies.addAll(frame(0xD2, 0x01));

        // When
        int reply = link.request(0x13, 1, 2, 2);

        // Then
        assertThat(reply).isEqualTo(-1);
    }

    private static List<Integer> frame(int... values) {
        List<Integer> bytes = new ArrayList<>(List.of(0x55, 0xFF, 0x00));
        int sum = 0;
        for (int value : values) {
            bytes.add(value);
            bytes.add(~value & 0xFF);
            sum += value;
        }
        bytes.add(sum & 0xFF);
        bytes.add(~sum & 0xFF);
        bytes.add(Infrared.TIMEOUT);
        return bytes;
    }
}

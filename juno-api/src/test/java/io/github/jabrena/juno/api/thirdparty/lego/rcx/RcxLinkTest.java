package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.api.testing.Wire;
import io.github.jabrena.juno.api.thirdparty.ir.Infrared;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RcxLinkTest {

    @BeforeEach
    void startWithAnEmptyLink() {
        Wire.resetBoard();
        Infrared.reset();
    }

    @Test
    void sendFramesEachByteWithItsComplementAndAChecksum() {
        RcxLink.send(0x13, 1, 2, 2);

        int code = Infrared.written.get(3);
        assertThat(Infrared.written).startsWith(0x55, 0xFF, 0x00);
        assertThat(code & 0xF7).isEqualTo(0x13);
        assertThat(Infrared.written.subList(3, 11)).containsExactly(
                code, ~code & 0xFF,
                1, 0xFE,
                2, 0xFD,
                (code + 1 + 2) & 0xFF, ~((code + 1 + 2) & 0xFF) & 0xFF);
    }

    @Test
    void sendAlternatesTheToggleBetweenFrames() {
        RcxLink.send(0x13, 0, 0, 0);
        int first = Infrared.written.get(3);
        Infrared.written.clear();

        RcxLink.send(0x13, 0, 0, 0);
        int second = Infrared.written.get(3);

        assertThat(first ^ second).isEqualTo(0x08);
    }

    @Test
    void receiveReadsAFrameAndItsArgument() {
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(frame(0xD2, 0x05));

        assertThat(RcxLink.receive()).isEqualTo(1);
        assertThat(RcxLink.opcode).isEqualTo(0xD2);
        assertThat(RcxLink.argument0).isEqualTo(0x05);
    }

    @Test
    void receiveReportsNothingWhileTheReceiverIsIdle() {
        Infrared.receivingFrame = false;

        assertThat(RcxLink.receive()).isEqualTo(-1);
    }

    @Test
    void receiveRejectsAFrameWhoseChecksumDoesNotMatch() {
        Infrared.receivingFrame = true;
        Infrared.replies.addAll(List.of(0x55, 0xFF, 0x00, 0xD2, 0x2D, 0x05, 0xFA, 0xD8, 0x27, Infrared.TIMEOUT));

        assertThat(RcxLink.receive()).isEqualTo(-1);
    }

    @Test
    void receiveRejectsAFrameWithAComplementError() {
        Infrared.receivingFrame = true;
        Infrared.replies.add(0x55);
        Infrared.replies.add(0xFF);
        Infrared.replies.add(0x00);
        Infrared.replies.add(0xD2);
        Infrared.replies.add(0xD2);

        assertThat(RcxLink.receive()).isEqualTo(-1);
    }

    @Test
    void receiveRejectsAFrameMissingItsHeader() {
        Infrared.receivingFrame = true;
        Infrared.replies.add(0x12);

        assertThat(RcxLink.receive()).isEqualTo(-1);
    }

    @Test
    void requestWaitsForTheComplementOpcodeOfItsCommand() {
        Infrared.replies.addAll(frame(0xE4, 0x07));

        assertThat(RcxLink.request(0x13, 1, 2, 2)).isEqualTo(1);
        assertThat(RcxLink.argument0).isEqualTo(0x07);
    }

    @Test
    void requestSkipsOtherFramesAndReportsATimeoutWhenNoReplyComes() {
        Infrared.replies.addAll(frame(0xD2, 0x01));

        assertThat(RcxLink.request(0x13, 1, 2, 2)).isEqualTo(-1);
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

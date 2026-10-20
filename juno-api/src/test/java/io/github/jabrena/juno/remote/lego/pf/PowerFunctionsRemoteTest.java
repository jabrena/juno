package io.github.jabrena.juno.remote.lego.pf;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LEGO Power Functions remote")
class PowerFunctionsRemoteTest {
    private static final int MARK = 156;
    private static final int PAUSE_START_STOP = 1014;
    private static final int PAUSE_ONE = 546;
    private static final int PAUSE_ZERO = 260;
    /** Each repeat is a start mark, sixteen data marks and a stop mark: 18 mark/space pairs. */
    private static final int PAIRS_PER_REPEAT = 18;
    private static final int REPEATS = 6;

    @BeforeEach
    void startWithAnEmptyTransmitter() {
        Wire.resetBoard();
        Infrared.reset();
        PowerFunctionsRemote.begin(3);
    }

    @Test
    @DisplayName("Set speed sends six repeats of the same message")
    void shouldSendSixRepeatsOfTheSameMessage() {
        // Given
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 7);

        // When
        List<Integer> repeats = IntStream.range(0, REPEATS).mapToObj(PowerFunctionsRemoteTest::decode).toList();

        // Then
        assertThat(Infrared.pulses).hasSize(REPEATS * PAIRS_PER_REPEAT * 2);
        assertThat(repeats).containsOnly(repeats.get(0));
    }

    @Test
    @DisplayName("Each repeat starts with a start mark")
    void shouldStartEachMessageWithAStartMark() {
        // Given
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 7);

        // When & Then
        assertThat(Infrared.pulses.subList(0, 2)).containsExactly(MARK, -PAUSE_START_STOP);
    }

    @Test
    @DisplayName("Set speed encodes the channel, output and speed nibbles")
    void shouldEncodeTheChannelOutputAndSpeedNibbles() {
        // Given
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 7);
        int message = decode(0);

        // When & Then
        assertThat(new int[] {(message >> 8) & 0xF, (message >> 4) & 0xF})
                .as("channel 1, red output, speed 7 uses nibble 4 for output and 7 for speed")
                .containsExactly(0x4, 7);
    }

    @Test
    @DisplayName("Set speed appends a checksum nibble")
    void shouldAppendAChecksumNibble() {
        // Given
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 7);
        int message = decode(0);

        // When
        int checksum = message & 0xF;

        // Then
        assertThat(checksum).as("checksum of the three nibbles").isEqualTo(0xF ^ (message >> 12) ^ 4 ^ 7);
    }

    @Test
    @DisplayName("Toggle bit flips between successive single-output messages")
    void shouldFlipTheToggleBitBetweenSuccessiveSingleOutputMessages() {
        // Given
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 7);
        int first = decode(0) >> 12;
        Infrared.pulses.clear();

        // When
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 7);
        int second = decode(0) >> 12;

        // Then
        assertThat(first ^ second).isEqualTo(0x8);
    }

    @Test
    @DisplayName("A negative speed is sent as the reverse PWM nibble")
    void shouldSendANegativeSpeedAsTheReversePwmNibble() {
        // Given
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_BLUE, -3);

        // When
        int nibble = (decode(0) >> 4) & 0xF;

        // Then
        assertThat(nibble).isEqualTo(13);
    }

    @Test
    @DisplayName("Brake sends the brake nibble")
    void shouldSendTheBrakeNibble() {
        // Given
        PowerFunctionsRemote.brake(PowerFunctionsRemote.CHANNEL_2, PowerFunctionsRemote.OUTPUT_RED);

        // When
        int nibble = (decode(0) >> 4) & 0xF;

        // Then
        assertThat(nibble).isEqualTo(8);
    }

    @Test
    @DisplayName("Set speeds sends both outputs with the channel in the escape nibble")
    void shouldSendBothOutputsWithTheChannelInTheEscapeNibble() {
        // Given
        PowerFunctionsRemote.setSpeeds(PowerFunctionsRemote.CHANNEL_2, 2, 3);

        // When
        int message = decode(0);

        // Then
        assertThat(new int[] {message >> 12, (message >> 8) & 0xF, (message >> 4) & 0xF})
                .as("escape nibble, blue, red")
                .containsExactly(0x4 | 1, 3, 2);
    }

    @Test
    @DisplayName("Stop floats both outputs of the channel")
    void shouldFloatBothOutputsOfTheChannel() {
        // Given
        PowerFunctionsRemote.stop(PowerFunctionsRemote.CHANNEL_3);

        // When
        int message = decode(0);

        // Then
        assertThat(new int[] {message >> 12, (message >> 8) & 0xF, (message >> 4) & 0xF})
                .as("escape nibble, blue, red")
                .containsExactly(0x4 | 2, 0, 0);
    }

    /** The 16-bit message in repeat {@code repeat}, read from the space after each mark. */
    private static int decode(int repeat) {
        int base = repeat * PAIRS_PER_REPEAT * 2;
        int message = 0;
        for (int bit = 0; bit < 16; bit++) {
            int space = -Infrared.pulses.get(base + 3 + 2 * bit);
            message = (message << 1) | (space == PAUSE_ONE ? 1 : 0);
        }
        return message;
    }
}

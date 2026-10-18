package io.github.jabrena.juno.api.thirdparty.lego.pf;

import io.github.jabrena.juno.api.testing.Wire;
import io.github.jabrena.juno.api.thirdparty.ir.Infrared;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void setSpeedSendsSixRepeatsOfTheSameMessage() {
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 7);

        assertThat(Infrared.pulses).hasSize(REPEATS * PAIRS_PER_REPEAT * 2);
        assertThat(Infrared.pulses.get(0)).isEqualTo(MARK);
        assertThat(Infrared.pulses.get(1)).isEqualTo(-PAUSE_START_STOP);
        int message = decode(0);
        assertThat((message >> 8) & 0xF).as("channel 1, red output, speed 7 uses nibble 4 for output and 7 for speed")
                .isEqualTo(0x4);
        assertThat((message >> 4) & 0xF).isEqualTo(7);
        assertThat(message & 0xF).as("checksum of the three nibbles").isEqualTo(0xF ^ (message >> 12) ^ 4 ^ 7);
        for (int repeat = 1; repeat < REPEATS; repeat++) {
            assertThat(decode(repeat)).as("repeat %d", repeat).isEqualTo(message);
        }
    }

    @Test
    void theToggleBitFlipsBetweenSuccessiveSingleOutputMessages() {
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 7);
        int first = decode(0) >> 12;
        Infrared.pulses.clear();

        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_RED, 7);
        int second = decode(0) >> 12;

        assertThat(first ^ second).isEqualTo(0x8);
    }

    @Test
    void aNegativeSpeedIsSentAsTheReversePwmNibble() {
        PowerFunctionsRemote.setSpeed(PowerFunctionsRemote.CHANNEL_1, PowerFunctionsRemote.OUTPUT_BLUE, -3);

        assertThat((decode(0) >> 4) & 0xF).isEqualTo(13);
    }

    @Test
    void brakeSendsTheBrakeNibble() {
        PowerFunctionsRemote.brake(PowerFunctionsRemote.CHANNEL_2, PowerFunctionsRemote.OUTPUT_RED);

        assertThat((decode(0) >> 4) & 0xF).isEqualTo(8);
    }

    @Test
    void setSpeedsSendsBothOutputsWithTheChannelInTheEscapeNibble() {
        PowerFunctionsRemote.setSpeeds(PowerFunctionsRemote.CHANNEL_2, 2, 3);

        int message = decode(0);
        assertThat(message >> 12).isEqualTo(0x4 | 1);
        assertThat((message >> 8) & 0xF).as("blue").isEqualTo(3);
        assertThat((message >> 4) & 0xF).as("red").isEqualTo(2);
    }

    @Test
    void stopFloatsBothOutputsOfTheChannel() {
        PowerFunctionsRemote.stop(PowerFunctionsRemote.CHANNEL_3);

        int message = decode(0);
        assertThat(message >> 12).isEqualTo(0x4 | 2);
        assertThat((message >> 8) & 0xF).isZero();
        assertThat((message >> 4) & 0xF).isZero();
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

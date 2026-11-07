package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RegulatedBrick.Fault;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The regulated pair against a brick whose replies are lost, corrupted, duplicated or preceded by noise, and against a
 * brick that loses power. Each case asserts what the host may and may not conclude: a start is never counted twice, a
 * duplicate never completes a motion early, and an answer that never comes is an unknown outcome.
 */
@DisplayName("RCX regulated pair under transport faults")
class RcxRegulatedFaultInjectionTest {
    private static final int START = 0x76;
    private static final int STATUS = 0x61;

    private RegulatedBrick brick;
    private RcxRegulatedPair pair;

    @BeforeEach
    void startWithAnArmedPairAndABrick() {
        Wire.resetBoard();
        Infrared.reset();
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), SimulatedTime.clock());
        brick = new RegulatedBrick();
        Infrared.brick = brick;
        pair = new RcxRegulatedPair(link, 1, RcxRegulatedPair.maxPower(7), 16);
        pair.arm();
    }

    @Test
    @DisplayName("A clean run starts the motion once and completes it")
    void shouldCompleteACleanRun() {
        // When
        pair.rotate(90, 90, false);

        // Then
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.COMPLETE);
        assertThat(brick.accepted(START)).isEqualTo(1);
        assertThat(brick.executed(START)).isEqualTo(1);
    }

    @Test
    @DisplayName("A lost start reply is retried, the brick answers the repeat as a duplicate, and the motion starts once")
    void shouldRetryALostStartWithoutASecondMotion() {
        // Given: the brick starts the motion but its reply never arrives
        brick.inject(START, Fault.DROP);

        // When
        pair.rotate(90, 90, false);

        // Then
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.COMPLETE);
        assertThat(brick.executed(START)).isEqualTo(2);
        assertThat(brick.accepted(START)).isEqualTo(1);
    }

    @Test
    @DisplayName("A corrupted start reply is discarded and the start is retried, with the same result")
    void shouldRetryACorruptedStart() {
        // Given
        brick.inject(START, Fault.CORRUPT);

        // When
        pair.rotate(90, 90, false);

        // Then
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.COMPLETE);
        assertThat(brick.executed(START)).isEqualTo(2);
        assertThat(brick.accepted(START)).isEqualTo(1);
    }

    @Test
    @DisplayName("Noise before a start reply is skipped, and the start is answered once")
    void shouldSkipNoiseBeforeAReply() {
        // Given
        brick.inject(START, Fault.NOISE);

        // When
        pair.rotate(90, 90, false);

        // Then
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.COMPLETE);
        assertThat(brick.executed(START)).isEqualTo(1);
    }

    @Test
    @DisplayName("A duplicated status reply does not complete a motion that is still running")
    void shouldNotCompleteEarlyOnADuplicatedStatus() {
        // Given: a long motion, and the third status poll answered twice
        pair.rotate(3600, 3600, true);
        brick.inject(STATUS, Fault.NONE, Fault.NONE, Fault.DUPLICATE);

        // When: the two polls after the duplicate read the second copy and then the brick's next answer
        pair.status();
        pair.status();
        RcxRegulatedState afterDuplicate = pair.status();

        // Then
        assertThat(afterDuplicate).isEqualTo(RcxRegulatedState.RUNNING);
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.RUNNING);
    }

    @Test
    @DisplayName("A start that is never answered is an unknown outcome, not a completed motion")
    void shouldReportAnUnansweredStartAsUnknown() {
        // Given: all three attempts lose their reply
        brick.inject(START, Fault.DROP, Fault.DROP, Fault.DROP);

        // When / Then
        assertThatThrownBy(() -> pair.rotate(90, 90, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome unknown");
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.LINK_UNKNOWN);
    }

    @Test
    @DisplayName("A power loss during a motion reads as not armed, and the wait ends there")
    void shouldReadNotArmedAfterAPowerLoss() {
        // Given: a long motion, then the brick loses power
        pair.rotate(3600, 3600, true);
        brick.powerLoss();

        // When
        pair.waitComplete();

        // Then
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.NOT_ARMED);
    }
}

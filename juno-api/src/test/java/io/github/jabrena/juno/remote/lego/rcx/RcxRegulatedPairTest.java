package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RCX regulated pair over the infrared link")
class RcxRegulatedPairTest {
    private static final int ARM = 0x63;
    private static final int STATUS = 0x61;
    private static final int STOP = 0x62;
    private static final int PROFILE = 0x67;
    private static final int START = 0x76;
    private static final int READY = 1;
    private static final int COMPLETE = 3;
    private static final int STOPPED = 4;
    private static final int STALLED = 5;
    private static final int FAULT = 7;
    private static final int ACCEPTED = 0;
    private static final int DUPLICATE = 1;
    private static final int BUSY = 3;
    private static final int STALE_COMMAND = 8;
    private static final int STALL_FAULT = 0x01;
    private static final int EXTERNAL_OVERRIDE_FAULT = 0x20;

    private RcxLink link;
    private RcxRegulatedPair pair;

    @BeforeEach
    void startWithAnArmedPairOnAnEmptyChannel() {
        Wire.resetBoard();
        Infrared.reset();
        link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), SimulatedTime.clock());
        pair = new RcxRegulatedPair(link, 1, RcxRegulatedPair.INVERT_RIGHT_MOTOR, 16);
    }

    @Test
    @DisplayName("Arm sends the session, mapping 1 and the configuration flags, then reads the state as ready")
    void shouldArmTheSessionAndReadReady() {
        // Given
        reply(ARM, 0, 1, 1);
        reply(STATUS, 1, 0, READY, 0, 0, 0, 0, 0);

        // When
        pair.arm();

        // Then
        assertThat(sent(ARM).get(0)).containsExactly(ARM, 1, 1, RcxRegulatedPair.INVERT_RIGHT_MOTOR);
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.READY);
    }

    @Test
    @DisplayName("Rotating both sides sends the profile, then one start with little-endian targets, then waits")
    void shouldStartBothSidesInOneCommandWithLittleEndianTargets() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 100, 0, 200, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, ACCEPTED, 1, 1);
        reply(STATUS, 1, 1, COMPLETE, 0, 116, 0, 184, 0);

        // When
        pair.rotate(360, -360, false);

        // Then
        assertThat(sent(PROFILE).get(0)).containsExactly(PROFILE, 1, 8, 0, 16, 0, 1, 10);
        assertThat(sent(START).get(0)).containsExactly(START, 1, 1, 0x74, 0, 0xB8, 0);
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.COMPLETE);
    }

    @Test
    @DisplayName("A malformed start reply is retried with the same bytes, and a DUPLICATE does not restart the motion")
    void shouldRetryAnIdenticalStartWithoutRestartingTheMotion() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 100, 0, 200, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, ACCEPTED, 0);
        reply(START, DUPLICATE, 1, 1);
        reply(STATUS, 1, 1, COMPLETE, 0, 116, 0, 184, 0);

        // When
        pair.rotate(360, -360, false);

        // Then
        List<int[]> starts = sent(START);
        assertThat(starts).hasSize(2);
        assertThat(Arrays.equals(Arrays.copyOfRange(starts.get(1), 1, 7), Arrays.copyOfRange(starts.get(0), 1, 7)))
                .isTrue();
        assertThat(starts.get(1)[0]).isEqualTo(START + 8);
    }

    @Test
    @DisplayName("A start with no valid reply after the retries is reported as an unknown outcome")
    void shouldReportAnUnknownOutcomeWhenEveryRetryFails() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 100, 0, 200, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, ACCEPTED, 0);
        reply(START, ACCEPTED, 0);
        reply(START, ACCEPTED, 0);

        // When / Then
        assertThatThrownBy(() -> pair.rotate(360, -360, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome unknown");
        assertThat(sent(START)).hasSize(3);
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.LINK_UNKNOWN);
    }

    @Test
    @DisplayName("A start the brick rejects names the acknowledgement")
    void shouldNameARejectedStart() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 100, 0, 200, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, BUSY, 1, 1);

        // When / Then
        assertThatThrownBy(() -> pair.rotate(360, -360, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUSY");
    }

    @Test
    @DisplayName("A stall the brick latches is reported once the wait ends, with the fault bit")
    void shouldReportAStallAfterWaiting() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 100, 0, 200, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, ACCEPTED, 1, 1);
        reply(STATUS, 1, 1, STALLED, STALL_FAULT, 100, 0, 200, 0);

        // When
        pair.rotate(360, 0, false);

        // Then
        assertThat(pair.isStalled()).isTrue();
        assertThat(pair.isMoving()).isFalse();
        assertThat(pair.faults()).isEqualTo(STALL_FAULT);
    }

    @Test
    @DisplayName("An external-override fault reads as EXTERNAL_OVERRIDE, not as a plain fault")
    void shouldReportAnExternalOverride() {
        // Given
        armed();
        reply(STATUS, 1, 0, FAULT, EXTERNAL_OVERRIDE_FAULT, 0, 0, 0, 0);

        // When
        RcxRegulatedState state = pair.status();

        // Then
        assertThat(state).isEqualTo(RcxRegulatedState.EXTERNAL_OVERRIDE);
    }

    @Test
    @DisplayName("Positions unwrap across the 16-bit ring, so the tachometer keeps counting past 65535")
    void shouldUnwrapTheSixteenBitPositionAcrossTheRing() {
        // Given
        reply(ARM, 0, 1, 1);
        reply(STATUS, 1, 0, READY, 0, 0xF0, 0xFF, 0, 0);
        reply(STATUS, 1, 0, READY, 0, 0x10, 0, 0, 0);
        pair.arm();

        // When
        int degrees = pair.left().tachoCount();

        // Then
        assertThat(degrees).isEqualTo(720);
    }

    @Test
    @DisplayName("Resetting the tachometer while idle moves only the Java-side zero")
    void shouldResetTheZeroWhileIdle() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 0x40, 0, 0, 0);
        reply(STATUS, 1, 0, READY, 0, 0x40, 0, 0, 0);
        reply(STATUS, 1, 0, READY, 0, 0x40, 0, 0, 0);

        // When
        int before = pair.left().tachoCount();
        pair.left().resetTachoCount();
        int after = pair.left().tachoCount();

        // Then
        assertThat(before).isEqualTo(1440);
        assertThat(after).isZero();
    }

    @Test
    @DisplayName("Resetting the tachometer while a movement runs is rejected")
    void shouldRejectTachoResetWhileMoving() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 100, 0, 200, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, ACCEPTED, 1, 1);
        pair.rotate(360, 0, true);

        // When / Then
        assertThatThrownBy(() -> pair.left().resetTachoCount()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A displacement of 32768 sensor steps is rejected before any command is sent")
    void shouldRejectAmbiguousDisplacement() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 0, 0, 0, 0);
        int startsBefore = sent(START).size();

        // When / Then
        assertThatThrownBy(() -> pair.rotate(737_280, 0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(sent(START)).hasSize(startsBefore);
    }

    @Test
    @DisplayName("A non-zero movement smaller than one sensor step is rejected instead of completing silently")
    void shouldRejectAMovementThatQuantizesToZero() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 0, 0, 0, 0);

        // When / Then
        assertThatThrownBy(() -> pair.rotate(1, 0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("less than one sensor step");
    }

    @Test
    @DisplayName("Stop names the active motion, and the pair reads the stopped state afterwards")
    void shouldStopTheActiveMotion() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 100, 0, 200, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, ACCEPTED, 1, 1);
        pair.rotate(360, 0, true);
        reply(STOP, ACCEPTED, 1, 1);
        reply(STATUS, 1, 1, STOPPED, 0, 100, 0, 200, 0);

        // When
        pair.stop();

        // Then
        assertThat(sent(STOP).get(0)).containsExactly(STOP, 1, 1);
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.STOPPED);
    }

    @Test
    @DisplayName("The session must be a non-zero byte")
    void shouldRejectSessionZero() {
        assertThatThrownBy(() -> new RcxRegulatedPair(link, 0, 0, 16)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Open-loop power calls are not supported on a regulated output")
    void shouldRejectOpenLoopPower() {
        assertThatThrownBy(() -> pair.left().forward()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pair.right().setPower(50)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("A brick that rejects REG_ARM is reported by name")
    void shouldNameAnArmRejection() {
        reply(ARM, BUSY, 1, 1);

        assertThatThrownBy(() -> pair.arm())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUSY");
    }

    @Test
    @DisplayName("A brick that maps the pair somewhere other than mapping 1 fails the arm")
    void shouldRejectAMappingOtherThanOne() {
        reply(ARM, ACCEPTED, 1, 2);

        assertThatThrownBy(() -> pair.arm())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mapped");
    }

    @Test
    @DisplayName("An arm that is never followed by a status reply fails rather than assuming ready")
    void shouldFailTheArmWithoutAStatusReply() {
        reply(ARM, ACCEPTED, 1, 1);

        assertThatThrownBy(() -> pair.arm())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no status reply");
    }

    @Test
    @DisplayName("Profile values outside the firmware's ranges are rejected before any command")
    void shouldRejectProfileValuesOutsideTheirRanges() {
        assertThatThrownBy(() -> pair.setSpeed(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pair.setSpeed(2_000_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pair.setAcceleration(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pair.setTolerance(256)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pair.setTimeoutSeconds(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(sentFrames()).isEmpty();
    }

    @Test
    @DisplayName("A pair that is stalled is not ready to start another movement")
    void shouldNotStartFromAStalledState() {
        // Given
        armed();
        reply(STATUS, 1, 0, STALLED, STALL_FAULT, 0, 0, 0, 0);

        // When / Then
        assertThatThrownBy(() -> pair.rotate(360, 0, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STALLED");
    }

    @Test
    @DisplayName("A rotation with no status reply starts nothing")
    void shouldStartNothingWithoutAStatusReply() {
        // Given
        armed();

        // When / Then
        assertThatThrownBy(() -> pair.rotate(360, 0, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nothing was started");
        assertThat(sent(START)).isEmpty();
    }

    @Test
    @DisplayName("Stop with no movement since arming sends nothing")
    void shouldNotStopWhenNothingWasStarted() {
        // Given
        armed();

        // When
        pair.stop();

        // Then
        assertThat(sent(STOP)).isEmpty();
    }

    @Test
    @DisplayName("A stop with no reply is an unknown outcome, not a stopped motor")
    void shouldReportAnUnknownStopOutcome() {
        // Given
        startMotion();

        // When / Then
        assertThatThrownBy(() -> pair.stop())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outcome unknown");
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.LINK_UNKNOWN);
    }

    @Test
    @DisplayName("A stop the brick refuses as stale names the acknowledgement")
    void shouldNameARefusedStop() {
        // Given
        startMotion();
        reply(STOP, STALE_COMMAND, 1, 1);

        // When / Then
        assertThatThrownBy(() -> pair.stop())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STALE_COMMAND");
    }

    @Test
    @DisplayName("A wait that outlives the movement's timeout reports an unknown outcome and returns")
    void shouldGiveUpWaitingAfterTheTimeout() {
        // Given
        startMotion();
        pair.setTimeoutSeconds(1);

        // When
        pair.waitComplete();

        // Then
        assertThat(pair.cachedState()).isEqualTo(RcxRegulatedState.LINK_UNKNOWN);
    }

    @Test
    @DisplayName("An absolute rotation of the left side moves it to the target relative to its tachometer")
    void shouldRotateToAnAbsolutePosition() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 0, 0, 0, 0);
        reply(STATUS, 1, 0, READY, 0, 0, 0, 0, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, ACCEPTED, 1, 1);
        reply(STATUS, 1, 1, COMPLETE, 0, 0x20, 0, 0, 0);

        // When
        pair.left().rotateTo(720);

        // Then
        assertThat(sent(START).get(0)).containsExactly(START, 1, 1, 0x20, 0, 0, 0);
    }

    @Test
    @DisplayName("A rotation of the right side leaves the left side's target at its current position")
    void shouldRotateOnlyTheRightSide() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 100, 0, 200, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, ACCEPTED, 1, 1);
        reply(STATUS, 1, 1, COMPLETE, 0, 100, 0, 216, 0);

        // When
        pair.right().rotate(360);

        // Then
        assertThat(sent(START).get(0)).containsExactly(START, 1, 1, 0x64, 0, 0xD8, 0);
    }

    @Test
    @DisplayName("A tachometer that moves backwards across the ring reads negative degrees")
    void shouldReadNegativeDegreesWhenThePositionFalls() {
        // Given
        reply(ARM, ACCEPTED, 1, 1);
        reply(STATUS, 1, 0, READY, 0, 0x10, 0, 0, 0);
        reply(STATUS, 1, 0, READY, 0, 0x00, 0, 0, 0);
        pair.arm();

        // When
        int degrees = pair.left().tachoCount();

        // Then
        assertThat(degrees).isEqualTo(-360);
    }

    @Test
    @DisplayName("A status reply for another session does not change the pair's state")
    void shouldIgnoreAStatusOfAnotherSession() {
        // Given
        armed();
        reply(STATUS, 2, 0, READY, 0, 0, 0, 0, 0);

        // When
        RcxRegulatedState state = pair.status();

        // Then
        assertThat(state).isEqualTo(RcxRegulatedState.NOT_ARMED);
    }

    @Test
    @DisplayName("A status read with no reply reports an unknown state")
    void shouldReportAnUnknownStateWithoutAStatusReply() {
        // Given
        armed();

        // When
        RcxRegulatedState state = pair.status();

        // Then
        assertThat(state).isEqualTo(RcxRegulatedState.LINK_UNKNOWN);
    }

    @Test
    @DisplayName("Motion IDs run out after 255 movements in a session, and the pair asks for a re-arm")
    void shouldRequireARearmWhenMotionIdsRunOut() {
        // Given
        armed();
        for (int motion = 1; motion <= 255; motion++) {
            reply(STATUS, 1, 0, READY, 0, 0, 0, 0, 0);
            reply(PROFILE, ACCEPTED);
            reply(START, ACCEPTED, 1, motion);
            pair.rotate(0, 0, true);
        }
        reply(STATUS, 1, 0, READY, 0, 0, 0, 0, 0);

        // When / Then
        assertThatThrownBy(() -> pair.rotate(0, 0, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("arm() a new session");
    }

    @Test
    @DisplayName("An unknown acknowledgement code is reported by its number")
    void shouldNameAnUnknownAcknowledgement() {
        // Given
        armed();
        reply(STATUS, 1, 0, READY, 0, 0, 0, 0, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, 42, 1, 1);

        // When / Then
        assertThatThrownBy(() -> pair.rotate(0, 0, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNKNOWN_42");
    }

    @Test
    @DisplayName("A pair needs a valid configuration, a session and a sensor resolution in range")
    void shouldRejectAnInvalidConfiguration() {
        assertThatThrownBy(() -> new RcxRegulatedPair(link, 1, 0x100, 16)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RcxRegulatedPair(link, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RcxRegulatedPair(link, 1, 0, 70_000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A link without an infrared receiver cannot run regulated commands, which need its replies")
    void shouldRequireAReceiver() {
        // Given
        RcxLink emitterOnly = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(-1),
                SimulatedTime.clock());

        // When / Then
        assertThatThrownBy(() -> new RcxRegulatedPair(emitterOnly, 1, 0, 16))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receiver");
    }

    @Test
    @DisplayName("The firmware's state codes map to their states, and an unknown code reads as a fault")
    void shouldMapFirmwareStateCodes() {
        assertThat(RcxRegulatedState.fromFirmware(2)).isEqualTo(RcxRegulatedState.RUNNING);
        assertThat(RcxRegulatedState.fromFirmware(12)).isEqualTo(RcxRegulatedState.FAULT);
        assertThat(RcxRegulatedState.RUNNING.canStart()).isFalse();
    }

    @Test
    @DisplayName("The open-loop calls on either side are unsupported, and the idle calls are no-ops")
    void shouldDelegateTheIdleCallsOfAMotorHandle() {
        // Given
        RcxRegulatedMotor motor = pair.left();

        // When
        motor.setSpeed(90);
        motor.setAcceleration(100);
        motor.waitComplete();
        motor.stop();

        // Then
        assertThat(motor.speed()).isEqualTo(90);
        assertThat(motor.isMoving()).isFalse();
        assertThat(motor.isStalled()).isFalse();
        assertThatThrownBy(() -> motor.backward()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> motor.flt()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> motor.getPower()).isInstanceOf(UnsupportedOperationException.class);
    }

    /** Arms the pair and starts a movement, leaving it running with its start acknowledged. */
    private void startMotion() {
        armed();
        reply(STATUS, 1, 0, READY, 0, 100, 0, 200, 0);
        reply(PROFILE, ACCEPTED);
        reply(START, ACCEPTED, 1, 1);
        pair.rotate(360, 0, true);
    }

    /** Arms the pair: the arm reply, then a status reading the new session as ready. */
    private void armed() {
        reply(ARM, 0, 1, 1);
        reply(STATUS, 1, 0, READY, 0, 0, 0, 0, 0);
        pair.arm();
    }

    /** Scripts a reply frame for {@code command}, followed by the silence that ends a frame on the air. */
    private static void reply(int command, int... arguments) {
        List<Integer> bytes = new ArrayList<>(List.of(0x55, 0xFF, 0x00));
        int replyOpcode = ~command & 0xFF;
        int sum = replyOpcode;
        bytes.add(replyOpcode);
        bytes.add(~replyOpcode & 0xFF);
        for (int argument : arguments) {
            sum += argument;
            bytes.add(argument);
            bytes.add(~argument & 0xFF);
        }
        bytes.add(sum & 0xFF);
        bytes.add(~sum & 0xFF);
        Infrared.replies.addAll(bytes);
        Infrared.replies.add(Infrared.TIMEOUT);
    }

    /** The frames the link transmitted, each as {@code [code, arguments...]} (toggle bit kept, checksum removed). */
    private static List<int[]> sentFrames() {
        List<Integer> bytes = Infrared.written;
        List<int[]> frames = new ArrayList<>();
        int index = 0;
        while (index < bytes.size()) {
            assertThat(bytes.subList(index, index + 3)).containsExactly(0x55, 0xFF, 0x00);
            index += 3;
            int pairs = (bytes.get(index) & 0x07) + 2;
            int[] values = new int[pairs - 1];
            int sum = 0;
            for (int pair = 0; pair < pairs; pair++) {
                int value = bytes.get(index + 2 * pair);
                assertThat(value ^ bytes.get(index + 2 * pair + 1)).isEqualTo(0xFF);
                if (pair < pairs - 1) {
                    values[pair] = value;
                    sum += value;
                } else {
                    assertThat(value).isEqualTo(sum & 0xFF);
                }
            }
            index += 2 * pairs;
            frames.add(values);
        }
        return frames;
    }

    /** The transmitted frames for {@code command}, ignoring the toggle bit: a retry is the same command. */
    private static List<int[]> sent(int command) {
        List<int[]> matching = new ArrayList<>();
        for (int[] frame : sentFrames()) {
            if ((frame[0] & ~0x08) == command) {
                matching.add(frame);
            }
        }
        return matching;
    }
}

package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RCX regulated differential drive")
class RcxRegulatedDifferentialDriveTest {
    private static final int STEPS_PER_TURN = 16;
    /** 82 mm wheels, 160 mm track, 1:1 on both sides: the reference build. */
    private static final DriveGeometry GEOMETRY = new DriveGeometry(82, 160, 360, 360);

    private RcxLink link;
    private RegulatedBrick brick;

    @BeforeEach
    void startWithABrick() {
        Wire.resetBoard();
        Infrared.reset();
        link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), SimulatedTime.clock());
        brick = new RegulatedBrick();
        Infrared.brick = brick;
    }

    private RcxRegulatedDifferentialDrive drive(boolean sidesSwapped) {
        return new RcxRegulatedDifferentialDrive(link, 1, RcxRegulatedPair.maxPower(7), STEPS_PER_TURN, GEOMETRY,
                sidesSwapped);
    }

    @Test
    @DisplayName("Arms when the brick answers and sends the brake lead set before arming")
    void shouldArmAndSendTheBrakeLead() {
        // Given
        RcxRegulatedDifferentialDrive drive = drive(false);
        drive.setBrakeLead(12);

        // When
        boolean armed = drive.tryArm();

        // Then
        assertThat(armed).isTrue();
        assertThat(brick.brakeLead()).isEqualTo(12);
        assertThat(drive.pair().cachedState()).isEqualTo(RcxRegulatedState.READY);
    }

    @Test
    @DisplayName("Reports a brick that does not answer as not armed, so the caller can retry")
    void shouldNotArmWithoutABrick() {
        // Given
        Infrared.brick = null;
        RcxRegulatedDifferentialDrive drive = drive(false);

        // When
        boolean armed = drive.tryArm();

        // Then
        assertThat(armed).isFalse();
        assertThat(drive.pair().cachedState()).isEqualTo(RcxRegulatedState.NOT_ARMED);
    }

    @Test
    @DisplayName("Travels both wheels forward by the same distance and completes")
    void shouldTravelStraight() {
        // Given
        RcxRegulatedDifferentialDrive drive = drive(false);
        drive.setLinearSpeedMillimetresPerSecond(150);
        drive.tryArm();

        // When
        drive.travelMillimetres(500);

        // Then
        assertThat(drive.status()).isEqualTo(RcxRegulatedState.COMPLETE);
        assertThat(drive.pair().cachedLeftTachoCount()).isPositive()
                .isEqualTo(drive.pair().cachedRightTachoCount());
    }

    @Test
    @DisplayName("Turns counter-clockwise with the left side backward and the right side forward")
    void shouldTurnCounterClockwise() {
        // Given
        RcxRegulatedDifferentialDrive drive = drive(false);
        drive.tryArm();

        // When
        drive.turnDegrees(90);

        // Then
        assertThat(drive.status()).isEqualTo(RcxRegulatedState.COMPLETE);
        assertThat(drive.pair().cachedLeftTachoCount()).isNegative();
        assertThat(drive.pair().cachedRightTachoCount()).isEqualTo(-drive.pair().cachedLeftTachoCount());
    }

    @Test
    @DisplayName("With swapped sides, a counter-clockwise turn drives the pair's left side forward")
    void shouldSwapSidesInATurn() {
        // Given
        RcxRegulatedDifferentialDrive drive = drive(true);
        drive.tryArm();

        // When
        drive.turnDegrees(90);

        // Then
        assertThat(drive.pair().cachedLeftTachoCount()).isPositive();
        assertThat(drive.pair().cachedRightTachoCount()).isNegative();
    }
}

package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.RegulatedPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Regulated differential drive navigation")
class RegulatedDifferentialDriveTest {

    /** A pair that records the movements it is asked for, instead of driving motors. */
    private static final class RecordingPair implements RegulatedPair {
        int speed;
        int acceleration;
        int leftDegrees;
        int rightDegrees;
        int movements;
        boolean immediate;
        boolean stopped;

        @Override
        public void setSpeed(int degreesPerSecond) {
            speed = degreesPerSecond;
        }

        @Override
        public void setAcceleration(int degreesPerSecondSquared) {
            acceleration = degreesPerSecondSquared;
        }

        @Override
        public void rotate(int left, int right, boolean immediateReturn) {
            leftDegrees = left;
            rightDegrees = right;
            immediate = immediateReturn;
            movements++;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public boolean isMoving() {
            return false;
        }

        @Override
        public boolean isStalled() {
            return false;
        }

        @Override
        public void waitComplete() {
        }

        @Override
        public boolean pollMoving() {
            return false;
        }

        @Override
        public int leftTachoCount() {
            return 0;
        }

        @Override
        public int rightTachoCount() {
            return 0;
        }
    }

    /** The calibrated geometry of the reference robot: 81 mm wheels, 140 mm track, 1800 encoder degrees per wheel turn. */
    private static final DriveGeometry REFERENCE = new DriveGeometry(81, 140, 1800, 1800);

    @Test
    @DisplayName("Straight travel gives both wheels the same target on symmetric gearing")
    void shouldTravelStraightWithEqualTargets() {
        // Given
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE);

        // When
        drive.travelMillimetres(500);

        // Then
        assertThat(pair.leftDegrees).isEqualTo(3537);
        assertThat(pair.rightDegrees).isEqualTo(3537);
        assertThat(pair.immediate).isFalse();
    }

    @Test
    @DisplayName("Backward travel negates both targets")
    void shouldTravelBackward() {
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE);

        drive.travelMillimetres(-500, true);

        assertThat(pair.leftDegrees).isEqualTo(-3537);
        assertThat(pair.rightDegrees).isEqualTo(-3537);
        assertThat(pair.immediate).isTrue();
    }

    @Test
    @DisplayName("A positive in-place turn drives the left wheel backwards and the right wheel forwards")
    void shouldTurnCounterClockwiseWithOppositeTargets() {
        // Given
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE);

        // When
        drive.turnDegrees(90);

        // Then
        assertThat(pair.leftDegrees).isEqualTo(-778);
        assertThat(pair.rightDegrees).isEqualTo(778);
    }

    @Test
    @DisplayName("With the sides swapped, a counter-clockwise turn sends the right wheel's forward target to the pair's left")
    void shouldTurnCounterClockwiseOnABuildWithSwappedSides() {
        // Given: the pair's left side drives the robot's right wheel, as on the reference RCX build
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE, true);

        // When
        drive.turnDegrees(90);

        // Then: the robot's right wheel, on the pair's left, forward; its left wheel, on the pair's right, backward
        assertThat(pair.leftDegrees).isEqualTo(778);
        assertThat(pair.rightDegrees).isEqualTo(-778);
    }

    @Test
    @DisplayName("With the sides swapped, each wheel keeps its own encoder ratio on the pair's other side")
    void shouldKeepEachWheelsRatioWhenTheSidesAreSwapped() {
        // Given: the robot's left wheel geared at 1800 degrees a turn, its right at 900
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, new DriveGeometry(81, 140, 1800, 900), true);

        // When
        drive.travelMillimetres(100);

        // Then: the right wheel's 354 degrees go to the pair's left, the left wheel's 707 to its right
        assertThat(pair.leftDegrees).isEqualTo(354);
        assertThat(pair.rightDegrees).isEqualTo(707);
    }

    @Test
    @DisplayName("A negative turn reverses the signs")
    void shouldTurnClockwise() {
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE);

        drive.turnDegrees(-90);

        assertThat(pair.leftDegrees).isEqualTo(778);
        assertThat(pair.rightDegrees).isEqualTo(-778);
    }

    @Test
    @DisplayName("A turn uses each side's own encoder ratio, so asymmetric gearing gives different target magnitudes")
    void shouldUseAsymmetricGearingForTurns() {
        // Given
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, new DriveGeometry(81, 140, 1800, 900));

        // When
        drive.turnDegrees(90);

        // Then
        assertThat(pair.leftDegrees).isEqualTo(-778);
        assertThat(pair.rightDegrees).isEqualTo(389);
    }

    @Test
    @DisplayName("Speed is converted to encoder degrees per second by the left wheel's ratio")
    void shouldConvertSpeedToEncoderDegrees() {
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE);

        drive.setLinearSpeedMillimetresPerSecond(150);

        assertThat(pair.speed).isEqualTo(1061);
    }

    @Test
    @DisplayName("Travel that quantizes to zero degrees is rejected rather than reported as complete")
    void shouldRejectTravelThatQuantizesToZero() {
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, new DriveGeometry(81, 140, 1, 1));

        assertThatThrownBy(() -> drive.travelMillimetres(1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pair.movements).isZero();
    }

    @Test
    @DisplayName("Geometry too large for the conversion's arithmetic is rejected at construction")
    void shouldRejectGeometryOutsideTheArithmeticRange() {
        RecordingPair pair = new RecordingPair();

        assertThatThrownBy(() -> new RegulatedDifferentialDrive(pair, new DriveGeometry(81, 140, 2_000_000, 1800)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A distance beyond the supported range is rejected before a movement is sent")
    void shouldRejectOverflowingDistance() {
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE);

        assertThatThrownBy(() -> drive.travelMillimetres(2_000_000)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pair.movements).isZero();
    }

    @Test
    @DisplayName("Speeds must be positive and within range, and one too low to represent is rejected")
    void shouldRejectUnrepresentableSpeeds() {
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE);
        RegulatedDifferentialDrive coarse = new RegulatedDifferentialDrive(pair, new DriveGeometry(81, 140, 1, 1));

        assertThatThrownBy(() -> drive.setLinearSpeedMillimetresPerSecond(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> drive.setLinearSpeedMillimetresPerSecond(2_000_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coarse.setLinearSpeedMillimetresPerSecond(1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pair.speed).isZero();
    }

    @Test
    @DisplayName("A turn too small to move a side by one encoder degree is rejected")
    void shouldRejectTurnThatQuantizesToZero() {
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, new DriveGeometry(81, 140, 1, 1));

        assertThatThrownBy(() -> drive.turnDegrees(1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pair.movements).isZero();
    }

    @Test
    @DisplayName("A turn beyond the supported angle is rejected before a movement is sent")
    void shouldRejectOverflowingTurn() {
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE);

        assertThatThrownBy(() -> drive.turnDegrees(400_000)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pair.movements).isZero();
    }

    @Test
    @DisplayName("A turn without waiting returns at once, and busy and wait calls reach the pair")
    void shouldPassTheImmediateFlagAndDelegateStatus() {
        RecordingPair pair = new RecordingPair();
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, REFERENCE);

        drive.turnDegrees(90, true);
        drive.waitComplete();

        assertThat(pair.immediate).isTrue();
        assertThat(drive.isMoving()).isFalse();
    }

    @Test
    @DisplayName("Stop reaches the pair")
    void shouldStopThePair() {
        RecordingPair pair = new RecordingPair();

        new RegulatedDifferentialDrive(pair, REFERENCE).stop();

        assertThat(pair.stopped).isTrue();
    }
}

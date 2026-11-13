package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.DirectionFinder;
import io.github.jabrena.juno.robotics.Encoder;
import io.github.jabrena.juno.robotics.Gyroscope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DifferentialDriveOdometryTest {

    @Test
    void usesEqualEncoderTravelToUpdateStraightLinePosition() {
        TestEncoder left = new TestEncoder();
        TestEncoder right = new TestEncoder();
        TestGyroscope gyro = new TestGyroscope();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, gyro, pose);

        assertThat(odometry.update()).isTrue();
        left.positionDegrees = 360;
        right.positionDegrees = 360;

        assertThat(odometry.update()).isTrue();
        assertThat(pose.xMillimetres()).isEqualTo(175);
        assertThat(pose.yMillimetres()).isZero();
        assertThat(pose.headingMillidegrees()).isZero();
    }

    @Test
    void usesImuHeadingToCorrectWheelOdometry() {
        TestEncoder left = new TestEncoder();
        TestEncoder right = new TestEncoder();
        TestGyroscope gyro = new TestGyroscope();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, gyro, pose);

        odometry.update();
        gyro.angleDegrees = 90;
        left.positionDegrees = 360;
        right.positionDegrees = 360;

        // A gyroscope's 90 degrees are clockwise: 270 in the Cartesian pose, and the robot moves right, to negative y.
        assertThat(odometry.update()).isTrue();
        assertThat(pose.headingMillidegrees()).isEqualTo(270_000);
        assertThat(pose.xMillimetres()).isEqualTo(124);
        assertThat(pose.yMillimetres()).isEqualTo(-124);
    }

    @Test
    void fallsBackToWheelHeadingWhenImuReadFails() {
        TestEncoder left = new TestEncoder();
        TestEncoder right = new TestEncoder();
        TestGyroscope gyro = new TestGyroscope();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, gyro, pose);

        odometry.update();
        gyro.valid = false;
        left.positionDegrees = 360;
        right.positionDegrees = 360;

        assertThat(odometry.update()).isFalse();
        assertThat(pose.xMillimetres()).isEqualTo(175);
        assertThat(pose.yMillimetres()).isZero();
    }

    @Test
    void keepsADirectionFinderCounterClockwiseHeadingInTheCartesianPose() {
        TestEncoder left = new TestEncoder();
        TestEncoder right = new TestEncoder();
        TestDirectionFinder direction = new TestDirectionFinder();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, direction, pose);

        assertThat(odometry.update()).isTrue();
        direction.degrees = 90f;
        left.positionDegrees = 360;
        right.positionDegrees = 360;

        // A quarter turn counter-clockwise, and the robot moves to the left: positive y.
        assertThat(odometry.update()).isTrue();
        assertThat(pose.headingMillidegrees()).isEqualTo(90_000);
        assertThat(pose.xMillimetres()).isEqualTo(124);
        assertThat(pose.yMillimetres()).isEqualTo(124);
    }

    @Test
    void keepsADirectionFinderHeadingToTheThousandthOfADegree() {
        TestDirectionFinder direction = new TestDirectionFinder();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(new TestEncoder(), new TestEncoder(), direction, pose);

        odometry.update();
        direction.degrees = 1.25f;
        odometry.update();

        assertThat(pose.headingMillidegrees()).isEqualTo(1_250);
    }

    @Test
    void addsUpTheDistanceOfManyShortUpdatesWithoutLosingTheRounding() {
        TestEncoder left = new TestEncoder();
        TestEncoder right = new TestEncoder();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, new TestDirectionFinder(), pose);

        odometry.update();
        // 4 encoder degrees on a 56 mm wheel are 1.95 mm: truncated update by update, 100 of them would make 100 mm.
        for (int update = 1; update <= 100; update++) {
            left.positionDegrees = 4 * update;
            right.positionDegrees = 4 * update;
            odometry.update();
        }

        assertThat(pose.xMillimetres()).isEqualTo(195);
    }

    @Test
    void keepsTheHalfMillimetreWhenTheWheelsDifferByAnOddAmount() {
        TestEncoder left = new TestEncoder();
        TestEncoder right = new TestEncoder();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, new TestDirectionFinder(), pose);

        odometry.update();
        // Each update the left wheel adds 2 mm and the right 3: halving each sum would count 2 mm, not 2.5.
        for (int update = 1; update <= 100; update++) {
            left.positionDegrees = (int) Math.round(update * 2 * 360 / (Math.PI * 56));
            right.positionDegrees = (int) Math.round(update * 3 * 360 / (Math.PI * 56));
            odometry.update();
        }

        assertThat(pose.xMillimetres()).isCloseTo(250, within(2));
    }

    @Test
    void addsUpTheSidewaysPartOfManyShortMovesAtASmallAngle() {
        TestEncoder left = new TestEncoder();
        TestEncoder right = new TestEncoder();
        TestDirectionFinder direction = new TestDirectionFinder();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, direction, pose);

        odometry.update();
        // 3 degrees clockwise: each 2 mm step moves 0.1 mm to the right, which rounds to nothing on its own.
        direction.degrees = -3f;
        for (int update = 1; update <= 250; update++) {
            left.positionDegrees = 4 * update;
            right.positionDegrees = 4 * update;
            odometry.update();
        }

        assertThat(pose.xMillimetres()).isCloseTo(487, within(1));
        assertThat(pose.yMillimetres()).isCloseTo(-26, within(1));
    }

    @Test
    void turnsClockwiseOnTheWheelsWhenTheLeftWheelGoesFurther() {
        TestEncoder left = new TestEncoder();
        TestEncoder right = new TestEncoder();
        TestGyroscope gyro = new TestGyroscope();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, gyro, pose);

        odometry.update();
        gyro.valid = false;
        left.positionDegrees = 360;

        // 175 mm on the left wheel over a 120 mm track: 175 × 180 / (π × 120) = 83.56 degrees clockwise, 276.44 Cartesian.
        assertThat(odometry.update()).isFalse();
        assertThat(pose.headingMillidegrees()).isCloseTo(276_443, within(2));
    }

    private static DifferentialDriveOdometry odometry(
            TestEncoder left,
            TestEncoder right,
            TestDirectionFinder direction,
            Pose pose) {
        DriveGeometry geometry = new DriveGeometry(56, 120, 360, 360);
        return new DifferentialDriveOdometry(left, right, direction, geometry, pose);
    }

    private static DifferentialDriveOdometry odometry(
            TestEncoder left,
            TestEncoder right,
            TestGyroscope gyro,
            Pose pose) {
        DriveGeometry geometry = new DriveGeometry(56, 120, 360, 360);
        return new DifferentialDriveOdometry(left, right, gyro, geometry, pose);
    }

    private static final class TestEncoder implements Encoder {
        private int positionDegrees;

        @Override
        public int getTachoCount() {
            return positionDegrees;
        }

        @Override
        public void resetTachoCount() {
            positionDegrees = 0;
        }
    }

    private static final class TestGyroscope implements Gyroscope {
        private boolean valid = true;
        private int angleDegrees;

        @Override
        public int getAngle() {
            return valid ? angleDegrees : -1;
        }

        @Override
        public float getAngularVelocity() {
            return 0f;
        }

        @Override
        public void recalibrateOffset() {
        }

        @Override
        public void reset() {
            angleDegrees = 0;
        }
    }

    private static final class TestDirectionFinder implements DirectionFinder {
        private float degrees;

        @Override
        public float getDegreesCartesian() {
            return degrees;
        }

        @Override
        public void resetCartesianZero() {
            degrees = 0f;
        }

        @Override
        public void startCalibration() {
        }

        @Override
        public void stopCalibration() {
        }
    }
}

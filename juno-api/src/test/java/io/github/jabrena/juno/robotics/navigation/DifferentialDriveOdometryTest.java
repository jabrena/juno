package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.Encoder;
import io.github.jabrena.juno.robotics.Gyroscope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(odometry.update()).isTrue();
        assertThat(pose.headingMillidegrees()).isEqualTo(90_000);
        assertThat(pose.xMillimetres()).isEqualTo(124);
        assertThat(pose.yMillimetres()).isEqualTo(124);
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
}

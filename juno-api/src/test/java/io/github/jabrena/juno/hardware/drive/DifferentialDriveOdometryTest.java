package io.github.jabrena.juno.hardware.drive;

import io.github.jabrena.juno.hardware.imu.Imu;
import io.github.jabrena.juno.hardware.imu.ImuSample;
import io.github.jabrena.juno.hardware.motor.Encoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DifferentialDriveOdometryTest {

    @Test
    void usesEqualEncoderTravelToUpdateStraightLinePosition() {
        TestEncoder left = new TestEncoder();
        TestEncoder right = new TestEncoder();
        TestImu imu = new TestImu();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, imu, pose);

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
        TestImu imu = new TestImu();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, imu, pose);

        odometry.update();
        imu.headingMillidegrees = 90_000;
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
        TestImu imu = new TestImu();
        Pose pose = new Pose();
        DifferentialDriveOdometry odometry = odometry(left, right, imu, pose);

        odometry.update();
        imu.valid = false;
        left.positionDegrees = 360;
        right.positionDegrees = 360;

        assertThat(odometry.update()).isFalse();
        assertThat(pose.xMillimetres()).isEqualTo(175);
        assertThat(pose.yMillimetres()).isZero();
    }

    private static DifferentialDriveOdometry odometry(
            TestEncoder left,
            TestEncoder right,
            TestImu imu,
            Pose pose) {
        DriveGeometry geometry = new DriveGeometry(56, 120, 360, 360);
        return new DifferentialDriveOdometry(left, right, imu, geometry, pose);
    }

    private static final class TestEncoder implements Encoder {
        private int positionDegrees;

        @Override
        public int positionDegrees() {
            return positionDegrees;
        }

        @Override
        public void reset() {
            positionDegrees = 0;
        }
    }

    private static final class TestImu implements Imu {
        private boolean valid = true;
        private int headingMillidegrees;

        @Override
        public boolean begin() {
            return true;
        }

        @Override
        public boolean read(ImuSample sample) {
            if (!valid) {
                sample.invalidate();
                return false;
            }
            sample.update(headingMillidegrees, 0, 0, 0, 3);
            return true;
        }
    }
}

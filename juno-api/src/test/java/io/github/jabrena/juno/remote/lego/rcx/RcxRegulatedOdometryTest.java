package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.robotics.navigation.DifferentialDriveOdometry;
import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.robotics.navigation.Pose;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RCX regulated motors as odometry encoders")
class RcxRegulatedOdometryTest {
    private static final int READY = 1;
    /** One motor revolution: 16 sensor steps. */
    private static final int STEPS_PER_TURN = 16;

    private RcxLink link;
    private RcxRegulatedPair pair;

    /** A gyroscope whose heading is fixed by the test; a negative angle is no valid sample. */
    private static final class FixedGyroscope implements Gyroscope {
        int angle;

        @Override
        public int getAngle() {
            return angle;
        }

        @Override
        public float getAngularVelocity() {
            return 0;
        }

        @Override
        public void recalibrateOffset() {
        }

        @Override
        public void reset() {
        }
    }

    @BeforeEach
    void startWithAnArmedPair() {
        Wire.resetBoard();
        Infrared.reset();
        link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), SimulatedTime.clock());
        pair = new RcxRegulatedPair(link, 1, RcxRegulatedPair.maxPower(7), STEPS_PER_TURN);
    }

    /** Queues one status reply with both sides' raw positions, then the silence that ends a frame. */
    private static void statusReply(int left, int right) {
        List<Integer> bytes = new ArrayList<>(List.of(0x55, 0xFF, 0x00));
        int[] arguments = {1, 0, READY, 0, left & 0xFF, left >> 8, right & 0xFF, right >> 8};
        int opcode = ~0x61 & 0xFF;
        int sum = opcode;
        bytes.add(opcode);
        bytes.add(~opcode & 0xFF);
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

    @Test
    @DisplayName("Odometry reads each regulated side's tachometer and advances the pose by the wheels' distance")
    void shouldAdvanceThePoseFromTheRegulatedTachometers() {
        // Given: the wheels turn one revolution each (16 steps = 360 degrees); the gyroscope gives no valid sample
        statusReply(0, 0);
        statusReply(0, 0);
        statusReply(STEPS_PER_TURN, STEPS_PER_TURN);
        statusReply(STEPS_PER_TURN, STEPS_PER_TURN);
        Pose pose = new Pose();
        FixedGyroscope gyro = new FixedGyroscope();
        gyro.angle = -1;
        DifferentialDriveOdometry odometry = new DifferentialDriveOdometry(
                pair.left(), pair.right(), gyro, new DriveGeometry(81, 140, 360, 360), pose);

        // When
        boolean firstValid = odometry.update();
        boolean secondValid = odometry.update();

        // Then: 360 degrees of a 81 mm wheel is about 254 mm of travel, straight ahead, with wheel-only heading
        assertThat(firstValid).isFalse();
        assertThat(secondValid).isFalse();
        assertThat(pose.xMillimetres()).isBetween(250, 258);
        assertThat(pose.yMillimetres()).isZero();
        assertThat(pose.headingMillidegrees()).isZero();
    }

    @Test
    @DisplayName("A valid gyroscope sample is reported as valid and sets the heading")
    void shouldReportAValidGyroscopeSample() {
        // Given: both sides at zero, then both turned one revolution
        statusReply(0, 0);
        statusReply(0, 0);
        statusReply(STEPS_PER_TURN, STEPS_PER_TURN);
        statusReply(STEPS_PER_TURN, STEPS_PER_TURN);
        Pose pose = new Pose();
        FixedGyroscope gyro = new FixedGyroscope();
        gyro.angle = 0;
        DifferentialDriveOdometry odometry = new DifferentialDriveOdometry(
                pair.left(), pair.right(), gyro, new DriveGeometry(81, 140, 360, 360), pose);

        // When: the first sample sets the reference heading; the gyroscope then reads 90 degrees
        boolean firstValid = odometry.update();
        gyro.angle = 90;
        boolean secondValid = odometry.update();

        // Then
        assertThat(firstValid).isTrue();
        assertThat(secondValid).isTrue();
        assertThat(pose.headingMillidegrees()).isBetween(89_000, 91_000);
    }
}

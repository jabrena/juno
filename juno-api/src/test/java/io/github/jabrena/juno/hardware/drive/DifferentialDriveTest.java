package io.github.jabrena.juno.hardware.drive;

import io.github.jabrena.juno.hardware.motor.Motor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DifferentialDriveTest {

    @Test
    void mixesForwardAndTurnPowerAndClampsTheSides() {
        Recorder left = new Recorder();
        Recorder right = new Recorder();
        DifferentialDrive drive = new DifferentialDrive(left, right);

        drive.drive(80, 40);

        assertThat(left.events).containsExactly("power:100", "forward");
        assertThat(right.events).containsExactly("power:40", "forward");
    }

    @Test
    void drivesNegativePowerBackwardAndZeroPowerStops() {
        Recorder left = new Recorder();
        Recorder right = new Recorder();
        DifferentialDrive drive = new DifferentialDrive(left, right);

        drive.tank(-25, 0);

        assertThat(left.events).containsExactly("power:25", "backward");
        assertThat(right.events).containsExactly("stop");
    }

    @Test
    void forwardsAndBrakesBothMotorsTogether() {
        Recorder left = new Recorder();
        Recorder right = new Recorder();
        DifferentialDrive drive = new DifferentialDrive(left, right);

        drive.forward(125);
        drive.brake();

        assertThat(left.events).containsExactly("power:100", "forward", "brake");
        assertThat(right.events).containsExactly("power:100", "forward", "brake");
    }

    private static final class Recorder implements Motor {
        private final java.util.ArrayList<String> events = new java.util.ArrayList<>();

        @Override
        public void setPower(int percent) {
            events.add("power:" + percent);
        }

        @Override
        public void forward() {
            events.add("forward");
        }

        @Override
        public void backward() {
            events.add("backward");
        }

        @Override
        public void coast() {
            events.add("coast");
        }

        @Override
        public void brake() {
            events.add("brake");
        }

        @Override
        public void stop() {
            events.add("stop");
        }
    }
}

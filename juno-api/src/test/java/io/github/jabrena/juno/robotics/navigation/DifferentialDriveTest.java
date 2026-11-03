package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.DCMotor;
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
        drive.stop();

        assertThat(left.events).containsExactly("power:100", "forward", "stop");
        assertThat(right.events).containsExactly("power:100", "forward", "stop");
    }

    @Test
    void coastsBothMotorsWithFlt() {
        Recorder left = new Recorder();
        Recorder right = new Recorder();
        DifferentialDrive drive = new DifferentialDrive(left, right);

        drive.flt();

        assertThat(left.events).containsExactly("flt");
        assertThat(right.events).containsExactly("flt");
    }

    private static final class Recorder implements DCMotor {
        private final java.util.ArrayList<String> events = new java.util.ArrayList<>();
        private int power;

        @Override
        public void setPower(int percent) {
            power = percent;
            events.add("power:" + percent);
        }

        @Override
        public int getPower() {
            return power;
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
        public void flt() {
            events.add("flt");
        }

        @Override
        public void stop() {
            events.add("stop");
        }

        @Override
        public boolean isMoving() {
            return false;
        }
    }
}

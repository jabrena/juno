package io.github.jabrena.juno.hardware.drive;

import io.github.jabrena.juno.hardware.motor.Motor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeadingHoldControllerTest {

    @Test
    void appliesProportionalCorrectionToBothSides() {
        Recorder left = new Recorder();
        Recorder right = new Recorder();
        Pose pose = new Pose(0, 0, 0);
        HeadingHoldController controller = controller(left, right, pose);
        controller.setTargetHeadingMillidegrees(90_000);

        controller.holdHeading(40);

        assertThat(controller.correction()).isEqualTo(20);
        assertThat(left.events).containsExactly("power:60", "forward");
        assertThat(right.events).containsExactly("power:20", "forward");
    }

    @Test
    void choosesTheShortestCorrectionAcrossHeadingWrap() {
        HeadingHoldController controller = controller(new Recorder(), new Recorder(), new Pose(0, 0, 350_000));
        controller.setTargetHeadingMillidegrees(10_000);

        assertThat(controller.headingErrorMillidegrees()).isEqualTo(20_000);
        assertThat(controller.correction()).isEqualTo(4);
    }

    @Test
    void limitsCorrectionForLargeErrors() {
        HeadingHoldController controller = controller(new Recorder(), new Recorder(), new Pose());
        controller.setTargetHeadingMillidegrees(180_000);

        assertThat(controller.correction()).isEqualTo(30);
    }

    private static HeadingHoldController controller(Recorder left, Recorder right, Pose pose) {
        return new HeadingHoldController(new DifferentialDrive(left, right), pose, 20, 30);
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

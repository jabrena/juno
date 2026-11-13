package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.DCMotor;
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

        // The target is a quarter turn counter-clockwise: the right side speeds up.
        assertThat(controller.correction()).isEqualTo(-20);
        assertThat(left.events).containsExactly("power:20", "forward");
        assertThat(right.events).containsExactly("power:60", "forward");
    }

    @Test
    void choosesTheShortestCorrectionAcrossHeadingWrap() {
        HeadingHoldController controller = controller(new Recorder(), new Recorder(), new Pose(0, 0, 350_000));
        controller.setTargetHeadingMillidegrees(10_000);

        assertThat(controller.headingErrorMillidegrees()).isEqualTo(20_000);
        assertThat(controller.correction()).isEqualTo(-4);
    }

    @Test
    void limitsCorrectionForLargeErrors() {
        HeadingHoldController controller = controller(new Recorder(), new Recorder(), new Pose());
        controller.setTargetHeadingMillidegrees(180_000);

        assertThat(controller.correction()).isEqualTo(-30);
    }

    private static HeadingHoldController controller(Recorder left, Recorder right, Pose pose) {
        return new HeadingHoldController(new DifferentialDrive(left, right), pose, 20, 30);
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

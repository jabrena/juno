package io.github.jabrena.juno.api.mvc;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import io.github.jabrena.juno.api.thirdparty.tft.ParallelBus;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.util.Delay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ViewRouterTest {
    /** Thrown from a view's render once its script is exhausted, which is how a test leaves the never-ending frame loop. */
    private static final class Stop extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Stop() {
            super(null, null, false, false);
        }
    }

    private final List<String> log = new ArrayList<>();
    private final Deque<Consumer<ViewRouter>> script = new ArrayDeque<>();
    private ViewRouter router;

    @BeforeEach
    void startWithAnIdleTouchPanel() {
        Wire.resetBoard();
        ParallelBus.reset();
        TftTouchShield.begin();
        Gpio.analogValues[16] = 0;
        Gpio.analogValues[17] = 0;
        router = new ViewRouter(10);
        for (int id = 0; id < 4; id++) {
            router.register(id, new Recorder(id));
        }
    }

    @Test
    void showSwitchesTheActiveViewBetweenFrames() {
        script.add(r -> r.show(1));
        script.add(r -> { });
        script.add(r -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsSubsequence("render:0", "enter:1", "render:1");
    }

    @Test
    void showingTheActiveViewDoesNothing() {
        script.add(r -> r.show(0));
        script.add(r -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).doesNotContain("enter:0");
    }

    @Test
    void backReturnsToThePreviousView() {
        script.add(r -> r.show(1));
        script.add(r -> r.back());
        script.add(r -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsSubsequence("enter:1", "enter:0");
    }

    @Test
    void backDoesNothingWhenNothingWasRemembered() {
        script.add(r -> r.back());
        script.add(r -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).doesNotContain("enter:1");
        assertThat(log).doesNotContain("enter:0");
    }

    @Test
    void theBackStackKeepsOnlyTheFourMostRecentViews() {
        script.add(r -> r.show(1));
        script.add(r -> r.show(2));
        script.add(r -> r.show(3));
        script.add(r -> r.show(0));
        script.add(r -> r.show(1));
        script.add(r -> r.back());
        script.add(r -> r.back());
        script.add(r -> r.back());
        script.add(r -> r.back());
        script.add(r -> r.back());
        script.add(r -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsSubsequence("enter:1", "enter:0", "enter:3", "enter:2", "enter:1");
        assertThat(log.subList(log.lastIndexOf("enter:1") + 1, log.size())).doesNotContain("enter:0");
    }

    @Test
    void aTouchIsDeliveredOncePerPress() {
        Gpio.analogValues[16] = 523;
        Gpio.analogValues[17] = 600;
        script.add(r -> { });
        script.add(r -> { });
        script.add(r -> { throw new Stop(); });

        runFrom(0);

        assertThat(log.stream().filter(entry -> entry.startsWith("touch:")).toList())
                .containsExactly("touch:0:93:158");
    }

    @Test
    void theBackgroundTaskStepsOncePerFrame() {
        int[] steps = {0};
        router.background(() -> steps[0]++);
        script.add(r -> { });
        script.add(r -> { throw new Stop(); });

        runFrom(0);

        assertThat(steps[0]).isEqualTo(2);
    }

    @Test
    void aFrameThatOverrunsTheScheduleRestartsItRatherThanRushingToCatchUp() {
        script.add(r -> Delay.millis(500));
        script.add(r -> { });
        script.add(r -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsExactly("render:0", "render:0", "render:0");
    }

    @Test
    void startEntersTheFirstViewBeforeTheLoop() {
        router.start(2);

        assertThat(log).containsExactly("enter:2");
    }

    /**
     * Starts the frame loop from {@code viewId}; the scripted steps and the final {@link Stop} end it. The log is cleared
     * after the start, so assertions only see what the loop did.
     */
    private void runFrom(int viewId) {
        router.start(viewId);
        log.clear();
        try {
            router.run();
        } catch (Stop stop) {
            // The script ended: the frame loop is over.
        }
    }

    /** A view that logs its calls and runs the next scripted step each time it renders. */
    private final class Recorder implements View {
        private final int id;

        Recorder(int id) {
            this.id = id;
        }

        @Override
        public void enter() {
            log.add("enter:" + id);
        }

        @Override
        public void render() {
            log.add("render:" + id);
            Consumer<ViewRouter> step = script.poll();
            if (step == null) {
                throw new Stop();
            }
            step.accept(router);
        }

        @Override
        public void touch(int x, int y) {
            log.add("touch:" + id + ":" + x + ":" + y);
        }
    }
}

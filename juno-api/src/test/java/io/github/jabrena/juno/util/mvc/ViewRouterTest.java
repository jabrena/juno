package io.github.jabrena.juno.util.mvc;

import io.github.jabrena.juno.hardware.device.tft.ParallelBus;
import io.github.jabrena.juno.hardware.device.tft.TftPins;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import io.github.jabrena.juno.util.Delay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("View router frame loop")
class ViewRouterTest {
    /** Thrown from a view's render once its script is exhausted, which is how a test leaves the never-ending frame loop. */
    private static final class Stop extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Stop() {
            super(null, null, false, false);
        }
    }

    private final Gpio pins = mock(Gpio.class);
    private final List<String> log = new ArrayList<>();
    private final Deque<Consumer<ViewRouter>> script = new ArrayDeque<>();
    private ViewRouter router;

    @BeforeEach
    void startWithAnIdleTouchPanel() {
        Wire.resetBoard();
        ParallelBus.reset();
        TftPins.use(pins);
        TftTouchShield.begin();
        router = new ViewRouter(10, SimulatedTime.clock());
        for (int id = 0; id < 4; id++) {
            router.register(id, new Recorder(id));
        }
    }

    @Test
    @DisplayName("Show switches the active view between frames")
    void shouldSwitchTheActiveViewBetweenFrames() {
        // Given
        script.add(r -> r.show(1));
        script.add(r -> { });
        script.add(r -> { throw new Stop(); });

        // When
        runFrom(0);

        // Then
        assertThat(log).containsSubsequence("render:0", "enter:1", "render:1");
    }

    @Test
    @DisplayName("Showing the active view does nothing")
    void shouldNotEnterTheActiveViewAgain() {
        // Given
        script.add(r -> r.show(0));
        script.add(r -> { throw new Stop(); });

        // When
        runFrom(0);

        // Then
        assertThat(log).doesNotContain("enter:0");
    }

    @Test
    @DisplayName("Back returns to the previous view")
    void shouldReturnToThePreviousView() {
        // Given
        script.add(r -> r.show(1));
        script.add(r -> r.back());
        script.add(r -> { throw new Stop(); });

        // When
        runFrom(0);

        // Then
        assertThat(log).containsSubsequence("enter:1", "enter:0");
    }

    @Test
    @DisplayName("Back does nothing when no view was remembered")
    void shouldDoNothingWhenNothingWasRemembered() {
        // Given
        script.add(r -> r.back());
        script.add(r -> { throw new Stop(); });

        // When
        runFrom(0);

        // Then
        assertThat(log).doesNotContain("enter:1", "enter:0");
    }

    @Test
    @DisplayName("The back stack keeps only the four most recent views")
    void shouldKeepOnlyTheFourMostRecentViewsInTheBackStack() {
        // Given
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

        // When
        runFrom(0);

        // Then
        assertThat(log).containsSubsequence("enter:1", "enter:0", "enter:3", "enter:2", "enter:1");
        assertThat(log.subList(log.lastIndexOf("enter:1") + 1, log.size())).doesNotContain("enter:0");
    }

    @Test
    @DisplayName("A touch is delivered once per press")
    void shouldDeliverATouchOncePerPress() {
        // Given
        when(pins.analogRead(Gpio.getA2())).thenReturn(523);
        when(pins.analogRead(Gpio.getA3())).thenReturn(600);
        script.add(r -> { });
        script.add(r -> { });
        script.add(r -> { throw new Stop(); });

        // When
        runFrom(0);

        // Then
        assertThat(log.stream().filter(entry -> entry.startsWith("touch:")).toList())
                .containsExactly("touch:0:93:158");
    }

    @Test
    @DisplayName("The background task steps once per frame")
    void shouldStepTheBackgroundTaskOncePerFrame() {
        // Given
        int[] steps = {0};
        router.background(() -> steps[0]++);
        script.add(r -> { });
        script.add(r -> { throw new Stop(); });

        // When
        runFrom(0);

        // Then
        assertThat(steps[0]).isEqualTo(2);
    }

    @Test
    @DisplayName("A frame that overruns its schedule restarts instead of rushing to catch up")
    void shouldRestartAFrameThatOverrunsTheScheduleInsteadOfRushing() {
        // Given
        script.add(r -> Delay.millis(500));
        script.add(r -> { });
        script.add(r -> { throw new Stop(); });

        // When
        runFrom(0);

        // Then
        assertThat(log).containsExactly("render:0", "render:0", "render:0");
    }

    @Test
    @DisplayName("Start enters the first view before the loop")
    void shouldEnterTheFirstViewBeforeTheLoop() {
        // When
        router.start(2);

        // Then
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

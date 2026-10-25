package io.github.jabrena.juno.util.mvc;

import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.testing.FrameClock;
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

@DisplayName("Keyboard view router")
class KeyboardViewRouterTest {
    private static final class Stop extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Stop() {
            super(null, null, false, false);
        }
    }

    private final Keyboard keyboard = mock(Keyboard.class);
    private final List<String> log = new ArrayList<>();
    private final Deque<Consumer<KeyboardViewRouter>> script = new ArrayDeque<>();
    private FrameClock clock;
    private KeyboardViewRouter router;

    @BeforeEach
    void createRouter() {
        clock = new FrameClock(10);
        router = new KeyboardViewRouter(10, keyboard, clock);
        for (int id = 0; id < 5; id++) {
            router.register(id, new Recorder(id));
        }
    }

    @Test
    @DisplayName("Start enters the first view")
    void shouldEnterTheFirstView() {
        router.start(2);

        assertThat(log).containsExactly("enter:2");
    }

    @Test
    @DisplayName("A pressed key is delivered with the Fn state")
    void shouldDeliverPressedKeys() {
        when(keyboard.isChanged()).thenReturn(true);
        when(keyboard.isPressed()).thenReturn(true);
        when(keyboard.isFnPressed()).thenReturn(true);
        when(keyboard.keyCount()).thenReturn(2);
        when(keyboard.keyAt(0)).thenReturn((int) 'a');
        when(keyboard.keyAt(1)).thenReturn((int) 'b');
        script.add(router -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsSubsequence("key:0:97:true", "key:0:98:true", "render:0");
    }

    @Test
    @DisplayName("Show switches views between frames")
    void shouldSwitchTheActiveView() {
        script.add(router -> router.show(1));
        script.add(router -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsSubsequence("render:0", "enter:1", "render:1");
    }

    @Test
    @DisplayName("Showing the active view does not re-enter it")
    void shouldNotReenterTheActiveView() {
        script.add(router -> router.show(0));
        script.add(router -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).doesNotContain("enter:0");
    }

    @Test
    @DisplayName("Back returns to the previous view")
    void shouldReturnToThePreviousView() {
        script.add(router -> router.show(1));
        script.add(KeyboardViewRouterTest.this::goBack);
        script.add(router -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsSubsequence("enter:1", "enter:0");
    }

    @Test
    @DisplayName("Back does nothing without history")
    void shouldIgnoreBackWithoutHistory() {
        script.add(KeyboardViewRouterTest.this::goBack);
        script.add(router -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).doesNotContain("enter:0", "enter:1");
    }

    @Test
    @DisplayName("The back stack keeps the four most recent views")
    void shouldKeepTheFourMostRecentViews() {
        script.add(router -> router.show(1));
        script.add(router -> router.show(2));
        script.add(router -> router.show(3));
        script.add(router -> router.show(4));
        script.add(router -> router.show(0));
        script.add(KeyboardViewRouterTest.this::goBack);
        script.add(KeyboardViewRouterTest.this::goBack);
        script.add(KeyboardViewRouterTest.this::goBack);
        script.add(KeyboardViewRouterTest.this::goBack);
        script.add(KeyboardViewRouterTest.this::goBack);
        script.add(router -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsSubsequence("enter:1", "enter:2", "enter:3", "enter:4", "enter:0", "enter:4", "enter:3", "enter:2", "enter:1");
        assertThat(log.subList(log.lastIndexOf("enter:1") + 1, log.size())).doesNotContain("enter:0");
    }

    @Test
    @DisplayName("The background task steps once per frame")
    void shouldStepTheBackgroundTask() {
        int[] steps = {0};
        router.background(() -> steps[0]++);
        script.add(router -> { });
        script.add(router -> { throw new Stop(); });

        runFrom(0);

        assertThat(steps[0]).isEqualTo(2);
    }

    @Test
    @DisplayName("A long frame restarts the schedule")
    void shouldRestartAfterAnOverrun() {
        script.add(router -> clock.advance(500));
        script.add(router -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsExactly("render:0", "render:0");
    }

    @Test
    @DisplayName("A short frame waits for its next scheduled frame")
    void shouldWaitForTheNextFrame() {
        clock = new FrameClock(1);
        router = new KeyboardViewRouter(10, keyboard, clock);
        router.register(0, new Recorder(0));
        script.add(router -> { throw new Stop(); });

        runFrom(0);

        assertThat(log).containsExactly("render:0");
    }

    private void goBack(KeyboardViewRouter router) {
        router.back();
    }

    private void runFrom(int viewId) {
        router.start(viewId);
        log.clear();
        try {
            router.run();
        } catch (Stop stop) {
            // The scripted render ended the never-ending frame loop.
        }
    }

    private final class Recorder implements KeyboardView {
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
            Consumer<KeyboardViewRouter> step = script.poll();
            if (step == null) {
                throw new Stop();
            }
            step.accept(router);
        }

        @Override
        public void touch(int x, int y) {
        }

        @Override
        public void key(int key, boolean fnPressed) {
            log.add("key:" + id + ":" + key + ":" + fnPressed);
        }
    }
}

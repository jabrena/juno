package io.github.jabrena.juno.usecases.zelda.common;

import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.Task;

/** The controller for a keyboard-driven board program using {@link KeyboardView} screens. */
public final class KeyboardViewRouter implements Navigator {
    /** How many views a program can register. */
    public static final int MAX_VIEWS = 8;

    private static final int NONE = -1;
    private static final int HISTORY_DEPTH = 4;

    private final KeyboardView[] views = new KeyboardView[MAX_VIEWS];
    private final int framePeriodMs;
    private final Keyboard keyboard;
    private final Clock clock;
    private final int[] history = new int[HISTORY_DEPTH];
    private int historySize;
    private int active = NONE;
    private int pending = NONE;
    private boolean pendingIsBack;
    private Task task = new Idle();

    /** A router using the board clock. */
    public KeyboardViewRouter(int framePeriodMs, Keyboard keyboard) {
        this(framePeriodMs, keyboard, Arduino.getClock());
    }

    /** A router using {@code clock}, which can be mocked in a unit test. */
    public KeyboardViewRouter(int framePeriodMs, Keyboard keyboard, Clock clock) {
        this.framePeriodMs = framePeriodMs;
        this.keyboard = keyboard;
        this.clock = clock;
    }

    public void register(int viewId, KeyboardView view) {
        views[viewId] = view;
    }

    /** Sets work advanced once per frame. */
    public void background(Task work) {
        task = work;
    }

    /** Selects and enters the first view. */
    public void start(int viewId) {
        active = viewId;
        views[active].enter();
    }

    @Override
    public void show(int viewId) {
        pending = viewId;
        pendingIsBack = false;
    }

    @Override
    public void back() {
        if (historySize > 0) {
            pending = history[historySize - 1];
            pendingIsBack = true;
        }
    }

    /** Runs the keyboard frame loop; it does not return. */
    public void run() {
        int next = clock.millis();
        while (true) {
            handleKeyboard();
            task.step();
            switchIfRequested();
            views[active].render();
            next = next + framePeriodMs;
            int wait = next - clock.millis();
            if (wait > 0) {
                Delay.millis(wait);
            } else if (wait < -4 * framePeriodMs) {
                next = clock.millis();
            }
        }
    }

    private void handleKeyboard() {
        keyboard.update();
        if (!keyboard.isChanged() || !keyboard.isPressed()) {
            return;
        }
        boolean fnPressed = keyboard.isFnPressed();
        for (int index = 0; index < keyboard.keyCount(); index++) {
            views[active].key(keyboard.keyAt(index), fnPressed);
        }
    }

    private void switchIfRequested() {
        if (pending == NONE) {
            return;
        }
        int next = pending;
        pending = NONE;
        if (next == active) {
            return;
        }
        if (pendingIsBack) {
            historySize = historySize - 1;
        } else {
            remember(active);
        }
        active = next;
        views[active].enter();
    }

    private void remember(int viewId) {
        if (historySize == HISTORY_DEPTH) {
            for (int index = 1; index < HISTORY_DEPTH; index++) {
                history[index - 1] = history[index];
            }
            historySize = historySize - 1;
        }
        history[historySize] = viewId;
        historySize = historySize + 1;
    }

    private static final class Idle implements Task {
        @Override
        public void step() {
        }
    }
}

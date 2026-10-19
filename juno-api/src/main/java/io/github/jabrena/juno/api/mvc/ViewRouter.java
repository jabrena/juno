package io.github.jabrena.juno.api.mvc;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.util.Delay;

/**
 * The controller of a multi-view TFT program: it owns the views, runs the frame loop and switches the active view
 * when a view asks through {@link Navigator}. Each frame it reads the touch panel, gives a new touch to the active
 * view, advances the optional background {@link Task} one step, applies any pending switch (calling the new view's
 * {@link View#enter()}), then lets the active view render. Everything runs on the calling thread, so a program needs
 * no thread stacks for it. The views and the back stack are allocated once, when the router is built; the loop allocates nothing. Juno needs
 * every array length at compile time, so a router holds at most {@value #MAX_VIEWS} views, numbered from {@code 0}.
 */
public final class ViewRouter implements Navigator {
    /** How many views a program can register. */
    public static final int MAX_VIEWS = 8;

    private static final int NONE = -1;
    private static final int HISTORY_DEPTH = 4;

    private final View[] views = new View[MAX_VIEWS];
    private final int framePeriodMs;
    private final Clock clock;
    private final int[] history = new int[HISTORY_DEPTH];
    private int historySize;
    private int active = NONE;
    private int pending = NONE;
    private boolean pendingIsBack;
    private boolean touchWasDown;
    /** Until {@link #background} sets one, a task that does nothing: Juno needs a linked implementation to call. */
    private Task task = new Idle();

    /** A router that renders a frame every {@code framePeriodMs} of the board's clock. */
    public ViewRouter(int framePeriodMs) {
        this(framePeriodMs, MCU.getClock());
    }

    /** A router that renders a frame every {@code framePeriodMs} of {@code clock}: a mocked one in a unit test. */
    public ViewRouter(int framePeriodMs, Clock clock) {
        this.framePeriodMs = framePeriodMs;
        this.clock = clock;
    }

    public void register(int viewId, View view) {
        views[viewId] = view;
    }

    /** Sets the work advanced a step every frame, before the active view renders. */
    public void background(Task work) {
        task = work;
    }

    /** Makes {@code viewId} the first active view and draws it, before {@link #run()} starts the loop. */
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

    /**
     * Runs the frame loop; never returns. Frames start every {@code framePeriodMs}, so a view whose frame takes longer
     * loses only what it overruns, and a long stall (a view drawing its whole screen or loading data) restarts the
     * schedule rather than rushing to catch up.
     */
    public void run() {
        int next = clock.millis();
        while (true) {
            handleTouch();
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

    private void handleTouch() {
        boolean touched = TftTouchShield.readTouch();
        if (touched && !touchWasDown) {
            views[active].touch(TftTouchShield.touchX(), TftTouchShield.touchY());
        }
        touchWasDown = touched;
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

    /** Pushes {@code viewId} on the back stack, dropping the oldest entry when it is full. */
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

    /** The background task of a program that has none. */
    private static final class Idle implements Task {
        @Override
        public void step() {
            // Nothing to advance.
        }
    }
}

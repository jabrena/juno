package io.github.jabrena.juno.usecases.dashboard;

/**
 * One view of the dashboard. A screen draws itself and decides what a touch means: a touch on one of its buttons
 * either does its own work and stays, or opens another screen. The main loop only routes touches and draws the FPS
 * label shared by every screen.
 */
interface TftDashboardScreen {
    /** Draws the screen's static content; called once each time the screen opens. */
    void show();

    /** Redraws whatever changed since the last frame; called on every frame. */
    void draw();

    /** Handles a new touch at ({@code x}, {@code y}) and returns the screen to show next: this screen to stay. */
    TftDashboardScreen touch(int x, int y);
}

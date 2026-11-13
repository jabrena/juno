package io.github.jabrena.juno.robotics.navigation;

/**
 * A pilot's detector until one is set: the way is never blocked. A pilot holds one rather than no detector, so a program
 * that never sets one links no detector of its own.
 */
final class NoObstacles implements ObstacleDetector {
    @Override
    public boolean blocked() {
        return false;
    }

    @Override
    public int rangeMillimetres() {
        return -1;
    }

    @Override
    public void reset() {
    }
}

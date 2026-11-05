package io.github.jabrena.juno.robotics;

/** A device that reports whether it has detected motion within its field of view. */
public interface MotionDetector {

    /** Returns whether motion was detected at the last reading. */
    boolean isMotionDetected();
}

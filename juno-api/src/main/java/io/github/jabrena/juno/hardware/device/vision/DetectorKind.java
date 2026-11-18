package io.github.jabrena.juno.hardware.device.vision;

/** The detectors the API provides. {@link #CAT} is the only one in v1; a new kind needs its own model and runtime. */
public enum DetectorKind {
    /** Scores how likely a frame shows a cat. */
    CAT
}

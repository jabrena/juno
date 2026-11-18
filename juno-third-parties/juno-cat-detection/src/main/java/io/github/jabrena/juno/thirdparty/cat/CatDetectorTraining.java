package io.github.jabrena.juno.thirdparty.cat;

import ai.djl.ndarray.NDManager;

/**
 * The training entry point for the cat detector. It runs DJL's NDArray engine on the build machine; {@code ./mvnw verify}
 * never runs it. This first step only proves that the DJL dependency resolves and the engine starts.
 */
public final class CatDetectorTraining {
    private CatDetectorTraining() {
    }

    /** Starts the NDArray engine and returns the number of elements in a 64×64 grayscale frame, as a smoke check. */
    public static long smokeCheck() {
        try (NDManager manager = NDManager.newBaseManager()) {
            return manager.zeros(new ai.djl.ndarray.types.Shape(1, 64, 64)).size();
        }
    }
}

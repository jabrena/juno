package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;

import java.util.Set;

/**
 * What a program that uses the Pico 2 W's TrustZone secret store gives up because it runs in the non-secure world: the
 * second core, which cannot be started from there.
 */
public final class PicoTrustZoneRuntime {
    private PicoTrustZoneRuntime() {
    }

    /** Rejects a program that uses the TrustZone store and runs its virtual threads on two carriers. */
    public static void check(Set<ShimFeature> features, boolean dualCarrier) {
        if (dualCarrier && features.contains(ShimFeature.PICO_TRUSTZONE)) {
            throw new CompileException("The Pico 2 W's TrustZone secret store runs the program on one carrier: the "
                    + "second core cannot be started from the non-secure world. Build with -Djuno.carriers=1");
        }
    }
}

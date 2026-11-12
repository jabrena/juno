package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;

/**
 * The stop-the-world handshake a collection needs when threads run on several carriers ({@code common/carrier-gc.cpp}):
 * the collector waits until every other carrier is out of Java or parked with its registers on its stack. One carrier
 * needs none of it, so its output is unchanged.
 */
final class CarrierGcRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();

    String source(CoreRuntime core, int carrierCount) {
        if (carrierCount == 1) return "";
        return runtimeSource.load("common/carrier-gc.cpp")
                .replace("${JUNO_CARRIER_COUNT}", Integer.toString(carrierCount))
                .replace("${JUNO_REGISTER_SPILL}", core.registerSpill());
    }
}

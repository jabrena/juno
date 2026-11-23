package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;

/**
 * The stop-the-world handshake a collection needs when threads run on several carriers ({@code common/carrier-gc.cpp}):
 * the collector waits until every other carrier is out of Java or parked with its registers on its stack. One carrier
 * needs none of it, so its output is unchanged.
 */
final class CarrierGcRuntime {
    /** Where each carrier says it runs Java or is parked, and whether a collection stops them: plain statics. */
    private static final String STATE = """
            static volatile uint32_t juno_carrier_in_java[${JUNO_CARRIER_COUNT}];
            static volatile uint32_t juno_carrier_parked[${JUNO_CARRIER_COUNT}];
            static volatile bool juno_gc_stopping = false;""";

    private final RuntimeSource runtimeSource = new RuntimeSource();

    String source(CoreRuntime core, int carrierCount) {
        if (carrierCount == 1) return "";
        return runtimeSource.load("common/carrier-gc.cpp")
                .replace("${JUNO_CARRIER_GC_STATE}", core.carrierGcState().orElse(STATE))
                .replace("${JUNO_WORLD_STOPPED}", core.worldStoppedStatement())
                .replace("${JUNO_CARRIER_COUNT}", Integer.toString(carrierCount))
                .replace("${JUNO_REGISTER_SPILL}", core.registerSpill());
    }
}

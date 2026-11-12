package io.github.jabrena.juno.backend.runtime;

/**
 * The C++ source of the {@code java.lang.ScopedValue} runtime in the generated shim. A {@code ScopedValue} is a
 * small key id; a {@code Carrier} is the head of a chain of arena-allocated {@code (key, value, prev)} records.
 * {@code run}/{@code call} push a frame holding the carrier on the calling thread's own stack and point
 * {@code juno_scoped_top} at it, so lookups walk the frames innermost first and the collector finds the carrier
 * through the stack scan. The scheduler swaps {@code juno_scoped_top} per thread, and a forked subtask starts with
 * its owner's top frame (still alive: the owner cannot leave the binding before the scope closes).
 */
final class ScopedValueRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();


    String bindings(boolean runEntry, boolean callEntry, int noSuchElementClassId) {
        return bindings(runEntry, callEntry, noSuchElementClassId, 1);
    }

    /** Scoped binding heads are carrier-local when the scheduler has more than one execution carrier. */
    String bindings(boolean runEntry, boolean callEntry, int noSuchElementClassId, int carrierCount) {
        String runDeclaration = runEntry ? "extern \"C\" void juno_thread_entry(int32_t runnable);" : "";
        String run = runEntry ? """
                extern "C" void juno_scoped_run(int32_t carrier, int32_t runnable) {
                  JunoBindingFrame frame = {carrier, juno_scoped_top};
                  juno_scoped_top = &frame;
                  juno_thread_entry(runnable);
                  juno_scoped_top = frame.prev;
                }
                """ : "";
        String callDeclaration = callEntry ? "extern \"C\" int32_t juno_scoped_call_entry(int32_t operation);" : "";
        String call = callEntry ? """
                extern "C" int32_t juno_scoped_call(int32_t carrier, int32_t operation) {
                  JunoBindingFrame frame = {carrier, juno_scoped_top};
                  juno_scoped_top = &frame;
                  int32_t result = juno_scoped_call_entry(operation);
                  juno_scoped_top = frame.prev;
                  return result;
                }
                """ : "";
        String source = runtimeSource.load("common/scoped-values.cpp").replace("${JUNO_SCOPED_RUN_DECLARATION}", runDeclaration)
                .replace("${JUNO_SCOPED_CALL_DECLARATION}", callDeclaration)
                .replace("${JUNO_SCOPED_RUN}", run)
                .replace("${JUNO_SCOPED_CALL}", call)
                .replace("${JUNO_NO_SUCH_ELEMENT_CLASS_ID}", Integer.toString(noSuchElementClassId));
        if (carrierCount > 1) {
            source = source.replace("static JunoBindingFrame* juno_scoped_top = nullptr;", """
                    extern "C" uint32_t juno_port_carrier_id();
                    static JunoBindingFrame* juno_scoped_tops[${JUNO_CARRIER_COUNT}] = {};
                    #define juno_scoped_top juno_scoped_tops[juno_port_carrier_id()]
                    """.replace("${JUNO_CARRIER_COUNT}", Integer.toString(carrierCount)));
            JavaCallbacks callbacks = new JavaCallbacks(carrierCount);
            source = callbacks.declarations() + callbacks.around(callbacks.around(source,
                    "juno_thread_entry(runnable);"), "int32_t result = juno_scoped_call_entry(operation);");
        }
        return source;
    }
}

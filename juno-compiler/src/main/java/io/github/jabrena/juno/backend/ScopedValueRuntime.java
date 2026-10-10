package io.github.jabrena.juno.backend;

/**
 * The C++ source of the {@code java.lang.ScopedValue} runtime in the generated shim. A {@code ScopedValue} is a
 * small key id; a {@code Carrier} is the head of a chain of arena-allocated {@code (key, value, prev)} records.
 * {@code run}/{@code call} push a frame holding the carrier on the calling thread's own stack and point
 * {@code juno_scoped_top} at it, so lookups walk the frames innermost first and the collector finds the carrier
 * through the stack scan. The scheduler swaps {@code juno_scoped_top} per thread, and a forked subtask starts with
 * its owner's top frame (still alive: the owner cannot leave the binding before the scope closes).
 */
final class ScopedValueRuntime {
    private ScopedValueRuntime() {
    }

    static String bindings(boolean runEntry, boolean callEntry, int noSuchElementClassId) {
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
        return """

                // ---- Juno scoped values ----
                struct JunoBinding {
                  int32_t key;
                  int32_t value;
                  int32_t prev;
                };
                struct JunoBindingFrame {
                  int32_t carrier;
                  JunoBindingFrame* prev;
                };
                static JunoBindingFrame* juno_scoped_top = nullptr;
                static int32_t juno_scoped_keys = 0;
                ${JUNO_SCOPED_RUN_DECLARATION}
                ${JUNO_SCOPED_CALL_DECLARATION}

                extern "C" int32_t juno_scoped_new() {
                  return ++juno_scoped_keys;
                }

                static int32_t juno_scoped_bind(int32_t key, int32_t value, int32_t previous) {
                  if (key == 0) juno_panic();
                  auto* binding = static_cast<JunoBinding*>(juno_alloc(sizeof(JunoBinding), 4u));
                  binding->key = key;
                  binding->value = value;
                  binding->prev = previous;
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(binding));
                }

                extern "C" int32_t juno_scoped_where(int32_t key, int32_t value) {
                  return juno_scoped_bind(key, value, 0);
                }

                extern "C" int32_t juno_scoped_carrier_where(int32_t carrier, int32_t key, int32_t value) {
                  return juno_scoped_bind(key, value, carrier);
                }

                static const JunoBinding* juno_scoped_find(int32_t key) {
                  for (const JunoBindingFrame* frame = juno_scoped_top; frame != nullptr; frame = frame->prev) {
                    int32_t cursor = frame->carrier;
                    while (cursor != 0) {
                      const auto* binding = reinterpret_cast<const JunoBinding*>(static_cast<intptr_t>(cursor));
                      if (binding->key == key) return binding;
                      cursor = binding->prev;
                    }
                  }
                  return nullptr;
                }

                extern "C" int32_t juno_scoped_get(int32_t key) {
                  const JunoBinding* binding = juno_scoped_find(key);
                  if (binding != nullptr) return binding->value;
                  auto* exception = static_cast<int32_t*>(juno_alloc(8u, 4u));
                  exception[0] = ${JUNO_NO_SUCH_ELEMENT_CLASS_ID};
                  exception[1] = static_cast<int32_t>(reinterpret_cast<intptr_t>("ScopedValue not bound"));
                  juno_throw_raise(static_cast<int32_t>(reinterpret_cast<intptr_t>(exception)));
                  return 0;
                }

                extern "C" int32_t juno_scoped_is_bound(int32_t key) {
                  return juno_scoped_find(key) != nullptr ? 1 : 0;
                }

                extern "C" int32_t juno_scoped_or_else(int32_t key, int32_t other) {
                  const JunoBinding* binding = juno_scoped_find(key);
                  return binding != nullptr ? binding->value : other;
                }

                ${JUNO_SCOPED_RUN}
                ${JUNO_SCOPED_CALL}
                """.replace("${JUNO_SCOPED_RUN_DECLARATION}", runDeclaration)
                .replace("${JUNO_SCOPED_CALL_DECLARATION}", callDeclaration)
                .replace("${JUNO_SCOPED_RUN}", run)
                .replace("${JUNO_SCOPED_CALL}", call)
                .replace("${JUNO_NO_SUCH_ELEMENT_CLASS_ID}", Integer.toString(noSuchElementClassId));
    }
}

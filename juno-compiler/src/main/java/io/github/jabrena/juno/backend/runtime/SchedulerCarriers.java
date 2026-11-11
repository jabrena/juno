package io.github.jabrena.juno.backend.runtime;

/**
 * The carrier-dependent parts of the common scheduler template ({@code common/thread-scheduler.cpp}). One carrier
 * keeps the original single-core text byte for byte; several carriers index the per-carrier state by
 * {@code juno_port_carrier_id()}, place new threads on the least-loaded carrier and bracket the scheduler's own entry
 * points with the runtime lock.
 */
sealed interface SchedulerCarriers {
    static SchedulerCarriers of(int carrierCount) {
        return carrierCount == 1 ? new Single() : new Multiple(carrierCount);
    }

    /** The code that starts the preemption tick on first use: once per board, or once per carrier. */
    String startPreemption();

    /** Fills the template's carrier placeholders. */
    String apply(String template);

    record Single() implements SchedulerCarriers {
        @Override
        public String startPreemption() {
            return """
                      static bool juno_preempt_started = false;
                      if (!juno_preempt_started) {
                        juno_preempt_started = true;
                        juno_port_preempt_start();
                      }""";
        }

        @Override
        public String apply(String template) {
            return template.replace("${JUNO_CURRENT_SLOT_DECLARATION}", "static uint32_t juno_current_slot = 0u;")
                    .replace("${JUNO_SLICE_START_DECLARATION}", "static uint32_t juno_slice_start = 0u;")
                    .replace("${JUNO_CARRIER_DECLARATIONS}", "")
                    .replace("${JUNO_CARRIER_FILTER}", "")
                    .replace("${JUNO_PLACEMENT}", "")
                    .replace("${JUNO_CURRENT_SCAN}", "index == juno_current_slot ? marker : nullptr")
                    .replace("${JUNO_SET_CARRIER}", "")
                    .replace("${JUNO_SLOT_CARRIER_FIELD}", "")
                    .replace("${JUNO_SLOT_CARRIER_ACCESSOR}", "")
                    .replace("${JUNO_RUNTIME_UNLOCK}", "")
                    .replace("${JUNO_RUNTIME_RELOCK}", "")
                    .replace("${JUNO_FINISHED_SIGNAL}", "")
                    .replace("${JUNO_MAIN_EXIT}", """
                            extern "C" void juno_thread_main_exit() {
                              if (juno_live_threads == 0u) return;
                              juno_slots[juno_current_slot].state = JUNO_SLOT_JOINING_ALL;
                              juno_sched_block();
                            }""");
        }
    }

    record Multiple(int carrierCount) implements SchedulerCarriers {
        @Override
        public String startPreemption() {
            return counted("""
                      static bool juno_preempt_started[${JUNO_CARRIER_COUNT}] = {};
                      if (!juno_preempt_started[juno_port_carrier_id()]) {
                        juno_preempt_started[juno_port_carrier_id()] = true;
                        juno_port_preempt_start();
                      }""");
        }

        @Override
        public String apply(String template) {
            return template.replace("${JUNO_CURRENT_SLOT_DECLARATION}", counted("""
                            static uint32_t juno_current_slots[${JUNO_CARRIER_COUNT}] = {};
                            #define juno_current_slot juno_current_slots[juno_port_carrier_id()]
                            """))
                    .replace("${JUNO_SLICE_START_DECLARATION}", counted("""
                            static uint32_t juno_slice_starts[${JUNO_CARRIER_COUNT}] = {};
                            #define juno_slice_start juno_slice_starts[juno_port_carrier_id()]
                            """))
                    .replace("${JUNO_CARRIER_DECLARATIONS}", """
                            extern "C" uint32_t juno_port_carrier_id();
                            static uint32_t juno_current_carrier() {
                              return juno_port_carrier_id();
                            }
                            """)
                    .replace("${JUNO_CARRIER_FILTER}", """
                              if (slot.carrier != juno_current_carrier()) return false;
                            """)
                    .replace("${JUNO_PLACEMENT}", counted("""
                            static uint32_t juno_placement_hint = 0u;

                            static uint32_t juno_choose_carrier() {
                              uint32_t loads[${JUNO_CARRIER_COUNT}] = {};
                              for (uint32_t index = 0u; index < juno_slot_count; index++) {
                                if (juno_slots[index].state != JUNO_SLOT_FREE) loads[juno_slots[index].carrier]++;
                              }
                              uint32_t selected = 0u;
                              for (uint32_t carrier = 1u; carrier < ${JUNO_CARRIER_COUNT}u; carrier++) {
                                if (loads[carrier] < loads[selected]) selected = carrier;
                              }
                              if (loads[0] == loads[1] && ${JUNO_CARRIER_COUNT}u > 1u) {
                                selected = juno_placement_hint++ % ${JUNO_CARRIER_COUNT}u;
                              }
                              return selected;
                            }
                            """))
                    .replace("${JUNO_CURRENT_SCAN}", "juno_slots[index].carrier == juno_current_carrier() "
                            + "&& index == juno_current_slot ? marker : nullptr")
                    .replace("${JUNO_SET_CARRIER}", "  juno_slots[slot].carrier = juno_choose_carrier();\n")
                    .replace("${JUNO_SLOT_CARRIER_FIELD}", "  uint32_t carrier;\n")
                    .replace("${JUNO_SLOT_CARRIER_ACCESSOR}",
                            "extern \"C\" uint32_t juno_slot_carrier(uint32_t slot) { return juno_slots[slot].carrier; }\n")
                    .replace("${JUNO_RUNTIME_UNLOCK}", "bool juno_scheduler_locked = juno_runtime_owned();\n"
                            + "  if (juno_scheduler_locked) juno_runtime_exit();\n      ")
                    .replace("${JUNO_RUNTIME_RELOCK}", "if (juno_scheduler_locked) juno_runtime_enter();\n      ")
                    .replace("${JUNO_FINISHED_SIGNAL}", "juno_port_finished();\n")
                    .replace("${JUNO_MAIN_EXIT}", """
                            extern "C" void juno_thread_main_exit() {
                              juno_runtime_enter();
                              if (juno_live_threads == 0u) {
                                juno_runtime_exit();
                                return;
                              }
                              juno_slots[juno_current_slot].state = JUNO_SLOT_JOINING_ALL;
                              juno_sched_block();
                              juno_runtime_exit();
                            }
                            """);
        }

        private String counted(String text) {
            return text.replace("${JUNO_CARRIER_COUNT}", Integer.toString(carrierCount));
        }
    }
}

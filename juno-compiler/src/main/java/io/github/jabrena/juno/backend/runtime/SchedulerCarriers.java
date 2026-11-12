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

    /**
     * Fills the template's carrier placeholders.
     *
     * @param carrierLoad the statements that load a thread's own state into its carrier's per-carrier state
     * @param delayFunction the core's millisecond delay, for the waits the carrier code adds
     */
    String apply(String template, String carrierLoad, String delayFunction);

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
        public String apply(String template, String carrierLoad, String delayFunction) {
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
                    .replace("${JUNO_SLOT_REUSABLE}", "")
                    .replace("${JUNO_BLOCK_IDLE}", "")
                    .replace("${JUNO_CARRIER_FUNCTIONS}", "")
                    .replace("${JUNO_CARRIER_ADOPT}", "")
                    .replace("${JUNO_FINISH_ENTER}", "")
                    .replace("${JUNO_FINISH_EXIT}", "")
                    .replace("${JUNO_START_IDLE_CARRIER}", "")
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
        public String apply(String template, String carrierLoad, String delayFunction) {
            // A finishing task's completion touches its scope, which a thread on the other carrier may read or change
            // at the same time, so it runs under the runtime lock.
            JavaCallbacks callbacks = new JavaCallbacks(carrierCount);
            String withCallbacks = callbacks.declarations() + callbacks.around(callbacks.around(
                    lockTaskCompletion(template), "juno_thread_entry(barrier[4]);"),
                    "int32_t configured = juno_task_configuration_entry(operatorHandle, configuration);");
            return withCallbacks.replace("${JUNO_CURRENT_SLOT_DECLARATION}", counted("""
                            static uint32_t juno_current_slots[${JUNO_CARRIER_COUNT}] = {};
                            #define juno_current_slot juno_current_slots[juno_port_carrier_id()]
                            """))
                    .replace("${JUNO_SLICE_START_DECLARATION}", counted("""
                            static uint32_t juno_slice_starts[${JUNO_CARRIER_COUNT}] = {};
                            #define juno_slice_start juno_slice_starts[juno_port_carrier_id()]
                            """))
                    .replace("${JUNO_CARRIER_DECLARATIONS}", counted("""
                            extern "C" uint32_t juno_port_carrier_id();
                            // main runs on whichever core the board runs the sketch on. The first scheduler call is
                            // always main's (no other thread exists yet): it records that core as main's carrier and
                            // leaves every other carrier idle until a thread is placed on it.
                            static bool juno_carriers_ready = false;
                            static void juno_carriers_init() {
                              if (juno_carriers_ready) return;
                              juno_carriers_ready = true;
                              uint32_t mainCarrier = juno_port_carrier_id();
                              juno_slots[0].carrier = mainCarrier;
                              for (uint32_t carrier = 0u; carrier < ${JUNO_CARRIER_COUNT}u; carrier++) {
                                juno_current_slots[carrier] = carrier == mainCarrier ? 0u : JUNO_NO_SLOT;
                              }
                            }
                            static uint32_t juno_current_carrier() {
                              juno_carriers_init();
                              return juno_port_carrier_id();
                            }
                            """))
                    .replace("${JUNO_CARRIER_FILTER}", """
                              if (slot.carrier != juno_current_carrier()) return false;
                            """)
                    .replace("${JUNO_PLACEMENT}", counted("""
                            static uint32_t juno_placement_hint = 0u;

                            static bool juno_carrier_empty(uint32_t carrier) {
                              for (uint32_t index = 0u; index < juno_slot_count; index++) {
                                if (juno_slots[index].state != JUNO_SLOT_FREE && juno_slots[index].carrier == carrier) {
                                  return false;
                                }
                              }
                              return true;
                            }

                            // A slot whose task the port has already pinned to a core keeps that carrier; otherwise
                            // the least-loaded carrier, not counting the slot being placed.
                            static uint32_t juno_choose_carrier(uint32_t slot) {
                              juno_carriers_init();
                              uint32_t fixed = juno_port_fixed_carrier(slot);
                              if (fixed < ${JUNO_CARRIER_COUNT}u) return fixed;
                              uint32_t loads[${JUNO_CARRIER_COUNT}] = {};
                              for (uint32_t index = 0u; index < juno_slot_count; index++) {
                                if (index != slot && juno_slots[index].state != JUNO_SLOT_FREE) {
                                  loads[juno_slots[index].carrier]++;
                                }
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
                    .replace("${JUNO_SET_CARRIER}", "  juno_slots[slot].carrier = juno_choose_carrier(slot);\n")
                    .replace("${JUNO_SLOT_CARRIER_FIELD}", "  uint32_t carrier;\n")
                    .replace("${JUNO_SLOT_CARRIER_ACCESSOR}",
                            "extern \"C\" uint32_t juno_slot_carrier(uint32_t slot) { return juno_slots[slot].carrier; }\n")
                    .replace("${JUNO_RUNTIME_UNLOCK}", "bool juno_scheduler_locked = juno_runtime_owned();\n"
                            + "  if (juno_scheduler_locked) juno_runtime_exit();\n      ")
                    .replace("${JUNO_RUNTIME_RELOCK}", "if (juno_scheduler_locked) juno_runtime_enter();\n      ")
                    .replace("${JUNO_FINISHED_SIGNAL}", """
                            juno_runtime_enter();
                                uint32_t juno_resume = juno_sched_pick(true);
                                if (juno_resume == slot) {
                                  juno_runtime_exit();
                                  juno_port_start(slot);
                                  return;
                                }
                                if (juno_resume == JUNO_NO_SLOT && juno_carrier_empty(juno_current_carrier())) {
                                  juno_current_slot = JUNO_NO_SLOT;
                                  juno_runtime_exit();
                                  return;
                                }
                                juno_runtime_exit();
                            """ + "    ")
                    .replace("${JUNO_SLOT_REUSABLE}", " || !juno_port_reusable(slot)")
                    .replace("${JUNO_BLOCK_IDLE}", "juno_port_check_killed();\n    ")
                    .replace("${JUNO_CARRIER_FUNCTIONS}", """
                            // Loads a thread's own state into its carrier's per-carrier state.
                            static void juno_carrier_load(uint32_t slot) {${JUNO_CARRIER_LOAD_BODY}
                            }

                            // A cancelled thread's task stopped: if its carrier was running it, hand the carrier to
                            // the next runnable thread there, or leave the carrier idle once no thread is left on it.
                            static void juno_carrier_vacate(uint32_t slot) {
                              juno_runtime_enter();
                              if (juno_current_slot != slot) {
                                juno_runtime_exit();
                                return;
                              }
                              for (;;) {
                                uint32_t next = juno_sched_pick(true);
                                if (next != JUNO_NO_SLOT) {
                                  juno_current_slot = next;
                                  juno_carrier_load(next);
                                  juno_runtime_exit();
                                  juno_port_start(next);
                                  return;
                                }
                                if (juno_carrier_empty(juno_current_carrier())) {
                                  juno_current_slot = JUNO_NO_SLOT;
                                  juno_runtime_exit();
                                  return;
                                }
                                juno_runtime_exit();
                                ${JUNO_CORE_DELAY}(1u);
                                juno_runtime_enter();
                              }
                            }

                            """)
                    .replace("${JUNO_CARRIER_ADOPT}", "\n  juno_carrier_load(slot);\n  juno_java_resume();")
                    .replace("${JUNO_FINISH_ENTER}", "  juno_java_pause();\n  juno_runtime_enter();\n")
                    .replace("${JUNO_FINISH_EXIT}", "  juno_runtime_exit();\n")
                    .replace("${JUNO_START_IDLE_CARRIER}", "\n" + """
                            // An idle carrier has no thread to hand it over: the new thread starts there at once.
                            if (juno_current_slots[juno_slots[slot].carrier] == JUNO_NO_SLOT) {
                              juno_current_slots[juno_slots[slot].carrier] = slot;
                              juno_port_start(slot);
                            }""".indent(2).stripTrailing())
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
                            """)
                    .replace("${JUNO_CARRIER_LOAD_BODY}", carrierLoad)
                    .replace("${JUNO_CORE_DELAY}", delayFunction);
        }

        private static String lockTaskCompletion(String template) {
            return template.replace("juno_task_complete(thread, 0);",
                            "juno_runtime_enter();\n  juno_task_complete(thread, 0);\n  juno_runtime_exit();")
                    .replace("juno_task_complete(thread, result);",
                            "juno_runtime_enter();\n  juno_task_complete(thread, result);\n  juno_runtime_exit();");
        }

        private String counted(String text) {
            return text.replace("${JUNO_CARRIER_COUNT}", Integer.toString(carrierCount));
        }
    }
}

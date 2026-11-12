// ---- Stop-the-world collection across carriers. ----
// The collector runs on one carrier while it holds the runtime lock. Every other carrier must not run Java meanwhile:
// it could change a reference the collector has already scanned, or keep one only in a register. A carrier publishes
// whether it runs Java; the collector waits until each other carrier is out of Java or parked, and a parked carrier
// first spills its registers onto its stack, which the collector scans as a whole.
#if defined(__XTENSA__)
#include <xtensa/hal.h>
#endif

extern "C" uint32_t juno_port_carrier_id();
extern "C" void juno_runtime_enter();
extern "C" void juno_runtime_exit();

static volatile uint32_t juno_carrier_in_java[${JUNO_CARRIER_COUNT}];
static volatile uint32_t juno_carrier_parked[${JUNO_CARRIER_COUNT}];
static volatile bool juno_gc_stopping = false;

static void juno_carrier_park() {
  ${JUNO_REGISTER_SPILL}
  uint32_t carrier = juno_port_carrier_id();
  juno_carrier_parked[carrier] = 1u;
  __sync_synchronize();
  while (juno_gc_stopping) {
  }
  __sync_synchronize();
  juno_carrier_parked[carrier] = 0u;
}

// This carrier is about to run Java: it waits for a collection in progress to finish first.
extern "C" void juno_java_resume() {
  uint32_t carrier = juno_port_carrier_id();
  for (;;) {
    juno_carrier_in_java[carrier] = 1u;
    __sync_synchronize();
    if (!juno_gc_stopping) return;
    juno_carrier_in_java[carrier] = 0u;
    __sync_synchronize();
    while (juno_gc_stopping) {
    }
  }
}

// This carrier stops running Java (it enters the runtime, or its thread ends).
extern "C" void juno_java_pause() {
  __sync_synchronize();
  juno_carrier_in_java[juno_port_carrier_id()] = 0u;
}

// Generated code's lock bracket around a runtime call.
extern "C" void juno_java_leave() {
  juno_java_pause();
  juno_runtime_enter();
}

extern "C" void juno_java_return() {
  juno_runtime_exit();
  juno_java_resume();
}

// A safepoint in generated code: park while another carrier collects.
static void juno_gc_safepoint() {
  if (juno_gc_stopping) juno_carrier_park();
}

// A carrier waiting for the runtime lock may run Java (an allocation or a wide field access): it parks while the
// lock holder collects.
static void juno_gc_lock_wait() {
  if (juno_gc_stopping) juno_carrier_park();
}

// Called by the collector with the runtime lock held.
static void juno_gc_stop_world() {
  juno_gc_stopping = true;
  __sync_synchronize();
  uint32_t self = juno_port_carrier_id();
  for (uint32_t carrier = 0u; carrier < ${JUNO_CARRIER_COUNT}u; carrier++) {
    if (carrier == self) continue;
    while (juno_carrier_in_java[carrier] != 0u && juno_carrier_parked[carrier] == 0u) {
      __sync_synchronize();
    }
  }
  __sync_synchronize();
}

static void juno_gc_resume_world() {
  __sync_synchronize();
  juno_gc_stopping = false;
  __sync_synchronize();
}

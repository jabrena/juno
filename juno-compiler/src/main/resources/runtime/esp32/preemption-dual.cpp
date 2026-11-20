// ESP32 dual-carrier preemption at safepoints: a safepoint parks for a collection and stops a cancelled thread as
// before, then asks the port, without a lock, whether its own carrier's slice is over. Only then does it take the
// runtime lock both carriers share, so the two cores meet on that lock once a millisecond each rather than at every
// call and loop iteration. A backedge never yields to FreeRTOS by itself.
static void juno_thread_slice_over() {
  juno_gc_safepoint();
  juno_port_check_killed();
  if (!juno_port_slice_due()) return;
  juno_runtime_enter();
  if (juno_live_threads == 0u) {
    juno_runtime_exit();
    return;
  }
  uint32_t now = static_cast<uint32_t>(millis());
  juno_slice_start = now;
  juno_sched_block();
  juno_runtime_exit();
  // Outside the runtime lock, so the other carrier never waits on a breath.
  juno_port_breathe();
}

// Method entry: a safepoint.
extern "C" void juno_thread_safepoint() {
  juno_thread_slice_over();
}

extern "C" void juno_thread_backedge() {
  juno_thread_slice_over();
}

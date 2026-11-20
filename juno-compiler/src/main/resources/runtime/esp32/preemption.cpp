// ESP32 preemption at safepoints: the backend calls juno_thread_backedge on every loop backedge and
// juno_thread_safepoint on every method entry, and both only ask the port whether the slice is over; the scheduler is
// entered once a millisecond. A backedge never yields to FreeRTOS by itself: once the slice is over, juno_port_breathe
// gives the core to lower-priority work.
static void juno_thread_slice_over() {
  if (!juno_port_slice_due()) return;
  if (juno_live_threads == 0u) return;
  uint32_t now = static_cast<uint32_t>(millis());
  juno_slice_start = now;
  juno_sched_block();
  juno_port_breathe();
}

// Method entry: a safepoint.
extern "C" void juno_thread_safepoint() {
  juno_thread_slice_over();
}

extern "C" void juno_thread_backedge() {
  juno_thread_slice_over();
}

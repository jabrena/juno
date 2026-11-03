static void juno_thread_slice_over() {
  if (juno_live_threads == 0u) return;
  uint32_t now = static_cast<uint32_t>(millis());
  if (now - juno_slice_start < JUNO_TIME_SLICE_MILLIS) return;
  juno_slice_start = now;
  juno_sched_block();
}

// Method entry: a safepoint.
extern "C" void juno_thread_safepoint() {
  juno_thread_slice_over();
}

extern "C" void juno_thread_backedge() {
  yield();
  juno_thread_slice_over();
}

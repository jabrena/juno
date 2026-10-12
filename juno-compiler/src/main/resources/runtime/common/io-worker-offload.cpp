
extern "C" void juno_io_offload(void (*job)(void*), void* argument) {
  if (juno_live_threads == 0u && !juno_port_io_busy()) {
    job(argument);
    return;
  }
  // A cancelled caller's job may still be finishing.
  while (juno_port_io_busy()) juno_thread_sleep(1);
  juno_io_offloader = juno_current_slot + 1u;
  juno_port_io_dispatch(job, argument);
  juno_slots[juno_current_slot].state = JUNO_SLOT_WAITING_IO;
  juno_sched_block();
  juno_io_offloader = 0u;
}

// Once the worker is idle: run a deferred abort, and free the slots of callers cancelled meanwhile.
static void juno_io_reap() {
  if (juno_port_io_busy()) return;
  if (juno_io_deferred_abort != nullptr) {
    void (*deferred)(void*) = juno_io_deferred_abort;
    juno_io_deferred_abort = nullptr;
    deferred(juno_io_deferred_handle);
  }
  for (uint32_t slot = 1u; slot < JUNO_MAX_THREADS; slot++) {
    if (juno_slots[slot].state == JUNO_SLOT_DRAINING) juno_slots[slot].state = JUNO_SLOT_FREE;
  }
}

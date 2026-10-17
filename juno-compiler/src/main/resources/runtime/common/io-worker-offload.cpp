
extern "C" void juno_io_offload(void (*job)(void*), void* argument) {
  if (juno_live_threads == 0u && !juno_port_io_busy()) {
    job(argument);
    return;
  }
  // A cancelled caller's job may still be finishing. Reserve one of the bounded workers before
  // changing the caller's state, so a second HTTPS lease can make progress independently.
  uint32_t worker = JUNO_IO_WORKER_COUNT;
  while (worker == JUNO_IO_WORKER_COUNT) {
    worker = juno_port_io_dispatch(job, argument);
    if (worker == JUNO_IO_WORKER_COUNT) juno_thread_sleep(1);
  }
  juno_io_offloader[worker] = juno_current_slot + 1u;
  juno_io_worker_for_slot[juno_current_slot] = worker + 1u;
  juno_slots[juno_current_slot].state = JUNO_SLOT_WAITING_IO;
  juno_sched_block();
  juno_io_worker_for_slot[juno_current_slot] = 0u;
  if (juno_io_offloader[worker] == juno_current_slot + 1u) juno_io_offloader[worker] = 0u;
}

static bool juno_io_waiting(uint32_t slot) {
  uint32_t worker = juno_io_worker_for_slot[slot];
  return worker != 0u && juno_port_io_busy(worker - 1u);
}

// Once a worker is idle: run a deferred abort, release its cancelled caller's device leases, and
// free the caller's draining slot once its arrays can no longer be touched.
static void juno_io_reap() {
  for (uint32_t worker = 0u; worker < JUNO_IO_WORKER_COUNT; worker++) {
    if (juno_port_io_busy(worker)) continue;
    if (juno_io_deferred_abort[worker] != nullptr) {
      void (*deferred)(void*) = juno_io_deferred_abort[worker];
      juno_io_deferred_abort[worker] = nullptr;
      deferred(juno_io_deferred_handle[worker]);
    }
    uint32_t owner = juno_io_deferred_owner[worker];
    if (owner != 0u) {
      for (uint32_t device = 0u; device < JUNO_IO_OWNED_DEVICES; device++) {
        if (juno_io_owners[device].owner == owner) juno_io_owners[device] = {0u, 0u, nullptr, nullptr};
      }
      juno_io_deferred_owner[worker] = 0u;
    }
  }
  for (uint32_t slot = 1u; slot < JUNO_MAX_THREADS; slot++) {
    if (juno_slots[slot].state == JUNO_SLOT_DRAINING && !juno_io_waiting(slot)) {
      juno_slots[slot].state = JUNO_SLOT_FREE;
      juno_io_worker_for_slot[slot] = 0u;
    }
  }
}

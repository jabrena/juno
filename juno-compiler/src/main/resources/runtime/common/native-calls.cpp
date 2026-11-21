
// ---- Native calls: a library call that blocks for long (a TLS handshake) made outside the runtime, so the other
// threads run meanwhile. Only on ports where every thread has a native stack of its own. The calling thread hands
// the CPU to the next runnable thread without waiting for it, makes the call, then raises its flag and waits for the
// CPU like any other thread; the scheduler counts a native thread runnable once its flag is up. The flag and the
// baton it waits on are fetched while the thread still owns the runtime, since the slot table can be reallocated
// meanwhile, and its stack stays a collector root. With no other thread runnable nothing is handed over. ----
static bool juno_native_leave(JunoNativeCall& call) {
  call.released = false;
  if (juno_live_threads == 0u) return false;
  uint32_t to = juno_sched_pick(false);
  if (to == JUNO_NO_SLOT) return false;
  uint32_t from = juno_current_slot;
  call.returned = juno_port_native_flag(from);
  call.handle = juno_port_native_handle(from);
  *call.returned = 0u;
  juno_port_native_mark_stack(from);
  juno_slots[from].state = JUNO_SLOT_NATIVE;
  ${JUNO_PENDING_SWAP}
  ${JUNO_BINDINGS_SWAP}
  juno_current_slot = to;
  juno_slice_start = static_cast<uint32_t>(millis());
  call.released = true;
  juno_port_signal(to);
  return true;
}
${JUNO_NATIVE_STOP}
static void juno_native_return(JunoNativeCall& call) {
  if (!call.released) return;
  *call.returned = 1u;
  juno_port_native_wait(call.handle);
  ${JUNO_NATIVE_CANCEL_CHECK}juno_slots[juno_current_slot].state = JUNO_SLOT_READY;
}

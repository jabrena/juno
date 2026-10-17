
struct JunoIoOwner {
  uint32_t owner;
  uint32_t depth;
  void* handle;
  void (*abort)(void*);
};
static constexpr uint32_t JUNO_HTTP_MAX_IN_FLIGHT = ${JUNO_HTTP_MAX_IN_FLIGHT}u;
static constexpr uint32_t JUNO_IO_HTTP_FIRST_OWNER = 0u;
static constexpr uint32_t JUNO_IO_NETWORK_OWNER = 2u;
static constexpr uint32_t JUNO_IO_OWNED_DEVICES = 5u;
static JunoIoOwner juno_io_owners[JUNO_IO_OWNED_DEVICES];
${WORKER_STATE}
extern "C" void juno_thread_sleep(int64_t millis);

static bool juno_http_in_use_by_other(uint32_t owner) {
  for (uint32_t index = 0u; index < JUNO_HTTP_MAX_IN_FLIGHT; index++) {
    uint32_t heldBy = juno_io_owners[JUNO_IO_HTTP_FIRST_OWNER + index].owner;
    if (heldBy != 0u && heldBy != owner) return true;
  }
  return false;
}

extern "C" void juno_io_acquire(uint32_t device, void* handle, void (*abort)(void*)) {${ON_WORKER}
  // A device call is the deepest the runtime goes on a thread's stack: catch an overflow here, before
  // the call's own frames are used.
  juno_port_check_stack(juno_current_slot);
  uint32_t self = juno_current_slot + 1u;
  JunoIoOwner& entry = juno_io_owners[device];
  while ((entry.owner != 0u && entry.owner != self)
      || (device == JUNO_IO_NETWORK_OWNER && juno_http_in_use_by_other(self))) juno_thread_sleep(1);
  if (entry.owner == self) {
    entry.depth++;
    if (entry.abort == nullptr) {
      entry.handle = handle;
      entry.abort = abort;
    }
    return;
  }
  entry = {self, 1u, handle, abort};
}

extern "C" uint32_t juno_http_acquire(void* handle, void (*abort)(void*)) {${ON_HTTP_WORKER}
  juno_port_check_stack(juno_current_slot);
  uint32_t self = juno_current_slot + 1u;
  JunoIoOwner& network = juno_io_owners[JUNO_IO_NETWORK_OWNER];
  for (;;) {
    while (network.owner != 0u && network.owner != self) juno_thread_sleep(1);
    for (uint32_t index = 0u; index < JUNO_HTTP_MAX_IN_FLIGHT; index++) {
      uint32_t device = JUNO_IO_HTTP_FIRST_OWNER + index;
      JunoIoOwner& entry = juno_io_owners[device];
      if (entry.owner == self) {
        entry.depth++;
        if (entry.abort == nullptr) {
          entry.handle = handle;
          entry.abort = abort;
        }
        return device;
      }
      if (entry.owner == 0u) {
        entry = {self, 1u, handle, abort};
        return device;
      }
    }
    juno_thread_sleep(1);
  }
}

extern "C" void juno_io_release(uint32_t device) {${ON_WORKER}
  JunoIoOwner& entry = juno_io_owners[device];
  if (entry.owner != juno_current_slot + 1u) return;
  if (--entry.depth == 0u) entry = {0u, 0u, nullptr, nullptr};
}

extern "C" void juno_http_release(uint32_t device) {
  juno_io_release(device);
}

// A cancelled thread never leaves its library call: abort what it left half done and free the device.
static void juno_io_release_owned(uint32_t owner) {${OFFLOADING}
  for (uint32_t device = 0u; device < JUNO_IO_OWNED_DEVICES; device++) {
    JunoIoOwner& entry = juno_io_owners[device];
    if (entry.owner != owner) continue;${ABORT}${OFFLOADING_CONTINUE}
    entry = {0u, 0u, nullptr, nullptr};
  }
}
${OFFLOAD}

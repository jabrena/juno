
// Handles are the arena addresses themselves, as everywhere else in this shim.
static int32_t juno_big_handle_of(const void* pointer) {
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(pointer));
}

static int32_t* juno_big_pointer(int32_t handle) {
  return reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
}

static const char* juno_big_pointer_chars(int32_t handle) {
  return reinterpret_cast<const char*>(static_cast<intptr_t>(handle));
}

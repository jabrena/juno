
extern "C" int32_t juno_memory_arena_used() {
  return static_cast<int32_t>(juno_arena_used);
}

extern "C" int32_t juno_memory_arena_capacity() {
  return static_cast<int32_t>(sizeof(juno_arena));
}

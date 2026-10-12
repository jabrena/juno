
extern "C" void juno_random_seed(int32_t seed) {
  randomSeed(static_cast<unsigned long>(static_cast<uint32_t>(seed)));
}

extern "C" int32_t juno_random_next_bound(int32_t bound) {
  return static_cast<int32_t>(random(static_cast<long>(bound)));
}

extern "C" int32_t juno_random_next_range(int32_t origin, int32_t bound) {
  return static_cast<int32_t>(random(static_cast<long>(origin), static_cast<long>(bound)));
}

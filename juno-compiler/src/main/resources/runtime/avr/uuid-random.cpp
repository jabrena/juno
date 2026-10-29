// The classic UNO has no hardware entropy source. This deliberately weak provider combines Arduino's PRNG,
// micros() jitter and samples from an unused, floating A5 pin. It is suitable for a best-effort node identity only.
static uint32_t juno_uuid_mix(uint32_t state, uint32_t value) {
  state ^= value + 0x9e3779b9u + (state << 6) + (state >> 2);
  state ^= state << 13;
  state ^= state >> 17;
  state ^= state << 5;
  return state;
}

extern "C" bool juno_uuid_random_bytes(uint8_t* target, uint32_t length) {
  if (length != 16u) return false;
  uint32_t seed = static_cast<uint32_t>(micros());
  for (uint8_t index = 0; index < 64u; index++) {
    uint32_t sample = static_cast<uint32_t>(analogRead(A5));
    sample ^= static_cast<uint32_t>(digitalRead(A5)) << 8;
    sample ^= static_cast<uint32_t>(micros()) << (index & 7u);
    seed = juno_uuid_mix(seed, sample);
  }
  randomSeed(seed);
  for (uint8_t index = 0; index < 16u; index++) {
    uint32_t value = static_cast<uint32_t>(random());
    value = juno_uuid_mix(value, static_cast<uint32_t>(micros()));
    target[index] = static_cast<uint8_t>(value >> ((index & 3u) * 8u));
  }
  return true;
}

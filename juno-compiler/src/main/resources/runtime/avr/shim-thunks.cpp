
// Argument words of the current call into the shim, stored by the generated assembly (see AvrShimThunks).
extern "C" uint32_t juno_avr_args[];

static inline uint64_t juno_avr_arg64(uint8_t index) {
  return juno_avr_args[index] | (static_cast<uint64_t>(juno_avr_args[index + 1]) << 32);
}

static inline float juno_avr_arg_float(uint8_t index) {
  float value;
  memcpy(&value, &juno_avr_args[index], sizeof value);
  return value;
}

static inline int32_t juno_avr_float_bits(float value) {
  int32_t bits;
  memcpy(&bits, &value, sizeof bits);
  return bits;
}


extern "C" JUNO_ASM_ABI double juno_dadd(double a, double b) { return a + b; }
extern "C" JUNO_ASM_ABI double juno_dsub(double a, double b) { return a - b; }
extern "C" JUNO_ASM_ABI double juno_dmul(double a, double b) { return a * b; }
extern "C" JUNO_ASM_ABI double juno_ddiv(double a, double b) { return a / b; }
extern "C" JUNO_ASM_ABI double juno_drem(double a, double b) { return fmod(a, b); }
extern "C" JUNO_ASM_ABI int32_t juno_dcmp(double a, double b, int32_t nanResult) {
  if (isnan(a) || isnan(b)) return nanResult;
  return (a > b) - (a < b);
}
extern "C" JUNO_ASM_ABI int32_t juno_double_compare(double a, double b) {
  if (isnan(a)) return isnan(b) ? 0 : 1;
  if (isnan(b)) return -1;
  if (a == b) {
    if (a == 0.0 && signbit(a) != signbit(b)) return signbit(a) ? -1 : 1;
    return 0;
  }
  return a < b ? -1 : 1;
}
extern "C" JUNO_ASM_ABI int32_t juno_double_hash_code(double value) {
  uint64_t bits;
  if (isnan(value)) bits = 0x7FF8000000000000ull; else memcpy(&bits, &value, sizeof(bits));
  return static_cast<int32_t>(bits ^ (bits >> 32));
}
extern "C" JUNO_ASM_ABI double juno_i2d(int32_t value) { return static_cast<double>(value); }
extern "C" JUNO_ASM_ABI int32_t juno_d2i(double value) {
  if (isnan(value)) return 0;
  if (value >= 0x1.0p31) return INT32_MAX;
  if (value <= -0x1.0p31) return INT32_MIN;
  return static_cast<int32_t>(value);
}
extern "C" JUNO_ASM_ABI double juno_l2d(int64_t value) { return static_cast<double>(value); }
extern "C" JUNO_ASM_ABI int64_t juno_d2l(double value) {
  if (isnan(value)) return 0;
  if (value >= 0x1.0p63) return INT64_MAX;
  if (value <= -0x1.0p63) return INT64_MIN;
  return static_cast<int64_t>(value);
}
extern "C" JUNO_ASM_ABI double juno_f2d(float value) { return static_cast<double>(value); }
extern "C" JUNO_ASM_ABI float juno_d2f(double value) { return static_cast<float>(value); }


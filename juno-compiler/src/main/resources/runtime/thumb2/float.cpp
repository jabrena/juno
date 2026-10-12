
extern "C" JUNO_ASM_ABI float juno_fadd(float a, float b) { return a + b; }
extern "C" JUNO_ASM_ABI float juno_fsub(float a, float b) { return a - b; }
extern "C" JUNO_ASM_ABI float juno_fmul(float a, float b) { return a * b; }
extern "C" JUNO_ASM_ABI float juno_fdiv(float a, float b) { return a / b; }
extern "C" JUNO_ASM_ABI float juno_frem(float a, float b) { return fmodf(a, b); }
extern "C" JUNO_ASM_ABI int32_t juno_fcmp(float a, float b, int32_t nanResult) {
  if (isnan(a) || isnan(b)) return nanResult;
  return (a > b) - (a < b);
}
extern "C" JUNO_ASM_ABI float juno_i2f(int32_t value) { return static_cast<float>(value); }
extern "C" JUNO_ASM_ABI int32_t juno_f2i(float value) {
  if (isnan(value)) return 0;
  if (value >= 0x1.0p31f) return INT32_MAX;
  if (value <= -0x1.0p31f) return INT32_MIN;
  return static_cast<int32_t>(value);
}
extern "C" JUNO_ASM_ABI float juno_l2f(int64_t value) { return static_cast<float>(value); }
extern "C" JUNO_ASM_ABI int64_t juno_f2l(float value) {
  if (isnan(value)) return 0;
  if (value >= 0x1.0p63f) return INT64_MAX;
  if (value <= -0x1.0p63f) return INT64_MIN;
  return static_cast<int64_t>(value);
}


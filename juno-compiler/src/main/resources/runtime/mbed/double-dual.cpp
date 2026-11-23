// ---- double helpers for the Portenta H7's two carriers. ----
// Generated code calls these for double arithmetic on either core, but only the Cortex-M7 has a double-precision FPU:
// on the Cortex-M4 a double instruction faults. On the M7 each helper stays one FPU instruction, kept out of line so
// no double instruction is ever reached on the M4; on the M4 it runs libgcc's integer routines, the ARM run-time ABI's
// __aeabi_* functions. Comparisons and hashes work on the bits, the same on both cores.
extern "C" {
JUNO_ASM_ABI double __aeabi_dadd(double, double);
JUNO_ASM_ABI double __aeabi_dsub(double, double);
JUNO_ASM_ABI double __aeabi_dmul(double, double);
JUNO_ASM_ABI double __aeabi_ddiv(double, double);
JUNO_ASM_ABI double __aeabi_i2d(int32_t);
JUNO_ASM_ABI int32_t __aeabi_d2iz(double);
JUNO_ASM_ABI double __aeabi_l2d(int64_t);
JUNO_ASM_ABI int64_t __aeabi_d2lz(double);
JUNO_ASM_ABI double __aeabi_f2d(float);
JUNO_ASM_ABI float __aeabi_d2f(double);
}

static inline bool juno_double_fpu() {
  return ((SCB->CPUID >> SCB_CPUID_PARTNO_Pos) & 0xFFFu) != 0xC24u;
}

static inline uint64_t juno_double_bits(double value) {
  uint64_t bits;
  memcpy(&bits, &value, sizeof(bits));
  return bits;
}

static inline double juno_bits_double(uint64_t bits) {
  double value;
  memcpy(&value, &bits, sizeof(value));
  return value;
}

static inline bool juno_double_nan(uint64_t bits) {
  return (bits << 1) > 0xFFE0000000000000ull;
}

// The order of Double.compare as a signed integer order: -0.0 below 0.0, every NaN above +Infinity.
static inline int64_t juno_double_order(double value) {
  uint64_t bits = juno_double_bits(value);
  if (juno_double_nan(bits)) bits = 0x7FF8000000000000ull;
  int64_t order = static_cast<int64_t>(bits);
  return order < 0 ? order ^ INT64_MAX : order;
}

// x % y on the bits: musl's fmod (MIT licence, www.musl-libc.org), with its two floating-point results (NaN and a
// signed zero) written as bit patterns.
static double juno_soft_fmod(double x, double y) {
  uint64_t uxi = juno_double_bits(x);
  uint64_t uyi = juno_double_bits(y);
  int32_t ex = static_cast<int32_t>(uxi >> 52 & 0x7FF);
  int32_t ey = static_cast<int32_t>(uyi >> 52 & 0x7FF);
  uint64_t sign = uxi & 0x8000000000000000ull;
  uint64_t i;
  if (uyi << 1 == 0 || juno_double_nan(uyi) || ex == 0x7FF) return juno_bits_double(0x7FF8000000000000ull);
  if (uxi << 1 <= uyi << 1) return uxi << 1 == uyi << 1 ? juno_bits_double(sign) : x;
  if (ex == 0) {
    for (i = uxi << 12; i >> 63 == 0; ex--, i <<= 1) {
    }
    uxi <<= -ex + 1;
  } else {
    uxi &= UINT64_MAX >> 12;
    uxi |= 1ull << 52;
  }
  if (ey == 0) {
    for (i = uyi << 12; i >> 63 == 0; ey--, i <<= 1) {
    }
    uyi <<= -ey + 1;
  } else {
    uyi &= UINT64_MAX >> 12;
    uyi |= 1ull << 52;
  }
  for (; ex > ey; ex--) {
    i = uxi - uyi;
    if (i >> 63 == 0) {
      if (i == 0) return juno_bits_double(sign);
      uxi = i;
    }
    uxi <<= 1;
  }
  i = uxi - uyi;
  if (i >> 63 == 0) {
    if (i == 0) return juno_bits_double(sign);
    uxi = i;
  }
  for (; uxi >> 52 == 0; uxi <<= 1, ex--) {
  }
  if (ex > 0) {
    uxi -= 1ull << 52;
    uxi |= static_cast<uint64_t>(ex) << 52;
  } else {
    uxi >>= -ex + 1;
  }
  return juno_bits_double(uxi | sign);
}

#define JUNO_DOUBLE_FPU __attribute__((noinline)) JUNO_ASM_ABI
static JUNO_DOUBLE_FPU double juno_fpu_dadd(double a, double b) { return a + b; }
static JUNO_DOUBLE_FPU double juno_fpu_dsub(double a, double b) { return a - b; }
static JUNO_DOUBLE_FPU double juno_fpu_dmul(double a, double b) { return a * b; }
static JUNO_DOUBLE_FPU double juno_fpu_ddiv(double a, double b) { return a / b; }
static JUNO_DOUBLE_FPU double juno_fpu_drem(double a, double b) { return fmod(a, b); }
static JUNO_DOUBLE_FPU double juno_fpu_i2d(int32_t value) { return static_cast<double>(value); }
static JUNO_DOUBLE_FPU int32_t juno_fpu_d2i(double value) { return static_cast<int32_t>(value); }
static JUNO_DOUBLE_FPU double juno_fpu_l2d(int64_t value) { return static_cast<double>(value); }
static JUNO_DOUBLE_FPU int64_t juno_fpu_d2l(double value) { return static_cast<int64_t>(value); }
static JUNO_DOUBLE_FPU double juno_fpu_f2d(float value) { return static_cast<double>(value); }
static JUNO_DOUBLE_FPU float juno_fpu_d2f(double value) { return static_cast<float>(value); }

extern "C" JUNO_ASM_ABI double juno_dadd(double a, double b) {
  return juno_double_fpu() ? juno_fpu_dadd(a, b) : __aeabi_dadd(a, b);
}
extern "C" JUNO_ASM_ABI double juno_dsub(double a, double b) {
  return juno_double_fpu() ? juno_fpu_dsub(a, b) : __aeabi_dsub(a, b);
}
extern "C" JUNO_ASM_ABI double juno_dmul(double a, double b) {
  return juno_double_fpu() ? juno_fpu_dmul(a, b) : __aeabi_dmul(a, b);
}
extern "C" JUNO_ASM_ABI double juno_ddiv(double a, double b) {
  return juno_double_fpu() ? juno_fpu_ddiv(a, b) : __aeabi_ddiv(a, b);
}
extern "C" JUNO_ASM_ABI double juno_drem(double a, double b) {
  return juno_double_fpu() ? juno_fpu_drem(a, b) : juno_soft_fmod(a, b);
}
extern "C" JUNO_ASM_ABI int32_t juno_dcmp(double a, double b, int32_t nanResult) {
  if (juno_double_nan(juno_double_bits(a)) || juno_double_nan(juno_double_bits(b))) return nanResult;
  int64_t left = juno_double_order(a);
  int64_t right = juno_double_order(b);
  // dcmp follows ==, where -0.0 equals 0.0.
  if (((juno_double_bits(a) | juno_double_bits(b)) << 1) == 0) return 0;
  return (left > right) - (left < right);
}
extern "C" JUNO_ASM_ABI int32_t juno_double_compare(double a, double b) {
  int64_t left = juno_double_order(a);
  int64_t right = juno_double_order(b);
  return (left > right) - (left < right);
}
extern "C" JUNO_ASM_ABI int32_t juno_double_hash_code(double value) {
  uint64_t bits = juno_double_bits(value);
  if (juno_double_nan(bits)) bits = 0x7FF8000000000000ull;
  return static_cast<int32_t>(bits ^ (bits >> 32));
}
extern "C" JUNO_ASM_ABI double juno_i2d(int32_t value) {
  return juno_double_fpu() ? juno_fpu_i2d(value) : __aeabi_i2d(value);
}
// Java's narrowing: NaN is 0 and out-of-range values saturate, decided on the bits before converting.
extern "C" JUNO_ASM_ABI int32_t juno_d2i(double value) {
  uint64_t bits = juno_double_bits(value);
  if (juno_double_nan(bits)) return 0;
  if (juno_double_order(value) >= juno_double_order(0x1.0p31)) return INT32_MAX;
  if (juno_double_order(value) <= juno_double_order(-0x1.0p31)) return INT32_MIN;
  return juno_double_fpu() ? juno_fpu_d2i(value) : __aeabi_d2iz(value);
}
extern "C" JUNO_ASM_ABI double juno_l2d(int64_t value) {
  return juno_double_fpu() ? juno_fpu_l2d(value) : __aeabi_l2d(value);
}
extern "C" JUNO_ASM_ABI int64_t juno_d2l(double value) {
  uint64_t bits = juno_double_bits(value);
  if (juno_double_nan(bits)) return 0;
  if (juno_double_order(value) >= juno_double_order(0x1.0p63)) return INT64_MAX;
  if (juno_double_order(value) <= juno_double_order(-0x1.0p63)) return INT64_MIN;
  return juno_double_fpu() ? juno_fpu_d2l(value) : __aeabi_d2lz(value);
}
extern "C" JUNO_ASM_ABI double juno_f2d(float value) {
  return juno_double_fpu() ? juno_fpu_f2d(value) : __aeabi_f2d(value);
}
extern "C" JUNO_ASM_ABI float juno_d2f(double value) {
  return juno_double_fpu() ? juno_fpu_d2f(value) : __aeabi_d2f(value);
}

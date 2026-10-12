
// Java float/double semantics in software, for targets whose own double is narrower than IEEE binary64 (AVR).
// A juno_f64 is the raw binary64 bit pattern; float values are binary32 bit patterns in a uint32_t. Rounding is
// always round-to-nearest-even with gradual underflow, as the JVM requires. binary32 arithmetic is computed in
// binary64 and rounded once more: for +, -, *, / and sqrt that double rounding is innocuous (53 >= 2 * 24 + 2).
typedef uint64_t juno_f64;

static const juno_f64 JUNO_F64_DEFAULT_NAN = 0x7FF8000000000000ull;
static const uint32_t JUNO_F32_DEFAULT_NAN = 0x7FC00000ul;

static inline bool juno_f64_sign(juno_f64 a) { return (a >> 63) != 0; }
static inline int16_t juno_f64_exp(juno_f64 a) { return static_cast<int16_t>((a >> 52) & 0x7FF); }
static inline uint64_t juno_f64_frac(juno_f64 a) { return a & 0x000FFFFFFFFFFFFFull; }
static inline bool juno_f64_is_nan(juno_f64 a) { return juno_f64_exp(a) == 0x7FF && juno_f64_frac(a) != 0; }
static inline juno_f64 juno_f64_pack(bool sign, int16_t exp, uint64_t sig) {
  return (static_cast<uint64_t>(sign) << 63) + (static_cast<uint64_t>(exp) << 52) + sig;
}

static uint64_t juno_shift_right_jam64(uint64_t value, uint16_t distance) {
  if (distance < 63) {
    return (value >> distance) | ((value << (-distance & 63)) != 0 ? 1u : 0u);
  }
  return value != 0 ? 1u : 0u;
}

static uint8_t juno_count_leading_zeros64(uint64_t value) {
  uint8_t count = 0;
  if ((value >> 32) == 0) { count = 32; value <<= 32; }
  if ((value >> 48) == 0) { count += 16; value <<= 16; }
  if ((value >> 56) == 0) { count += 8; value <<= 8; }
  while ((value >> 63) == 0) { count++; value <<= 1; }
  return count;
}

// sig has its leading one at bit 62 for normal results (10 rounding bits below the 53-bit significand);
// exp is the biased exponent minus one, so the leading bit carries into the exponent field when packed.
static juno_f64 juno_f64_round_pack(bool sign, int16_t exp, uint64_t sig) {
  uint16_t roundBits = static_cast<uint16_t>(sig & 0x3FF);
  if (0x7FD <= static_cast<uint16_t>(exp)) {
    if (exp < 0) {
      sig = juno_shift_right_jam64(sig, static_cast<uint16_t>(-exp));
      exp = 0;
      roundBits = static_cast<uint16_t>(sig & 0x3FF);
    } else if (0x7FD < exp || 0x8000000000000000ull <= sig + 0x200) {
      return juno_f64_pack(sign, 0x7FF, 0);
    }
  }
  sig = (sig + 0x200) >> 10;
  if (roundBits == 0x200) sig &= ~static_cast<uint64_t>(1);
  if (sig == 0) exp = 0;
  return juno_f64_pack(sign, exp, sig);
}

static juno_f64 juno_f64_norm_round_pack(bool sign, int16_t exp, uint64_t sig) {
  int8_t shift = static_cast<int8_t>(juno_count_leading_zeros64(sig) - 1);
  exp = static_cast<int16_t>(exp - shift);
  if (10 <= shift && static_cast<uint16_t>(exp) < 0x7FD) {
    return juno_f64_pack(sign, sig != 0 ? exp : 0, sig << (shift - 10));
  }
  return juno_f64_round_pack(sign, exp, sig << shift);
}

// Normalizes a subnormal's fraction: returns the exponent and leaves the significand with its leading one at bit 52.
static int16_t juno_f64_normalize_subnormal(uint64_t* sig) {
  int8_t shift = static_cast<int8_t>(juno_count_leading_zeros64(*sig) - 11);
  *sig <<= shift;
  return static_cast<int16_t>(1 - shift);
}

static juno_f64 juno_f64_propagate_nan(juno_f64 a, juno_f64 b) {
  static_cast<void>(a);
  static_cast<void>(b);
  return JUNO_F64_DEFAULT_NAN;
}

static juno_f64 juno_f64_add_mags(juno_f64 a, juno_f64 b, bool sign) {
  int16_t expA = juno_f64_exp(a);
  uint64_t sigA = juno_f64_frac(a);
  int16_t expB = juno_f64_exp(b);
  uint64_t sigB = juno_f64_frac(b);
  int16_t expDiff = static_cast<int16_t>(expA - expB);
  int16_t expZ;
  uint64_t sigZ;
  if (expDiff == 0) {
    if (expA == 0) return juno_f64_pack(sign, 0, sigA + sigB);
    if (expA == 0x7FF) return (sigA | sigB) != 0 ? juno_f64_propagate_nan(a, b) : a;
    expZ = expA;
    sigZ = (0x0020000000000000ull + sigA + sigB) << 9;
  } else {
    sigA <<= 9;
    sigB <<= 9;
    if (expDiff < 0) {
      if (expB == 0x7FF) return sigB != 0 ? juno_f64_propagate_nan(a, b) : juno_f64_pack(sign, 0x7FF, 0);
      expZ = expB;
      if (expA != 0) sigA += 0x2000000000000000ull; else sigA <<= 1;
      sigA = juno_shift_right_jam64(sigA, static_cast<uint16_t>(-expDiff));
    } else {
      if (expA == 0x7FF) return sigA != 0 ? juno_f64_propagate_nan(a, b) : a;
      expZ = expA;
      if (expB != 0) sigB += 0x2000000000000000ull; else sigB <<= 1;
      sigB = juno_shift_right_jam64(sigB, static_cast<uint16_t>(expDiff));
    }
    sigZ = 0x2000000000000000ull + sigA + sigB;
    if (sigZ < 0x4000000000000000ull) {
      --expZ;
      sigZ <<= 1;
    }
  }
  return juno_f64_round_pack(sign, expZ, sigZ);
}

static juno_f64 juno_f64_sub_mags(juno_f64 a, juno_f64 b, bool sign) {
  int16_t expA = juno_f64_exp(a);
  uint64_t sigA = juno_f64_frac(a);
  int16_t expB = juno_f64_exp(b);
  uint64_t sigB = juno_f64_frac(b);
  int16_t expDiff = static_cast<int16_t>(expA - expB);
  if (expDiff == 0) {
    if (expA == 0x7FF) return (sigA | sigB) != 0 ? juno_f64_propagate_nan(a, b) : JUNO_F64_DEFAULT_NAN;
    int64_t sigDiff = static_cast<int64_t>(sigA - sigB);
    if (sigDiff == 0) return juno_f64_pack(false, 0, 0);
    if (expA != 0) --expA;
    if (sigDiff < 0) {
      sign = !sign;
      sigDiff = -sigDiff;
    }
    int8_t shift = static_cast<int8_t>(juno_count_leading_zeros64(static_cast<uint64_t>(sigDiff)) - 11);
    int16_t expZ = static_cast<int16_t>(expA - shift);
    if (expZ < 0) {
      shift = static_cast<int8_t>(expA);
      expZ = 0;
    }
    return juno_f64_pack(sign, expZ, static_cast<uint64_t>(sigDiff) << shift);
  }
  sigA <<= 10;
  sigB <<= 10;
  int16_t expZ;
  uint64_t sigZ;
  if (expDiff < 0) {
    sign = !sign;
    if (expB == 0x7FF) return sigB != 0 ? juno_f64_propagate_nan(a, b) : juno_f64_pack(sign, 0x7FF, 0);
    sigA += expA != 0 ? 0x4000000000000000ull : sigA;
    sigA = juno_shift_right_jam64(sigA, static_cast<uint16_t>(-expDiff));
    sigB |= 0x4000000000000000ull;
    expZ = expB;
    sigZ = sigB - sigA;
  } else {
    if (expA == 0x7FF) return sigA != 0 ? juno_f64_propagate_nan(a, b) : a;
    sigB += expB != 0 ? 0x4000000000000000ull : sigB;
    sigB = juno_shift_right_jam64(sigB, static_cast<uint16_t>(expDiff));
    sigA |= 0x4000000000000000ull;
    expZ = expA;
    sigZ = sigA - sigB;
  }
  return juno_f64_norm_round_pack(sign, static_cast<int16_t>(expZ - 1), sigZ);
}

static juno_f64 juno_f64_add(juno_f64 a, juno_f64 b) {
  bool signA = juno_f64_sign(a);
  return signA == juno_f64_sign(b) ? juno_f64_add_mags(a, b, signA) : juno_f64_sub_mags(a, b, signA);
}

static juno_f64 juno_f64_sub(juno_f64 a, juno_f64 b) {
  bool signA = juno_f64_sign(a);
  return signA == juno_f64_sign(b) ? juno_f64_sub_mags(a, b, signA) : juno_f64_add_mags(a, b, signA);
}

// High 64 bits of a 64x64-bit product, the low 64 bits ORed into bit 0 (sticky).
static uint64_t juno_mul64_jam(uint64_t a, uint64_t b) {
  uint32_t a0 = static_cast<uint32_t>(a);
  uint32_t a1 = static_cast<uint32_t>(a >> 32);
  uint32_t b0 = static_cast<uint32_t>(b);
  uint32_t b1 = static_cast<uint32_t>(b >> 32);
  uint64_t p00 = static_cast<uint64_t>(a0) * b0;
  uint64_t p01 = static_cast<uint64_t>(a0) * b1;
  uint64_t p10 = static_cast<uint64_t>(a1) * b0;
  uint64_t p11 = static_cast<uint64_t>(a1) * b1;
  uint64_t middle = (p00 >> 32) + static_cast<uint32_t>(p01) + static_cast<uint32_t>(p10);
  uint64_t low = (middle << 32) | static_cast<uint32_t>(p00);
  uint64_t high = p11 + (p01 >> 32) + (p10 >> 32) + (middle >> 32);
  return high | (low != 0 ? 1u : 0u);
}

static juno_f64 juno_f64_mul(juno_f64 a, juno_f64 b) {
  bool signZ = juno_f64_sign(a) != juno_f64_sign(b);
  int16_t expA = juno_f64_exp(a);
  uint64_t sigA = juno_f64_frac(a);
  int16_t expB = juno_f64_exp(b);
  uint64_t sigB = juno_f64_frac(b);
  if (expA == 0x7FF) {
    if (sigA != 0 || (expB == 0x7FF && sigB != 0)) return juno_f64_propagate_nan(a, b);
    if ((expB | static_cast<int16_t>(sigB != 0)) == 0) return JUNO_F64_DEFAULT_NAN;
    return juno_f64_pack(signZ, 0x7FF, 0);
  }
  if (expB == 0x7FF) {
    if (sigB != 0) return juno_f64_propagate_nan(a, b);
    if ((expA | static_cast<int16_t>(sigA != 0)) == 0) return JUNO_F64_DEFAULT_NAN;
    return juno_f64_pack(signZ, 0x7FF, 0);
  }
  if (expA == 0) {
    if (sigA == 0) return juno_f64_pack(signZ, 0, 0);
    expA = juno_f64_normalize_subnormal(&sigA);
  }
  if (expB == 0) {
    if (sigB == 0) return juno_f64_pack(signZ, 0, 0);
    expB = juno_f64_normalize_subnormal(&sigB);
  }
  int16_t expZ = static_cast<int16_t>(expA + expB - 0x3FF);
  sigA = (sigA | 0x0010000000000000ull) << 10;
  sigB = (sigB | 0x0010000000000000ull) << 11;
  uint64_t sigZ = juno_mul64_jam(sigA, sigB);
  if (sigZ < 0x4000000000000000ull) {
    --expZ;
    sigZ <<= 1;
  }
  return juno_f64_round_pack(signZ, expZ, sigZ);
}

static juno_f64 juno_f64_div(juno_f64 a, juno_f64 b) {
  bool signZ = juno_f64_sign(a) != juno_f64_sign(b);
  int16_t expA = juno_f64_exp(a);
  uint64_t sigA = juno_f64_frac(a);
  int16_t expB = juno_f64_exp(b);
  uint64_t sigB = juno_f64_frac(b);
  if (expA == 0x7FF) {
    if (sigA != 0) return juno_f64_propagate_nan(a, b);
    if (expB == 0x7FF) return sigB != 0 ? juno_f64_propagate_nan(a, b) : JUNO_F64_DEFAULT_NAN;
    return juno_f64_pack(signZ, 0x7FF, 0);
  }
  if (expB == 0x7FF) return sigB != 0 ? juno_f64_propagate_nan(a, b) : juno_f64_pack(signZ, 0, 0);
  if (expB == 0) {
    if (sigB == 0) {
      if ((expA | static_cast<int16_t>(sigA != 0)) == 0) return JUNO_F64_DEFAULT_NAN;
      return juno_f64_pack(signZ, 0x7FF, 0);
    }
    expB = juno_f64_normalize_subnormal(&sigB);
  }
  if (expA == 0) {
    if (sigA == 0) return juno_f64_pack(signZ, 0, 0);
    expA = juno_f64_normalize_subnormal(&sigA);
  }
  int16_t expZ = static_cast<int16_t>(expA - expB + 0x3FE);
  sigA |= 0x0010000000000000ull;
  sigB |= 0x0010000000000000ull;
  if (sigA < sigB) {
    --expZ;
    sigA <<= 1;
  }
  // Restoring division: 62 quotient bits leave the leading one at bit 62 and 9 rounding bits plus a sticky bit.
  uint64_t quotient = 0;
  for (uint8_t bit = 0; bit < 63; bit++) {
    quotient <<= 1;
    if (sigA >= sigB) {
      sigA -= sigB;
      quotient |= 1u;
    }
    sigA <<= 1;
  }
  if (sigA != 0) quotient |= 1u;
  return juno_f64_round_pack(signZ, expZ, quotient);
}

// Java's % (C's fmod): exact, the sign of the dividend.
static juno_f64 juno_f64_rem(juno_f64 a, juno_f64 b) {
  bool signA = juno_f64_sign(a);
  int16_t expA = juno_f64_exp(a);
  uint64_t sigA = juno_f64_frac(a);
  int16_t expB = juno_f64_exp(b);
  uint64_t sigB = juno_f64_frac(b);
  if (expA == 0x7FF || (expB == 0x7FF && sigB != 0)) return JUNO_F64_DEFAULT_NAN;
  if (expB == 0 && sigB == 0) return JUNO_F64_DEFAULT_NAN;
  if (expB == 0x7FF) return a;
  if (expA == 0) {
    if (sigA == 0) return a;
    expA = juno_f64_normalize_subnormal(&sigA);
  }
  if (expB == 0) expB = juno_f64_normalize_subnormal(&sigB);
  sigA |= 0x0010000000000000ull;
  sigB |= 0x0010000000000000ull;
  if (expA < expB || (expA == expB && sigA < sigB)) return a;
  // Long division bit by bit: sigA * 2^(expA - expB) mod sigB.
  for (int16_t diff = static_cast<int16_t>(expA - expB); diff > 0; diff--) {
    if (sigA >= sigB) sigA -= sigB;
    sigA <<= 1;
  }
  if (sigA >= sigB) sigA -= sigB;
  if (sigA == 0) return juno_f64_pack(signA, 0, 0);
  // sigA is now the remainder scaled by 2^(expB - 1 - 0x3FE - 52); renormalize it.
  int16_t expZ = expB;
  while (sigA < 0x0010000000000000ull) {
    sigA <<= 1;
    expZ--;
  }
  if (expZ <= 0) {
    // Subnormal: the remainder is exact, so this shift only drops zero bits.
    sigA >>= (1 - expZ);
    return juno_f64_pack(signA, 0, sigA);
  }
  return juno_f64_pack(signA, expZ, sigA & 0x000FFFFFFFFFFFFFull);
}

static juno_f64 juno_f64_sqrt(juno_f64 a) {
  bool signA = juno_f64_sign(a);
  int16_t expA = juno_f64_exp(a);
  uint64_t sigA = juno_f64_frac(a);
  if (expA == 0x7FF) {
    if (sigA != 0) return juno_f64_propagate_nan(a, 0);
    return signA ? JUNO_F64_DEFAULT_NAN : a;
  }
  if (expA == 0 && sigA == 0) return a;
  if (signA) return JUNO_F64_DEFAULT_NAN;
  if (expA == 0) expA = juno_f64_normalize_subnormal(&sigA);
  sigA |= 0x0010000000000000ull;
  // value = sigA * 2^(expA - 0x3FF - 52); make the exponent even so the root's is exact.
  int16_t unbiased = static_cast<int16_t>(expA - 0x3FF);
  if (unbiased & 1) {
    sigA <<= 1;
    unbiased--;
  }
  // Integer square root of sigA * 2^62, two radicand bits per step. Its 116 bits are sigA << 4 (58 bits) followed by
  // zeros, so the root has 58 bits with its leading one at bit 57, and the remainder stays below 2^61.
  uint64_t window = sigA << 4;
  uint64_t root = 0;
  uint64_t remainder = 0;
  for (uint8_t step = 0; step < 58; step++) {
    remainder = (remainder << 2) | (window >> 56);
    window = (window << 2) & 0x03FFFFFFFFFFFFFFull;
    uint64_t trial = (root << 2) | 1u;
    root <<= 1;
    if (remainder >= trial) {
      remainder -= trial;
      root |= 1u;
    }
  }
  if (remainder != 0) root |= 1u;
  // round_pack wants the leading one at bit 62; value = root * 2^((unbiased - 114) / 2).
  int16_t expZ = static_cast<int16_t>(unbiased / 2 + 0x3FE);
  return juno_f64_round_pack(false, expZ, root << 5);
}

// -1, 0 or 1; 2 when either is NaN. +0.0 and -0.0 compare equal.
static int8_t juno_f64_compare(juno_f64 a, juno_f64 b) {
  if (juno_f64_is_nan(a) || juno_f64_is_nan(b)) return 2;
  if (((a | b) << 1) == 0) return 0;
  bool signA = juno_f64_sign(a);
  bool signB = juno_f64_sign(b);
  if (signA != signB) return signA ? -1 : 1;
  if (a == b) return 0;
  bool less = (a < b) != signA;
  return less ? -1 : 1;
}

static juno_f64 juno_f64_from_i64(int64_t value) {
  bool sign = value < 0;
  uint64_t magnitude = sign ? 0ull - static_cast<uint64_t>(value) : static_cast<uint64_t>(value);
  if ((magnitude & 0x7FFFFFFFFFFFFFFFull) == 0) {
    return sign ? juno_f64_pack(true, 0x43E, 0) : 0;
  }
  return juno_f64_norm_round_pack(sign, 0x43C, magnitude);
}

static juno_f64 juno_f64_from_i32(int32_t value) {
  return juno_f64_from_i64(value);
}

// Java's (long) cast: NaN is 0, out-of-range saturates, everything else truncates toward zero.
static int64_t juno_f64_to_i64(juno_f64 a) {
  bool sign = juno_f64_sign(a);
  int16_t exp = juno_f64_exp(a);
  uint64_t sig = juno_f64_frac(a);
  if (exp == 0x7FF && sig != 0) return 0;
  if (exp < 0x3FF) return 0;
  int16_t shift = static_cast<int16_t>(exp - 0x3FF - 52);
  if (exp - 0x3FF >= 63) return sign ? INT64_MIN : INT64_MAX;
  sig |= 0x0010000000000000ull;
  uint64_t magnitude = shift >= 0 ? sig << shift : sig >> -shift;
  return sign ? static_cast<int64_t>(0ull - magnitude) : static_cast<int64_t>(magnitude);
}

static int32_t juno_f64_to_i32(juno_f64 a) {
  if (juno_f64_is_nan(a)) return 0;
  int64_t value = juno_f64_to_i64(a);
  if (value > INT32_MAX) return INT32_MAX;
  if (value < INT32_MIN) return INT32_MIN;
  return static_cast<int32_t>(value);
}

static juno_f64 juno_f64_from_f32(uint32_t a) {
  bool sign = (a >> 31) != 0;
  int16_t exp = static_cast<int16_t>((a >> 23) & 0xFF);
  uint64_t frac = a & 0x007FFFFFu;
  if (exp == 0xFF) {
    if (frac != 0) return JUNO_F64_DEFAULT_NAN;
    return juno_f64_pack(sign, 0x7FF, 0);
  }
  if (exp == 0) {
    if (frac == 0) return juno_f64_pack(sign, 0, 0);
    // Normalize: shift the leading one up to bit 23.
    exp = 1;
    while ((frac & 0x00800000u) == 0) {
      frac <<= 1;
      exp--;
    }
    frac &= 0x007FFFFFu;
  }
  return juno_f64_pack(sign, static_cast<int16_t>(exp + 0x380), frac << 29);
}

// binary32 round-and-pack: sig has its leading one at bit 30 (7 rounding bits), exp is the biased exponent minus one.
static uint32_t juno_f32_round_pack(bool sign, int16_t exp, uint32_t sig) {
  uint32_t signBit = sign ? 0x80000000ul : 0;
  uint8_t roundBits = static_cast<uint8_t>(sig & 0x7F);
  if (0xFD <= static_cast<uint16_t>(exp)) {
    if (exp < 0) {
      uint16_t distance = static_cast<uint16_t>(-exp);
      sig = distance < 31 ? (sig >> distance) | ((sig << (-distance & 31)) != 0 ? 1u : 0u) : (sig != 0 ? 1u : 0u);
      exp = 0;
      roundBits = static_cast<uint8_t>(sig & 0x7F);
    } else if (0xFD < exp || 0x80000000ul <= sig + 0x40) {
      return signBit | 0x7F800000ul;
    }
  }
  sig = (sig + 0x40) >> 7;
  if (roundBits == 0x40) sig &= ~1ul;
  if (sig == 0) exp = 0;
  return signBit + (static_cast<uint32_t>(exp) << 23) + sig;
}

static uint32_t juno_f64_to_f32(juno_f64 a) {
  bool sign = juno_f64_sign(a);
  int16_t exp = juno_f64_exp(a);
  uint64_t frac = juno_f64_frac(a);
  if (exp == 0x7FF) return frac != 0 ? JUNO_F32_DEFAULT_NAN : (sign ? 0xFF800000ul : 0x7F800000ul);
  // 22 significant bits plus a sticky bit (binary64 subnormals all round to a binary32 zero or minimum).
  uint32_t sig = static_cast<uint32_t>(juno_shift_right_jam64(frac, 22));
  if ((static_cast<uint16_t>(exp) | sig) == 0) return sign ? 0x80000000ul : 0;
  return juno_f32_round_pack(sign, static_cast<int16_t>(exp - 0x381), sig | 0x40000000ul);
}

// Java's (float) of a long: rounded once, straight from the integer.
static uint32_t juno_f32_from_i64(int64_t value) {
  bool sign = value < 0;
  uint64_t magnitude = sign ? 0ull - static_cast<uint64_t>(value) : static_cast<uint64_t>(value);
  if (magnitude == 0) return 0;
  int16_t top = static_cast<int16_t>(63 - juno_count_leading_zeros64(magnitude));
  uint32_t sig = top > 30 ? static_cast<uint32_t>(juno_shift_right_jam64(magnitude, static_cast<uint16_t>(top - 30)))
                          : static_cast<uint32_t>(magnitude << (30 - top));
  return juno_f32_round_pack(sign, static_cast<int16_t>(top + 0x7E), sig);
}

// Round toward negative infinity / positive infinity to an integral value; NaN, infinities and zeros unchanged.
static juno_f64 juno_f64_round_integral(juno_f64 a, bool towardPositive) {
  int16_t exp = juno_f64_exp(a);
  bool sign = juno_f64_sign(a);
  if (exp >= 0x3FF + 52) return a;
  if (exp < 0x3FF) {
    if ((a << 1) == 0) return a;
    bool roundsAway = sign ? !towardPositive : towardPositive;
    if (!roundsAway) return juno_f64_pack(sign, 0, 0);
    return juno_f64_pack(sign, 0x3FF, 0);
  }
  uint64_t fractionMask = 0x000FFFFFFFFFFFFFull >> (exp - 0x3FF);
  if ((a & fractionMask) == 0) return a;
  bool roundsAway = sign ? !towardPositive : towardPositive;
  if (roundsAway) a += fractionMask + 1;
  return a & ~fractionMask;
}

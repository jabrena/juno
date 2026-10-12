
// The 63 bits of |limbs[0, n)| below its top bit's position, a sticky bit for anything lower, rounded to
// binary64 and scaled by 2^binaryExponent.
static juno_f64 juno_big_limbs_to_f64(bool negative, const uint32_t* limbs, uint32_t n,
                                      int32_t binaryExponent, bool sticky) {
  n = juno_big_trim(limbs, n);
  if (n == 0u) return negative ? 0x8000000000000000ull : 0;
  int32_t length = static_cast<int32_t>(32u * n - juno_big_clz(limbs[n - 1u]));
  uint64_t sig = 0;
  for (int32_t i = 0; i < 63; i++) {
    int32_t bit = length - 1 - i;
    sig <<= 1;
    if (bit >= 0 && ((limbs[bit / 32] >> (bit % 32)) & 1u) != 0u) sig |= 1u;
  }
  for (int32_t bit = length - 64; bit >= 0 && !sticky; bit--) {
    if (bit % 32 == 31 && limbs[bit / 32] == 0u) {
      bit -= 31;
      continue;
    }
    sticky = ((limbs[bit / 32] >> (bit % 32)) & 1u) != 0u;
  }
  if (sticky) sig |= 1u;
  // round_pack's sig has its leading one at bit 62 and means sig * 2^(exp - 1022 - 62).
  int32_t exp = length - 63 + binaryExponent + 1084;
  if (exp > 0x7FF) exp = 0x7FF;
  if (exp < -2000) exp = -2000;
  return juno_f64_round_pack(negative, static_cast<int16_t>(exp), sig);
}

extern "C" juno_f64 juno_big_integer_double_value(int32_t a) {
  return juno_big_limbs_to_f64(juno_big_sign_of(a) < 0, juno_big_limbs_of(a), juno_big_len_of(a), 0, false);
}

extern "C" juno_f64 juno_big_decimal_double_value(int32_t a) {
  int32_t unscaled = juno_bd_unscaled(a);
  int32_t scale = juno_bd_scale(a);
  bool negative = juno_big_sign_of(unscaled) < 0;
  if (juno_big_sign_of(unscaled) == 0) return negative ? 0x8000000000000000ull : 0;
  // Far outside binary64's range the answer is known without the arithmetic: |a| >= 10^309 overflows,
  // |a| < 10^-325 rounds to zero.
  int64_t magnitude = static_cast<int64_t>(juno_big_digits_h(unscaled)) - scale;
  if (magnitude > 310) return juno_f64_pack(negative, 0x7FF, 0);
  if (magnitude < -325) return negative ? 0x8000000000000000ull : 0;
  if (scale <= 0) {
    juno_big_prepare(4u * (juno_big_len_of(unscaled) + static_cast<uint32_t>(-scale) / 9u + 8u) * 4u);
    int32_t value = juno_big_scale_up_h(unscaled, static_cast<uint32_t>(-scale));
    return juno_big_limbs_to_f64(negative, juno_big_limbs_of(value), juno_big_len_of(value), 0, false);
  }
  // q = (|unscaled| << shift) / 10^scale keeps at least 66 significant bits; the remainder is sticky.
  uint32_t powerBits = static_cast<uint32_t>(scale) * 10u / 3u + 1u;
  uint32_t unscaledBits = 32u * juno_big_len_of(unscaled)
      - juno_big_clz(juno_big_limbs_of(unscaled)[juno_big_len_of(unscaled) - 1u]);
  uint32_t shift = powerBits + 66u > unscaledBits ? powerBits + 66u - unscaledBits : 0u;
  juno_big_prepare(4u * (juno_big_len_of(unscaled) + shift / 32u + powerBits / 32u + 8u) * 4u);
  uint32_t* bit = juno_big_scratch(shift / 32u + 1u);
  bit[shift / 32u] = static_cast<uint32_t>(1u) << (shift % 32u);
  int32_t numerator = juno_big_mul_h(juno_big_abs_h(unscaled), juno_big_make(1, bit, shift / 32u + 1u));
  int32_t quotient;
  int32_t remainder;
  juno_big_divrem_h(numerator, juno_big_pow10_h(static_cast<uint32_t>(scale)), &quotient, &remainder);
  return juno_big_limbs_to_f64(negative, juno_big_limbs_of(quotient), juno_big_len_of(quotient),
                               -static_cast<int32_t>(shift), juno_big_sign_of(remainder) != 0);
}

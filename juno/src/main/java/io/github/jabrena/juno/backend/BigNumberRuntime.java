package io.github.jabrena.juno.backend;

/**
 * The runtime-shim side of the {@code java.math} subset (see {@code BigNumberMethods}): {@code BigInteger},
 * {@code BigDecimal} and {@code MathContext} as immutable arena blocks, with the JDK's rounding and text rules.
 * The code is exercised against the JDK classes by {@code BigNumberRuntimeTest} (a native build of this exact text)
 * and under QEMU by the {@code BigNumbers} programs.
 */
final class BigNumberRuntime {
    private BigNumberRuntime() {
    }

    /** How a handle maps to an address. A native test build substitutes its own. */
    static String handleHelpers() {
        return """

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
                """;
    }

    /**
     * @param valueOfDouble whether {@code BigDecimal.valueOf(double)} is used; it formats the double with the
     *                      runtime-string helpers, which must then be present
     */
    static String helpers(int arithmeticClassId, int numberFormatClassId, int illegalArgumentClassId,
                          boolean valueOfDouble) {
        return ("""

                // ---- java.math.BigInteger / BigDecimal / MathContext runtime ------------------------------------------
                // A BigInteger is one arena block: int32 sign (-1, 0, 1), int32 limb count, then little-endian uint32 limbs
                // (no leading zero limb; zero has no limbs). A BigDecimal is a two-word block: its unscaled BigInteger and its
                // scale. A MathContext is a two-word block: precision and RoundingMode ordinal. Every value is immutable.
                // Nothing here collects garbage in the middle of an operation: each public entry point first makes room for
                // everything it may allocate (juno_big_prepare), then only takes memory without collecting, so intermediate
                // objects need no rooting and the conservative collector never sees a half-built value.

                static constexpr int32_t JUNO_BIG_UP = 0;
                static constexpr int32_t JUNO_BIG_DOWN = 1;
                static constexpr int32_t JUNO_BIG_CEILING = 2;
                static constexpr int32_t JUNO_BIG_FLOOR = 3;
                static constexpr int32_t JUNO_BIG_HALF_UP = 4;
                static constexpr int32_t JUNO_BIG_HALF_DOWN = 5;
                static constexpr int32_t JUNO_BIG_HALF_EVEN = 6;
                static constexpr int32_t JUNO_BIG_UNNECESSARY = 7;

                static int32_t juno_big_raise(int32_t classId, const char* message) {
                  auto* exception = static_cast<int32_t*>(juno_alloc(8u, 4u));
                  exception[0] = classId;
                  exception[1] = juno_big_handle_of(message);
                  juno_throw_raise(juno_big_handle_of(exception));
                  return 0;
                }

                static int32_t juno_big_arithmetic(const char* message) {
                  return juno_big_raise(${JUNO_BIG_ARITHMETIC_CLASS_ID}, message);
                }

                static int32_t juno_big_number_format(const char* message) {
                  return juno_big_raise(${JUNO_BIG_NUMBER_FORMAT_CLASS_ID}, message);
                }

                // Room for `bytes` of new blocks, collecting now (while no half-built value exists) if the arena is short.
                static void juno_big_prepare(uint32_t bytes) {
                  uint32_t total = static_cast<uint32_t>(sizeof(juno_arena)) - juno_arena_used;
                  uint32_t offset = juno_gc_free_list_head;
                  while (offset != JUNO_GC_NO_NEXT) {
                    total += 4u + juno_gc_block_size(*juno_gc_header_at(offset));
                    offset = *reinterpret_cast<uint32_t*>(&juno_arena[offset + 4u]);
                  }
                  if (total < 2u * bytes + 128u) juno_gc_collect();
                }

                static void* juno_big_alloc(uint32_t bytes) {
                  void* memory = juno_gc_try_allocate(juno_gc_round_up4(bytes));
                  if (memory == nullptr) {
                    Serial.print("[juno-big] OOM: need ");
                    Serial.println(bytes);
                    juno_panic();
                  }
                  uint8_t* cursor = static_cast<uint8_t*>(memory);
                  for (uint32_t index = 0; index < juno_gc_round_up4(bytes); index++) cursor[index] = 0;
                  return memory;
                }

                static uint32_t* juno_big_scratch(uint32_t limbs) {
                  return static_cast<uint32_t*>(juno_big_alloc(4u * (limbs + 1u)));
                }

                // ---- magnitudes: little-endian uint32 limbs ------------------------------------------------------------

                static uint32_t juno_big_trim(const uint32_t* a, uint32_t n) {
                  while (n > 0u && a[n - 1u] == 0u) n--;
                  return n;
                }

                static int juno_big_cmp_mag(const uint32_t* a, uint32_t na, const uint32_t* b, uint32_t nb) {
                  if (na != nb) return na < nb ? -1 : 1;
                  for (uint32_t i = na; i > 0u; i--) {
                    if (a[i - 1u] != b[i - 1u]) return a[i - 1u] < b[i - 1u] ? -1 : 1;
                  }
                  return 0;
                }

                // r = a + b, r holds max(na, nb) + 1 limbs and may alias a or b.
                static uint32_t juno_big_add_mag(uint32_t* r, const uint32_t* a, uint32_t na, const uint32_t* b, uint32_t nb) {
                  if (na < nb) {
                    const uint32_t* t = a; a = b; b = t;
                    uint32_t tn = na; na = nb; nb = tn;
                  }
                  uint64_t carry = 0;
                  for (uint32_t i = 0; i < nb; i++) {
                    uint64_t sum = static_cast<uint64_t>(a[i]) + b[i] + carry;
                    r[i] = static_cast<uint32_t>(sum);
                    carry = sum >> 32;
                  }
                  for (uint32_t i = nb; i < na; i++) {
                    uint64_t sum = static_cast<uint64_t>(a[i]) + carry;
                    r[i] = static_cast<uint32_t>(sum);
                    carry = sum >> 32;
                  }
                  r[na] = static_cast<uint32_t>(carry);
                  return juno_big_trim(r, na + 1u);
                }

                // r = a - b for a >= b; r holds na limbs and may alias a.
                static uint32_t juno_big_sub_mag(uint32_t* r, const uint32_t* a, uint32_t na, const uint32_t* b, uint32_t nb) {
                  int64_t borrow = 0;
                  for (uint32_t i = 0; i < na; i++) {
                    int64_t diff = static_cast<int64_t>(a[i]) - (i < nb ? b[i] : 0u) - borrow;
                    borrow = diff < 0 ? 1 : 0;
                    r[i] = static_cast<uint32_t>(diff);
                  }
                  return juno_big_trim(r, na);
                }

                // r = a * b, r holds na + nb limbs and must not alias.
                static uint32_t juno_big_mul_mag(uint32_t* r, const uint32_t* a, uint32_t na, const uint32_t* b, uint32_t nb) {
                  for (uint32_t i = 0; i < na + nb; i++) r[i] = 0u;
                  for (uint32_t i = 0; i < na; i++) {
                    uint64_t carry = 0;
                    for (uint32_t j = 0; j < nb; j++) {
                      uint64_t t = static_cast<uint64_t>(a[i]) * b[j] + r[i + j] + carry;
                      r[i + j] = static_cast<uint32_t>(t);
                      carry = t >> 32;
                    }
                    r[i + nb] = static_cast<uint32_t>(carry);
                  }
                  return juno_big_trim(r, na + nb);
                }

                // q = a / d for a one-limb divisor; returns the remainder. q holds na limbs and may alias a.
                static uint32_t juno_big_divmod_small(uint32_t* q, const uint32_t* a, uint32_t na, uint32_t d) {
                  uint64_t rem = 0;
                  for (uint32_t i = na; i > 0u; i--) {
                    uint64_t cur = (rem << 32) | a[i - 1u];
                    q[i - 1u] = static_cast<uint32_t>(cur / d);
                    rem = cur % d;
                  }
                  return static_cast<uint32_t>(rem);
                }

                // a = a * m + add in place; a holds n + 1 limbs. Returns the new length.
                static uint32_t juno_big_mul_small_add(uint32_t* a, uint32_t n, uint32_t m, uint32_t add) {
                  uint64_t carry = add;
                  for (uint32_t i = 0; i < n; i++) {
                    uint64_t t = static_cast<uint64_t>(a[i]) * m + carry;
                    a[i] = static_cast<uint32_t>(t);
                    carry = t >> 32;
                  }
                  if (carry != 0u) a[n++] = static_cast<uint32_t>(carry);
                  return n;
                }

                static uint32_t juno_big_clz(uint32_t value) {
                  uint32_t count = 0;
                  if (value == 0u) return 32u;
                  while ((value & 0x80000000u) == 0u) {
                    value <<= 1;
                    count++;
                  }
                  return count;
                }

                // Knuth algorithm D: q = a / b and r = a mod b for nb >= 1. q holds na - nb + 1 limbs, r holds nb limbs;
                // neither aliases an input. Returns the remainder length through *rn and the quotient length.
                static uint32_t juno_big_divmod_mag(uint32_t* q, uint32_t* r, uint32_t* rn, const uint32_t* a, uint32_t na,
                                                    const uint32_t* b, uint32_t nb) {
                  if (juno_big_cmp_mag(a, na, b, nb) < 0) {
                    for (uint32_t i = 0; i < na; i++) r[i] = a[i];
                    *rn = na;
                    return 0u;
                  }
                  if (nb == 1u) {
                    uint32_t rem = juno_big_divmod_small(q, a, na, b[0]);
                    r[0] = rem;
                    *rn = rem != 0u ? 1u : 0u;
                    return juno_big_trim(q, na);
                  }
                  uint32_t shift = juno_big_clz(b[nb - 1u]);
                  uint32_t* vn = juno_big_scratch(nb);
                  uint32_t* un = juno_big_scratch(na + 1u);
                  if (shift == 0u) {
                    for (uint32_t i = 0; i < nb; i++) vn[i] = b[i];
                    for (uint32_t i = 0; i < na; i++) un[i] = a[i];
                    un[na] = 0u;
                  } else {
                    for (uint32_t i = nb - 1u; i > 0u; i--) vn[i] = (b[i] << shift) | (b[i - 1u] >> (32u - shift));
                    vn[0] = b[0] << shift;
                    un[na] = a[na - 1u] >> (32u - shift);
                    for (uint32_t i = na - 1u; i > 0u; i--) un[i] = (a[i] << shift) | (a[i - 1u] >> (32u - shift));
                    un[0] = a[0] << shift;
                  }
                  const uint64_t base = 0x100000000ull;
                  for (int32_t j = static_cast<int32_t>(na - nb); j >= 0; j--) {
                    uint64_t numerator = (static_cast<uint64_t>(un[j + nb]) << 32) | un[j + nb - 1u];
                    uint64_t qhat = numerator / vn[nb - 1u];
                    uint64_t rhat = numerator % vn[nb - 1u];
                    while (qhat >= base || qhat * vn[nb - 2u] > ((rhat << 32) | un[j + nb - 2u])) {
                      qhat--;
                      rhat += vn[nb - 1u];
                      if (rhat >= base) break;
                    }
                    int64_t borrow = 0;
                    uint64_t carry = 0;
                    for (uint32_t i = 0; i < nb; i++) {
                      uint64_t product = qhat * vn[i] + carry;
                      carry = product >> 32;
                      int64_t t = static_cast<int64_t>(un[i + j]) - borrow - static_cast<int64_t>(product & 0xFFFFFFFFull);
                      un[i + j] = static_cast<uint32_t>(t);
                      borrow = t < 0 ? 1 : 0;
                    }
                    int64_t t = static_cast<int64_t>(un[j + nb]) - borrow - static_cast<int64_t>(carry);
                    un[j + nb] = static_cast<uint32_t>(t);
                    if (t < 0) {
                      qhat--;
                      uint64_t add = 0;
                      for (uint32_t i = 0; i < nb; i++) {
                        uint64_t sum = static_cast<uint64_t>(un[i + j]) + vn[i] + add;
                        un[i + j] = static_cast<uint32_t>(sum);
                        add = sum >> 32;
                      }
                      un[j + nb] = static_cast<uint32_t>(un[j + nb] + add);
                    }
                    q[j] = static_cast<uint32_t>(qhat);
                  }
                  if (shift == 0u) {
                    for (uint32_t i = 0; i < nb; i++) r[i] = un[i];
                  } else {
                    for (uint32_t i = 0; i + 1u < nb; i++) r[i] = (un[i] >> shift) | (un[i + 1u] << (32u - shift));
                    r[nb - 1u] = un[nb - 1u] >> shift;
                  }
                  *rn = juno_big_trim(r, nb);
                  return juno_big_trim(q, na - nb + 1u);
                }

                // ---- BigInteger objects ------------------------------------------------------------------------------

                static int32_t* juno_big_words(int32_t handle) {
                  if (handle == 0) juno_panic();
                  return juno_big_pointer(handle);
                }

                static int32_t juno_big_sign_of(int32_t handle) { return juno_big_words(handle)[0]; }
                static uint32_t juno_big_len_of(int32_t handle) { return static_cast<uint32_t>(juno_big_words(handle)[1]); }
                static uint32_t* juno_big_limbs_of(int32_t handle) { return reinterpret_cast<uint32_t*>(juno_big_words(handle) + 2); }

                static int32_t juno_big_make(int32_t sign, const uint32_t* limbs, uint32_t n) {
                  n = juno_big_trim(limbs, n);
                  auto* object = static_cast<int32_t*>(juno_big_alloc(8u + 4u * n));
                  object[0] = n == 0u ? 0 : sign;
                  object[1] = static_cast<int32_t>(n);
                  uint32_t* target = reinterpret_cast<uint32_t*>(object + 2);
                  for (uint32_t i = 0; i < n; i++) target[i] = limbs[i];
                  return juno_big_handle_of(object);
                }

                static int32_t juno_big_of_long_h(int64_t value) {
                  uint64_t magnitude = value < 0 ? 0ull - static_cast<uint64_t>(value) : static_cast<uint64_t>(value);
                  uint32_t limbs[2] = {static_cast<uint32_t>(magnitude), static_cast<uint32_t>(magnitude >> 32)};
                  return juno_big_make(value < 0 ? -1 : 1, limbs, 2u);
                }

                static int juno_big_compare_h(int32_t a, int32_t b) {
                  int32_t sa = juno_big_sign_of(a);
                  int32_t sb = juno_big_sign_of(b);
                  if (sa != sb) return sa < sb ? -1 : 1;
                  if (sa == 0) return 0;
                  int magnitude = juno_big_cmp_mag(juno_big_limbs_of(a), juno_big_len_of(a), juno_big_limbs_of(b),
                      juno_big_len_of(b));
                  return sa < 0 ? -magnitude : magnitude;
                }

                static int32_t juno_big_negate_h(int32_t a) {
                  return juno_big_make(-juno_big_sign_of(a), juno_big_limbs_of(a), juno_big_len_of(a));
                }

                static int32_t juno_big_abs_h(int32_t a) {
                  return juno_big_sign_of(a) >= 0 ? a : juno_big_negate_h(a);
                }

                static int32_t juno_big_add_h(int32_t a, int32_t b) {
                  int32_t sa = juno_big_sign_of(a);
                  int32_t sb = juno_big_sign_of(b);
                  if (sa == 0) return b;
                  if (sb == 0) return a;
                  uint32_t na = juno_big_len_of(a);
                  uint32_t nb = juno_big_len_of(b);
                  const uint32_t* la = juno_big_limbs_of(a);
                  const uint32_t* lb = juno_big_limbs_of(b);
                  uint32_t capacity = (na > nb ? na : nb) + 1u;
                  uint32_t* result = juno_big_scratch(capacity);
                  if (sa == sb) {
                    uint32_t n = juno_big_add_mag(result, la, na, lb, nb);
                    return juno_big_make(sa, result, n);
                  }
                  int order = juno_big_cmp_mag(la, na, lb, nb);
                  if (order == 0) return juno_big_make(0, result, 0u);
                  if (order > 0) {
                    uint32_t n = juno_big_sub_mag(result, la, na, lb, nb);
                    return juno_big_make(sa, result, n);
                  }
                  uint32_t n = juno_big_sub_mag(result, lb, nb, la, na);
                  return juno_big_make(sb, result, n);
                }

                static int32_t juno_big_sub_h(int32_t a, int32_t b) {
                  if (juno_big_sign_of(b) == 0) return a;
                  return juno_big_add_h(a, juno_big_negate_h(b));
                }

                static int32_t juno_big_mul_h(int32_t a, int32_t b) {
                  int32_t sign = juno_big_sign_of(a) * juno_big_sign_of(b);
                  if (sign == 0) return juno_big_make(0, nullptr, 0u);
                  uint32_t na = juno_big_len_of(a);
                  uint32_t nb = juno_big_len_of(b);
                  uint32_t* result = juno_big_scratch(na + nb);
                  uint32_t n = juno_big_mul_mag(result, juno_big_limbs_of(a), na, juno_big_limbs_of(b), nb);
                  return juno_big_make(sign, result, n);
                }

                // Truncating division: *quotient = a / b, *remainder = a - quotient * b (the remainder takes a's sign).
                static void juno_big_divrem_h(int32_t a, int32_t b, int32_t* quotient, int32_t* remainder) {
                  uint32_t na = juno_big_len_of(a);
                  uint32_t nb = juno_big_len_of(b);
                  if (juno_big_cmp_mag(juno_big_limbs_of(a), na, juno_big_limbs_of(b), nb) < 0) {
                    *quotient = juno_big_make(0, nullptr, 0u);
                    *remainder = a;
                    return;
                  }
                  uint32_t* q = juno_big_scratch(na - nb + 1u);
                  uint32_t* r = juno_big_scratch(nb);
                  uint32_t rn = 0;
                  uint32_t qn = juno_big_divmod_mag(q, r, &rn, juno_big_limbs_of(a), na, juno_big_limbs_of(b), nb);
                  *quotient = juno_big_make(juno_big_sign_of(a) * juno_big_sign_of(b), q, qn);
                  *remainder = juno_big_make(juno_big_sign_of(a), r, rn);
                }

                static int32_t juno_big_one_h() {
                  uint32_t one = 1u;
                  return juno_big_make(1, &one, 1u);
                }

                // 10^k as a BigInteger.
                static int32_t juno_big_pow10_h(uint32_t k) {
                  uint32_t* limbs = juno_big_scratch(k / 9u + 2u);
                  limbs[0] = 1u;
                  uint32_t n = 1u;
                  while (k >= 9u) {
                    n = juno_big_mul_small_add(limbs, n, 1000000000u, 0u);
                    k -= 9u;
                  }
                  uint32_t factor = 1u;
                  for (uint32_t i = 0; i < k; i++) factor *= 10u;
                  if (factor != 1u) n = juno_big_mul_small_add(limbs, n, factor, 0u);
                  return juno_big_make(1, limbs, n);
                }

                static int32_t juno_big_scale_up_h(int32_t a, uint32_t k) {
                  if (k == 0u || juno_big_sign_of(a) == 0) return a;
                  return juno_big_mul_h(a, juno_big_pow10_h(k));
                }

                static bool juno_big_is_odd_h(int32_t a) {
                  return juno_big_len_of(a) > 0u && (juno_big_limbs_of(a)[0] & 1u) != 0u;
                }

                // Number of decimal digits of |a| (1 for zero).
                static uint32_t juno_big_digits_h(int32_t a) {
                  uint32_t n = juno_big_len_of(a);
                  if (n == 0u) return 1u;
                  uint32_t* work = juno_big_scratch(n);
                  for (uint32_t i = 0; i < n; i++) work[i] = juno_big_limbs_of(a)[i];
                  uint32_t digits = 0;
                  uint32_t length = n;
                  while (length > 0u) {
                    uint32_t rem = juno_big_divmod_small(work, work, length, 1000000000u);
                    length = juno_big_trim(work, length);
                    if (length == 0u) {
                      uint32_t chunk = rem;
                      while (chunk != 0u) {
                        digits++;
                        chunk /= 10u;
                      }
                    } else {
                      digits += 9u;
                    }
                  }
                  return digits;
                }

                // Whether a rounding step moves away from zero: `halfCompare` compares the dropped part with one half, `exact`
                // says nothing was dropped, `sign` is the value's sign and `odd` the last kept digit's parity.
                static bool juno_big_round_up(int32_t mode, int halfCompare, bool exact, int32_t sign, bool odd) {
                  if (exact) return false;
                  switch (mode) {
                    case JUNO_BIG_UP: return true;
                    case JUNO_BIG_DOWN: return false;
                    case JUNO_BIG_CEILING: return sign > 0;
                    case JUNO_BIG_FLOOR: return sign < 0;
                    case JUNO_BIG_HALF_UP: return halfCompare >= 0;
                    case JUNO_BIG_HALF_DOWN: return halfCompare > 0;
                    case JUNO_BIG_HALF_EVEN: return halfCompare > 0 || (halfCompare == 0 && odd);
                    default: return false;
                  }
                }

                // a / b rounded to an integer; *inexact says whether anything nonzero was dropped. `sticky` is a nonzero amount
                // already truncated from `a` (it breaks exact-half ties upwards). Returns 0 with an ArithmeticException pending
                // for UNNECESSARY when the division is inexact.
                static int32_t juno_big_round_div_h(int32_t a, int32_t b, int32_t mode, bool sticky, bool* inexact) {
                  int32_t quotient = 0;
                  int32_t remainder = 0;
                  juno_big_divrem_h(a, b, &quotient, &remainder);
                  bool remainderNonZero = juno_big_sign_of(remainder) != 0;
                  *inexact = remainderNonZero || sticky;
                  if (!*inexact) return quotient;
                  if (mode == JUNO_BIG_UNNECESSARY) return juno_big_arithmetic("Rounding necessary");
                  int32_t sign = juno_big_sign_of(a) * juno_big_sign_of(b);
                  if (sign == 0) sign = 1;
                  int halfCompare = -1;
                  if (remainderNonZero) {
                    int32_t magnitude = juno_big_abs_h(remainder);
                    halfCompare = juno_big_compare_h(juno_big_add_h(magnitude, magnitude), juno_big_abs_h(b));
                    if (halfCompare == 0 && sticky) halfCompare = 1;
                  }
                  if (!juno_big_round_up(mode, halfCompare, false, sign, juno_big_is_odd_h(quotient))) return quotient;
                  int32_t one = juno_big_one_h();
                  return sign < 0 ? juno_big_sub_h(quotient, one) : juno_big_add_h(quotient, one);
                }

                // ---- BigInteger entry points -----------------------------------------------------------------------

                static uint32_t juno_big_budget(int32_t a, int32_t b) {
                  return 4u * (juno_big_len_of(a) + juno_big_len_of(b) + 4u);
                }

                extern "C" int32_t juno_big_integer_value_of(int64_t value) {
                  juno_big_prepare(32u);
                  return juno_big_of_long_h(value);
                }

                extern "C" int32_t juno_big_integer_constant(int32_t value) {
                  juno_big_prepare(32u);
                  return juno_big_of_long_h(value);
                }

                extern "C" int32_t juno_big_integer_add(int32_t a, int32_t b) {
                  juno_big_prepare(4u * juno_big_budget(a, b));
                  return juno_big_add_h(a, b);
                }

                extern "C" int32_t juno_big_integer_subtract(int32_t a, int32_t b) {
                  juno_big_prepare(4u * juno_big_budget(a, b));
                  return juno_big_sub_h(a, b);
                }

                extern "C" int32_t juno_big_integer_multiply(int32_t a, int32_t b) {
                  juno_big_prepare(4u * juno_big_budget(a, b));
                  return juno_big_mul_h(a, b);
                }

                extern "C" int32_t juno_big_integer_divide(int32_t a, int32_t b) {
                  if (juno_big_sign_of(b) == 0) return juno_big_arithmetic("BigInteger divide by zero");
                  juno_big_prepare(8u * juno_big_budget(a, b));
                  int32_t quotient = 0;
                  int32_t remainder = 0;
                  juno_big_divrem_h(a, b, &quotient, &remainder);
                  return quotient;
                }

                extern "C" int32_t juno_big_integer_remainder(int32_t a, int32_t b) {
                  if (juno_big_sign_of(b) == 0) return juno_big_arithmetic("BigInteger divide by zero");
                  juno_big_prepare(8u * juno_big_budget(a, b));
                  int32_t quotient = 0;
                  int32_t remainder = 0;
                  juno_big_divrem_h(a, b, &quotient, &remainder);
                  return remainder;
                }

                extern "C" int32_t juno_big_integer_mod(int32_t a, int32_t b) {
                  if (juno_big_sign_of(b) <= 0) return juno_big_arithmetic("BigInteger: modulus not positive");
                  juno_big_prepare(8u * juno_big_budget(a, b));
                  int32_t quotient = 0;
                  int32_t remainder = 0;
                  juno_big_divrem_h(a, b, &quotient, &remainder);
                  return juno_big_sign_of(remainder) < 0 ? juno_big_add_h(remainder, b) : remainder;
                }

                extern "C" int32_t juno_big_integer_negate(int32_t a) {
                  juno_big_prepare(4u * juno_big_budget(a, a));
                  return juno_big_negate_h(a);
                }

                extern "C" int32_t juno_big_integer_abs(int32_t a) {
                  juno_big_prepare(4u * juno_big_budget(a, a));
                  return juno_big_abs_h(a);
                }

                extern "C" int32_t juno_big_integer_signum(int32_t a) {
                  return juno_big_sign_of(a);
                }

                extern "C" int32_t juno_big_integer_compare_to(int32_t a, int32_t b) {
                  return juno_big_compare_h(a, b);
                }

                extern "C" int32_t juno_big_integer_equals(int32_t a, int32_t b) {
                  if (b == 0) return 0;
                  return juno_big_compare_h(a, b) == 0 ? 1 : 0;
                }

                extern "C" int32_t juno_big_integer_min(int32_t a, int32_t b) {
                  return juno_big_compare_h(a, b) <= 0 ? a : b;
                }

                extern "C" int32_t juno_big_integer_max(int32_t a, int32_t b) {
                  return juno_big_compare_h(a, b) >= 0 ? a : b;
                }

                extern "C" int32_t juno_big_integer_bit_length(int32_t a) {
                  uint32_t n = juno_big_len_of(a);
                  if (n == 0u) return 0;
                  const uint32_t* limbs = juno_big_limbs_of(a);
                  uint32_t bits = 32u * n - juno_big_clz(limbs[n - 1u]);
                  if (juno_big_sign_of(a) < 0) {
                    // A negative power of two needs one bit fewer than its magnitude.
                    bool powerOfTwo = (limbs[n - 1u] & (limbs[n - 1u] - 1u)) == 0u;
                    for (uint32_t i = 0; powerOfTwo && i + 1u < n; i++) powerOfTwo = limbs[i] == 0u;
                    if (powerOfTwo) bits--;
                  }
                  return static_cast<int32_t>(bits);
                }

                static uint64_t juno_big_low64(int32_t a) {
                  uint32_t n = juno_big_len_of(a);
                  const uint32_t* limbs = juno_big_limbs_of(a);
                  uint64_t magnitude = (n > 0u ? limbs[0] : 0u) | (static_cast<uint64_t>(n > 1u ? limbs[1] : 0u) << 32);
                  return juno_big_sign_of(a) < 0 ? 0ull - magnitude : magnitude;
                }

                extern "C" int32_t juno_big_integer_int_value(int32_t a) {
                  return static_cast<int32_t>(static_cast<uint32_t>(juno_big_low64(a)));
                }

                extern "C" int64_t juno_big_integer_long_value(int32_t a) {
                  return static_cast<int64_t>(juno_big_low64(a));
                }

                extern "C" int32_t juno_big_integer_pow(int32_t a, int32_t exponent) {
                  if (exponent < 0) return juno_big_arithmetic("Negative exponent");
                  uint32_t n = juno_big_len_of(a);
                  juno_big_prepare(12u * 4u * (n * static_cast<uint32_t>(exponent) + 4u));
                  int32_t result = juno_big_one_h();
                  int32_t base = a;
                  uint32_t remaining = static_cast<uint32_t>(exponent);
                  while (remaining != 0u) {
                    if ((remaining & 1u) != 0u) result = juno_big_mul_h(result, base);
                    remaining >>= 1;
                    if (remaining != 0u) base = juno_big_mul_h(base, base);
                  }
                  return result;
                }

                extern "C" int32_t juno_big_integer_shift_left(int32_t a, int32_t distance) {
                  if (distance < 0) {
                    // Arithmetic right shift: floor(a / 2^-distance).
                    uint32_t bits = static_cast<uint32_t>(-static_cast<int64_t>(distance));
                    int32_t sign = juno_big_sign_of(a);
                    uint32_t n = juno_big_len_of(a);
                    juno_big_prepare(4u * (n + 8u) * 3u);
                    uint32_t words = bits / 32u;
                    uint32_t inner = bits % 32u;
                    if (words >= n) return sign < 0 ? juno_big_of_long_h(-1) : juno_big_make(0, nullptr, 0u);
                    const uint32_t* limbs = juno_big_limbs_of(a);
                    uint32_t outLength = n - words;
                    uint32_t* out = juno_big_scratch(outLength);
                    bool lost = false;
                    for (uint32_t i = 0; i < words; i++) if (limbs[i] != 0u) lost = true;
                    if (inner != 0u && (limbs[words] & ((1u << inner) - 1u)) != 0u) lost = true;
                    for (uint32_t i = 0; i < outLength; i++) {
                      uint32_t low = limbs[i + words] >> inner;
                      uint32_t high = (inner != 0u && i + words + 1u < n) ? limbs[i + words + 1u] << (32u - inner) : 0u;
                      out[i] = low | high;
                    }
                    int32_t shifted = juno_big_make(sign, out, outLength);
                    if (sign < 0 && lost) shifted = juno_big_sub_h(shifted, juno_big_one_h());
                    return shifted;
                  }
                  uint32_t n = juno_big_len_of(a);
                  if (n == 0u || distance == 0) return a;
                  uint32_t words = static_cast<uint32_t>(distance) / 32u;
                  uint32_t inner = static_cast<uint32_t>(distance) % 32u;
                  juno_big_prepare(4u * (n + words + 4u) * 3u);
                  uint32_t* out = juno_big_scratch(n + words + 1u);
                  const uint32_t* limbs = juno_big_limbs_of(a);
                  for (uint32_t i = 0; i < n; i++) {
                    out[i + words] |= limbs[i] << inner;
                    if (inner != 0u) out[i + words + 1u] |= limbs[i] >> (32u - inner);
                  }
                  return juno_big_make(juno_big_sign_of(a), out, n + words + 1u);
                }

                extern "C" int32_t juno_big_integer_shift_right(int32_t a, int32_t distance) {
                  if (distance == static_cast<int32_t>(0x80000000u)) return juno_big_integer_shift_left(a, 0x7FFFFFFF);
                  return juno_big_integer_shift_left(a, -distance);
                }

                extern "C" int32_t juno_big_integer_sqrt(int32_t a) {
                  if (juno_big_sign_of(a) < 0) return juno_big_arithmetic("Negative BigInteger");
                  if (juno_big_sign_of(a) == 0) return a;
                  uint32_t n = juno_big_len_of(a);
                  juno_big_prepare(4u * (n + 4u) * 8u * (32u * n + 8u));
                  int32_t bits = juno_big_integer_bit_length(a);
                  int32_t x = juno_big_integer_shift_left(juno_big_one_h(), (bits + 1) / 2);
                  for (;;) {
                    int32_t quotient = 0;
                    int32_t remainder = 0;
                    juno_big_divrem_h(a, x, &quotient, &remainder);
                    int32_t y = juno_big_integer_shift_left(juno_big_add_h(x, quotient), -1);
                    if (juno_big_compare_h(y, x) >= 0) return x;
                    x = y;
                  }
                }

                extern "C" int32_t juno_big_integer_gcd(int32_t a, int32_t b) {
                  juno_big_prepare(4u * juno_big_budget(a, b) * 8u * (32u * (juno_big_len_of(a) + juno_big_len_of(b)) + 8u));
                  int32_t x = juno_big_abs_h(a);
                  int32_t y = juno_big_abs_h(b);
                  while (juno_big_sign_of(y) != 0) {
                    int32_t quotient = 0;
                    int32_t remainder = 0;
                    juno_big_divrem_h(x, y, &quotient, &remainder);
                    x = y;
                    y = remainder;
                  }
                  return x;
                }

                // Writes |a| in decimal into a fresh arena string; *length gets its character count.
                static char* juno_big_decimal(int32_t a, uint32_t* length) {
                  uint32_t n = juno_big_len_of(a);
                  uint32_t chunks = (n * 32u) / 29u + 1u;
                  char* text = static_cast<char*>(juno_big_alloc(chunks * 9u + 2u));
                  if (n == 0u) {
                    text[0] = 48;
                    *length = 1u;
                    return text;
                  }
                  uint32_t* work = juno_big_scratch(n);
                  for (uint32_t i = 0; i < n; i++) work[i] = juno_big_limbs_of(a)[i];
                  uint32_t end = chunks * 9u + 1u;
                  uint32_t position = end;
                  uint32_t used = n;
                  while (used > 0u) {
                    uint32_t chunk = juno_big_divmod_small(work, work, used, 1000000000u);
                    used = juno_big_trim(work, used);
                    for (uint32_t i = 0; i < 9u; i++) {
                      if (used == 0u && chunk == 0u) break;
                      text[--position] = static_cast<char>(48 + chunk % 10u);
                      chunk /= 10u;
                    }
                  }
                  *length = end - position;
                  for (uint32_t i = 0; i < *length; i++) text[i] = text[position + i];
                  text[*length] = 0;
                  return text;
                }

                extern "C" int32_t juno_big_integer_to_string(int32_t a) {
                  juno_big_prepare(4u * (juno_big_len_of(a) + 8u) * 4u);
                  uint32_t length = 0;
                  char* digits = juno_big_decimal(a, &length);
                  if (juno_big_sign_of(a) >= 0) return juno_big_handle_of(digits);
                  char* text = static_cast<char*>(juno_big_alloc(length + 2u));
                  text[0] = 45;
                  for (uint32_t i = 0; i < length; i++) text[i + 1u] = digits[i];
                  return juno_big_handle_of(text);
                }

                extern "C" JUNO_ASM_ABI double juno_big_integer_double_value(int32_t a) {
                  juno_big_prepare(4u * (juno_big_len_of(a) + 8u) * 4u);
                  uint32_t length = 0;
                  char* digits = juno_big_decimal(a, &length);
                  double magnitude = strtod(digits, nullptr);
                  return juno_big_sign_of(a) < 0 ? -magnitude : magnitude;
                }

                static int32_t juno_big_message(const char* prefix, const char* text, const char* suffix) {
                  uint32_t length = 1u;
                  for (const char* p = prefix; *p != 0; p++) length++;
                  for (const char* p = text; *p != 0; p++) length++;
                  for (const char* p = suffix; *p != 0; p++) length++;
                  char* message = static_cast<char*>(juno_big_alloc(length));
                  uint32_t out = 0;
                  for (const char* p = prefix; *p != 0; p++) message[out++] = *p;
                  for (const char* p = text; *p != 0; p++) message[out++] = *p;
                  for (const char* p = suffix; *p != 0; p++) message[out++] = *p;
                  return juno_big_handle_of(message);
                }

                static int32_t juno_big_bad_input(const char* text) {
                  juno_big_prepare(4u * 64u);
                  int32_t message = juno_big_message("For input string: \\"", text, "\\"");
                  return juno_big_raise(${JUNO_BIG_NUMBER_FORMAT_CLASS_ID}, juno_big_pointer_chars(message));
                }

                // Parses [sign] digits into a magnitude; false if the text is not a plain integer.
                static bool juno_big_parse_digits(const char* text, uint32_t count, uint32_t* limbs, uint32_t* length) {
                  uint32_t n = 0;
                  uint32_t index = 0;
                  while (index < count) {
                    uint32_t chunkDigits = count - index < 9u ? count - index : 9u;
                    uint32_t chunk = 0;
                    uint32_t factor = 1;
                    for (uint32_t i = 0; i < chunkDigits; i++) {
                      char c = text[index + i];
                      if (c < 48 || c > 57) return false;
                      chunk = chunk * 10u + static_cast<uint32_t>(c - 48);
                      factor *= 10u;
                    }
                    n = juno_big_mul_small_add(limbs, n, factor, chunk);
                    index += chunkDigits;
                  }
                  *length = n;
                  return true;
                }

                extern "C" int32_t juno_big_integer_parse(const char* text) {
                  uint32_t length = 0;
                  while (text[length] != 0) length++;
                  uint32_t start = 0;
                  int32_t sign = 1;
                  if (length > 0u && (text[0] == 45 || text[0] == 43)) {
                    sign = text[0] == 45 ? -1 : 1;
                    start = 1;
                  }
                  if (length == start) return juno_big_bad_input(text);
                  juno_big_prepare(4u * (length / 9u + 8u) * 2u + 256u);
                  uint32_t* limbs = juno_big_scratch((length - start) / 9u + 2u);
                  uint32_t n = 0;
                  if (!juno_big_parse_digits(text + start, length - start, limbs, &n)) return juno_big_bad_input(text);
                  return juno_big_make(sign, limbs, n);
                }

                // ---- MathContext ----------------------------------------------------------------------------------

                extern "C" int32_t juno_big_math_context_new(int32_t precision, int32_t mode) {
                  if (precision < 0) return juno_big_raise(${JUNO_BIG_ILLEGAL_ARGUMENT_CLASS_ID}, "Digits < 0");
                  juno_big_prepare(32u);
                  auto* object = static_cast<int32_t*>(juno_big_alloc(8u));
                  object[0] = precision;
                  object[1] = mode;
                  return juno_big_handle_of(object);
                }

                extern "C" int32_t juno_big_math_context_of_precision(int32_t precision) {
                  return juno_big_math_context_new(precision, JUNO_BIG_HALF_UP);
                }

                extern "C" int32_t juno_big_math_context_constant(int32_t which) {
                  // 0 UNLIMITED, 1 DECIMAL32, 2 DECIMAL64, 3 DECIMAL128 (all but UNLIMITED round half even).
                  static const int32_t precisions[4] = {0, 7, 16, 34};
                  return juno_big_math_context_new(precisions[which & 3], which == 0 ? JUNO_BIG_HALF_UP : JUNO_BIG_HALF_EVEN);
                }

                extern "C" int32_t juno_big_math_context_precision(int32_t context) {
                  return juno_big_words(context)[0];
                }

                // ---- BigDecimal -----------------------------------------------------------------------------------

                static int32_t juno_bd_make(int32_t unscaled, int32_t scale) {
                  auto* object = static_cast<int32_t*>(juno_big_alloc(8u));
                  object[0] = unscaled;
                  object[1] = scale;
                  return juno_big_handle_of(object);
                }

                static int32_t juno_bd_unscaled(int32_t value) { return juno_big_words(value)[0]; }
                static int32_t juno_bd_scale(int32_t value) { return juno_big_words(value)[1]; }

                static int32_t juno_bd_zero_h() {
                  return juno_bd_make(juno_big_make(0, nullptr, 0u), 0);
                }

                // Value with scale `scale` and the same numeric value, for scale >= the current one.
                static int32_t juno_bd_unscaled_at(int32_t value, int32_t scale) {
                  return juno_big_scale_up_h(juno_bd_unscaled(value), static_cast<uint32_t>(scale - juno_bd_scale(value)));
                }

                static int juno_bd_compare_h(int32_t a, int32_t b) {
                  int32_t sa = juno_big_sign_of(juno_bd_unscaled(a));
                  int32_t sb = juno_big_sign_of(juno_bd_unscaled(b));
                  if (sa != sb) return sa < sb ? -1 : 1;
                  if (sa == 0) return 0;
                  int32_t scale = juno_bd_scale(a) > juno_bd_scale(b) ? juno_bd_scale(a) : juno_bd_scale(b);
                  return juno_big_compare_h(juno_bd_unscaled_at(a, scale), juno_bd_unscaled_at(b, scale));
                }

                static uint32_t juno_bd_budget(int32_t a, int32_t b) {
                  int64_t scaleGap = static_cast<int64_t>(juno_bd_scale(a)) - juno_bd_scale(b);
                  uint32_t gap = static_cast<uint32_t>(scaleGap < 0 ? -scaleGap : scaleGap);
                  return juno_big_budget(juno_bd_unscaled(a), juno_bd_unscaled(b)) + 4u * (gap / 9u + 4u);
                }

                // Strips trailing zeros of the unscaled value while the scale stays above `preferred`.
                static int32_t juno_bd_strip_to(int32_t unscaled, int32_t scale, int32_t preferred) {
                  int32_t ten = juno_big_of_long_h(10);
                  while (scale > preferred && juno_big_sign_of(unscaled) != 0) {
                    int32_t quotient = 0;
                    int32_t remainder = 0;
                    juno_big_divrem_h(unscaled, ten, &quotient, &remainder);
                    if (juno_big_sign_of(remainder) != 0) break;
                    unscaled = quotient;
                    scale--;
                  }
                  return juno_bd_make(unscaled, scale);
                }

                // Rounds `unscaled` (at `scale`) to `precision` digits (0 keeps every digit); `sticky` marks a nonzero amount
                // already truncated. *inexact says whether anything nonzero was dropped. Returns 0 with an exception pending.
                static int32_t juno_bd_round_to(int32_t unscaled, int32_t scale, int32_t precision, int32_t mode, bool sticky,
                                                bool* inexact) {
                  int32_t digits = static_cast<int32_t>(juno_big_digits_h(unscaled));
                  *inexact = false;
                  if (precision == 0 || digits <= precision) return juno_bd_make(unscaled, scale);
                  int32_t drop = digits - precision;
                  int32_t divisor = juno_big_pow10_h(static_cast<uint32_t>(drop));
                  int32_t rounded = juno_big_round_div_h(unscaled, divisor, mode, sticky, inexact);
                  if (rounded == 0 && *inexact && mode == JUNO_BIG_UNNECESSARY) return 0;
                  scale -= drop;
                  if (static_cast<int32_t>(juno_big_digits_h(rounded)) > precision) {
                    int32_t quotient = 0;
                    int32_t remainder = 0;
                    juno_big_divrem_h(rounded, juno_big_of_long_h(10), &quotient, &remainder);
                    rounded = quotient;
                    scale--;
                  }
                  return juno_bd_make(rounded, scale);
                }

                static int32_t juno_bd_round_mc(int32_t value, int32_t context) {
                  bool inexact = false;
                  return juno_bd_round_to(juno_bd_unscaled(value), juno_bd_scale(value), juno_big_words(context)[0],
                      juno_big_words(context)[1], false, &inexact);
                }

                // Sets the scale, rounding when digits are dropped.
                static int32_t juno_bd_set_scale_h(int32_t value, int32_t scale, int32_t mode) {
                  int32_t current = juno_bd_scale(value);
                  if (scale >= current) {
                    return juno_bd_make(juno_bd_unscaled_at(value, scale), scale);
                  }
                  int32_t divisor = juno_big_pow10_h(static_cast<uint32_t>(current - scale));
                  bool inexact = false;
                  int32_t rounded = juno_big_round_div_h(juno_bd_unscaled(value), divisor, mode, false, &inexact);
                  if (inexact && mode == JUNO_BIG_UNNECESSARY) return 0;
                  return juno_bd_make(rounded, scale);
                }

                extern "C" int32_t juno_big_decimal_value_of(int64_t value) {
                  juno_big_prepare(64u);
                  return juno_bd_make(juno_big_of_long_h(value), 0);
                }

                extern "C" int32_t juno_big_decimal_value_of_scaled(int64_t value, int32_t scale) {
                  juno_big_prepare(64u);
                  return juno_bd_make(juno_big_of_long_h(value), scale);
                }

                extern "C" int32_t juno_big_decimal_of_int(int32_t value) {
                  juno_big_prepare(64u);
                  return juno_bd_make(juno_big_of_long_h(value), 0);
                }

                extern "C" int32_t juno_big_decimal_of_integer(int32_t value) {
                  juno_big_prepare(32u);
                  return juno_bd_make(value, 0);
                }

                extern "C" int32_t juno_big_decimal_of_integer_scaled(int32_t value, int32_t scale) {
                  juno_big_prepare(32u);
                  return juno_bd_make(value, scale);
                }

                extern "C" int32_t juno_big_decimal_constant(int32_t value) {
                  juno_big_prepare(64u);
                  return juno_bd_make(juno_big_of_long_h(value), 0);
                }

                static int32_t juno_bd_parse(const char* text) {
                  uint32_t length = 0;
                  while (text[length] != 0) length++;
                  uint32_t index = 0;
                  int32_t sign = 1;
                  if (index < length && (text[index] == 45 || text[index] == 43)) {
                    sign = text[index] == 45 ? -1 : 1;
                    index++;
                  }
                  juno_big_prepare(4u * (length / 9u + 8u) * 4u + 256u);
                  char* digits = static_cast<char*>(juno_big_alloc(length + 1u));
                  uint32_t count = 0;
                  int64_t fraction = 0;
                  bool seenPoint = false;
                  bool anyDigit = false;
                  for (; index < length; index++) {
                    char c = text[index];
                    if (c >= 48 && c <= 57) {
                      digits[count++] = c;
                      anyDigit = true;
                      if (seenPoint) fraction++;
                    } else if (c == 46 && !seenPoint) {
                      seenPoint = true;
                    } else {
                      break;
                    }
                  }
                  if (!anyDigit) return juno_big_bad_input(text);
                  int64_t exponent = 0;
                  if (index < length && (text[index] == 101 || text[index] == 69)) {
                    index++;
                    int64_t exponentSign = 1;
                    if (index < length && (text[index] == 45 || text[index] == 43)) {
                      exponentSign = text[index] == 45 ? -1 : 1;
                      index++;
                    }
                    if (index >= length) return juno_big_bad_input(text);
                    for (; index < length; index++) {
                      char c = text[index];
                      if (c < 48 || c > 57) return juno_big_bad_input(text);
                      exponent = exponent * 10 + (c - 48);
                      if (exponent > 2147483647ll) return juno_big_bad_input(text);
                    }
                    exponent *= exponentSign;
                  }
                  if (index != length) return juno_big_bad_input(text);
                  int64_t scale = fraction - exponent;
                  if (scale > 2147483647ll || scale < -2147483647ll) return juno_big_bad_input(text);
                  uint32_t* limbs = juno_big_scratch(count / 9u + 2u);
                  uint32_t n = 0;
                  juno_big_parse_digits(digits, count, limbs, &n);
                  return juno_bd_make(juno_big_make(sign, limbs, n), static_cast<int32_t>(scale));
                }

                extern "C" int32_t juno_big_decimal_parse(const char* text) {
                  return juno_bd_parse(text);
                }

                extern "C" int32_t juno_big_decimal_add(int32_t a, int32_t b) {
                  juno_big_prepare(6u * juno_bd_budget(a, b));
                  int32_t scale = juno_bd_scale(a) > juno_bd_scale(b) ? juno_bd_scale(a) : juno_bd_scale(b);
                  return juno_bd_make(juno_big_add_h(juno_bd_unscaled_at(a, scale), juno_bd_unscaled_at(b, scale)), scale);
                }

                extern "C" int32_t juno_big_decimal_subtract(int32_t a, int32_t b) {
                  juno_big_prepare(6u * juno_bd_budget(a, b));
                  int32_t scale = juno_bd_scale(a) > juno_bd_scale(b) ? juno_bd_scale(a) : juno_bd_scale(b);
                  return juno_bd_make(juno_big_sub_h(juno_bd_unscaled_at(a, scale), juno_bd_unscaled_at(b, scale)), scale);
                }

                extern "C" int32_t juno_big_decimal_multiply(int32_t a, int32_t b) {
                  juno_big_prepare(6u * juno_bd_budget(a, b));
                  return juno_bd_make(juno_big_mul_h(juno_bd_unscaled(a), juno_bd_unscaled(b)),
                      juno_bd_scale(a) + juno_bd_scale(b));
                }

                extern "C" int32_t juno_big_decimal_add_mc(int32_t a, int32_t b, int32_t context) {
                  juno_big_prepare(12u * juno_bd_budget(a, b));
                  int32_t scale = juno_bd_scale(a) > juno_bd_scale(b) ? juno_bd_scale(a) : juno_bd_scale(b);
                  return juno_bd_round_mc(juno_bd_make(juno_big_add_h(juno_bd_unscaled_at(a, scale),
                      juno_bd_unscaled_at(b, scale)), scale), context);
                }

                extern "C" int32_t juno_big_decimal_subtract_mc(int32_t a, int32_t b, int32_t context) {
                  juno_big_prepare(12u * juno_bd_budget(a, b));
                  int32_t scale = juno_bd_scale(a) > juno_bd_scale(b) ? juno_bd_scale(a) : juno_bd_scale(b);
                  return juno_bd_round_mc(juno_bd_make(juno_big_sub_h(juno_bd_unscaled_at(a, scale),
                      juno_bd_unscaled_at(b, scale)), scale), context);
                }

                extern "C" int32_t juno_big_decimal_multiply_mc(int32_t a, int32_t b, int32_t context) {
                  juno_big_prepare(12u * juno_bd_budget(a, b));
                  return juno_bd_round_mc(juno_bd_make(juno_big_mul_h(juno_bd_unscaled(a), juno_bd_unscaled(b)),
                      juno_bd_scale(a) + juno_bd_scale(b)), context);
                }

                extern "C" int32_t juno_big_decimal_negate(int32_t a) {
                  juno_big_prepare(4u * juno_big_budget(juno_bd_unscaled(a), juno_bd_unscaled(a)));
                  return juno_bd_make(juno_big_negate_h(juno_bd_unscaled(a)), juno_bd_scale(a));
                }

                extern "C" int32_t juno_big_decimal_abs(int32_t a) {
                  juno_big_prepare(4u * juno_big_budget(juno_bd_unscaled(a), juno_bd_unscaled(a)));
                  return juno_bd_make(juno_big_abs_h(juno_bd_unscaled(a)), juno_bd_scale(a));
                }

                extern "C" int32_t juno_big_decimal_round(int32_t a, int32_t context) {
                  juno_big_prepare(12u * juno_big_budget(juno_bd_unscaled(a), juno_bd_unscaled(a)) + 256u);
                  return juno_bd_round_mc(a, context);
                }

                extern "C" int32_t juno_big_decimal_signum(int32_t a) { return juno_big_sign_of(juno_bd_unscaled(a)); }
                extern "C" int32_t juno_big_decimal_scale(int32_t a) { return juno_bd_scale(a); }
                extern "C" int32_t juno_big_decimal_unscaled_value(int32_t a) { return juno_bd_unscaled(a); }

                extern "C" int32_t juno_big_decimal_precision(int32_t a) {
                  juno_big_prepare(4u * (juno_big_len_of(juno_bd_unscaled(a)) + 8u) * 2u);
                  return static_cast<int32_t>(juno_big_digits_h(juno_bd_unscaled(a)));
                }

                extern "C" int32_t juno_big_decimal_compare_to(int32_t a, int32_t b) {
                  juno_big_prepare(6u * juno_bd_budget(a, b));
                  return juno_bd_compare_h(a, b);
                }

                extern "C" int32_t juno_big_decimal_equals(int32_t a, int32_t b) {
                  if (b == 0) return 0;
                  return juno_bd_scale(a) == juno_bd_scale(b)
                      && juno_big_compare_h(juno_bd_unscaled(a), juno_bd_unscaled(b)) == 0 ? 1 : 0;
                }

                extern "C" int32_t juno_big_decimal_min(int32_t a, int32_t b) {
                  juno_big_prepare(6u * juno_bd_budget(a, b));
                  return juno_bd_compare_h(a, b) <= 0 ? a : b;
                }

                extern "C" int32_t juno_big_decimal_max(int32_t a, int32_t b) {
                  juno_big_prepare(6u * juno_bd_budget(a, b));
                  return juno_bd_compare_h(a, b) >= 0 ? a : b;
                }

                extern "C" int32_t juno_big_decimal_set_scale(int32_t a, int32_t scale, int32_t mode) {
                  int64_t gap = static_cast<int64_t>(scale) - juno_bd_scale(a);
                  juno_big_prepare(12u * (juno_big_budget(juno_bd_unscaled(a), juno_bd_unscaled(a)) +
                      4u * static_cast<uint32_t>((gap < 0 ? -gap : gap) / 9 + 4)));
                  return juno_bd_set_scale_h(a, scale, mode);
                }

                extern "C" int32_t juno_big_decimal_set_scale_exact(int32_t a, int32_t scale) {
                  return juno_big_decimal_set_scale(a, scale, JUNO_BIG_UNNECESSARY);
                }

                extern "C" int32_t juno_big_decimal_strip_trailing_zeros(int32_t a) {
                  juno_big_prepare(12u * juno_big_budget(juno_bd_unscaled(a), juno_bd_unscaled(a)) * 4u);
                  if (juno_big_sign_of(juno_bd_unscaled(a)) == 0) return juno_bd_zero_h();
                  return juno_bd_strip_to(juno_bd_unscaled(a), juno_bd_scale(a), -2147483647 - 1);
                }

                // Same value times 10^-places; a negative resulting scale is raised back to zero, as the JDK does.
                static int32_t juno_bd_move_point(int32_t a, int32_t places) {
                  int64_t scale = static_cast<int64_t>(juno_bd_scale(a)) + places;
                  juno_big_prepare(12u * juno_big_budget(juno_bd_unscaled(a), juno_bd_unscaled(a)) + 4u * 64u +
                      4u * static_cast<uint32_t>((scale < 0 ? -scale : 0) / 9 + 4));
                  if (scale >= 0) return juno_bd_make(juno_bd_unscaled(a), static_cast<int32_t>(scale));
                  return juno_bd_make(juno_big_scale_up_h(juno_bd_unscaled(a), static_cast<uint32_t>(-scale)), 0);
                }

                extern "C" int32_t juno_big_decimal_move_point_left(int32_t a, int32_t places) {
                  return juno_bd_move_point(a, places);
                }

                extern "C" int32_t juno_big_decimal_move_point_right(int32_t a, int32_t places) {
                  return juno_bd_move_point(a, -places);
                }

                extern "C" int32_t juno_big_decimal_pow(int32_t a, int32_t exponent) {
                  if (exponent < 0 || exponent > 999999999) return juno_big_arithmetic("Invalid operation");
                  int32_t unscaled = juno_bd_unscaled(a);
                  juno_big_prepare(12u * 4u * (juno_big_len_of(unscaled) * static_cast<uint32_t>(exponent) + 4u));
                  int32_t result = juno_big_one_h();
                  int32_t base = unscaled;
                  uint32_t remaining = static_cast<uint32_t>(exponent);
                  while (remaining != 0u) {
                    if ((remaining & 1u) != 0u) result = juno_big_mul_h(result, base);
                    remaining >>= 1;
                    if (remaining != 0u) base = juno_big_mul_h(base, base);
                  }
                  return juno_bd_make(result, juno_bd_scale(a) * exponent);
                }

                // Quotient to `scale` digits after the point, rounded per `mode`.
                static int32_t juno_bd_divide_scale(int32_t a, int32_t b, int32_t scale, int32_t mode) {
                  int32_t shift = scale - juno_bd_scale(a) + juno_bd_scale(b);
                  int32_t numerator = juno_bd_unscaled(a);
                  int32_t denominator = juno_bd_unscaled(b);
                  if (shift >= 0) {
                    numerator = juno_big_scale_up_h(numerator, static_cast<uint32_t>(shift));
                  } else {
                    denominator = juno_big_scale_up_h(denominator, static_cast<uint32_t>(-static_cast<int64_t>(shift)));
                  }
                  bool inexact = false;
                  int32_t rounded = juno_big_round_div_h(numerator, denominator, mode, false, &inexact);
                  if (inexact && mode == JUNO_BIG_UNNECESSARY) return 0;
                  return juno_bd_make(rounded, scale);
                }

                static uint32_t juno_bd_divide_budget(int32_t a, int32_t b, int64_t shift) {
                  return 12u * (juno_bd_budget(a, b) + 4u * static_cast<uint32_t>((shift < 0 ? -shift : shift) / 9 + 4));
                }

                extern "C" int32_t juno_big_decimal_divide_scale(int32_t a, int32_t b, int32_t scale, int32_t mode) {
                  if (juno_big_sign_of(juno_bd_unscaled(b)) == 0) {
                    return juno_big_arithmetic(juno_big_sign_of(juno_bd_unscaled(a)) == 0 ? "Division undefined"
                                                                                          : "Division by zero");
                  }
                  int64_t shift = static_cast<int64_t>(scale) - juno_bd_scale(a) + juno_bd_scale(b);
                  juno_big_prepare(juno_bd_divide_budget(a, b, shift));
                  return juno_bd_divide_scale(a, b, scale, mode);
                }

                extern "C" int32_t juno_big_decimal_divide_mode(int32_t a, int32_t b, int32_t mode) {
                  return juno_big_decimal_divide_scale(a, b, juno_bd_scale(a), mode);
                }

                // a / b rounded to context.precision significant digits, or exact when the precision is 0.
                extern "C" int32_t juno_big_decimal_divide_mc(int32_t a, int32_t b, int32_t context) {
                  int32_t ua = juno_bd_unscaled(a);
                  int32_t ub = juno_bd_unscaled(b);
                  if (juno_big_sign_of(ub) == 0) {
                    return juno_big_arithmetic(juno_big_sign_of(ua) == 0 ? "Division undefined" : "Division by zero");
                  }
                  int32_t preferred = juno_bd_scale(a) - juno_bd_scale(b);
                  int32_t precision = juno_big_words(context)[0];
                  int32_t mode = juno_big_words(context)[1];
                  juno_big_prepare(juno_bd_divide_budget(a, b, static_cast<int64_t>(precision) + 64) * 4u);
                  if (juno_big_sign_of(ua) == 0) return juno_bd_make(ua, preferred);
                  if (precision == 0) {
                    // Exact quotient: it terminates only if the reduced divisor has no prime factor besides 2 and 5.
                    int32_t divisor = juno_big_abs_h(ub);
                    int32_t g = juno_big_integer_gcd(ua, ub);
                    int32_t reduced = 0;
                    int32_t ignored = 0;
                    juno_big_divrem_h(divisor, g, &reduced, &ignored);
                    int32_t two = juno_big_of_long_h(2);
                    int32_t five = juno_big_of_long_h(5);
                    int32_t twos = 0;
                    int32_t fives = 0;
                    for (;;) {
                      int32_t quotient = 0;
                      int32_t remainder = 0;
                      juno_big_divrem_h(reduced, two, &quotient, &remainder);
                      if (juno_big_sign_of(remainder) != 0) break;
                      reduced = quotient;
                      twos++;
                    }
                    for (;;) {
                      int32_t quotient = 0;
                      int32_t remainder = 0;
                      juno_big_divrem_h(reduced, five, &quotient, &remainder);
                      if (juno_big_sign_of(remainder) != 0) break;
                      reduced = quotient;
                      fives++;
                    }
                    if (juno_big_compare_h(reduced, juno_big_one_h()) != 0) {
                      return juno_big_arithmetic("Non-terminating decimal expansion; no exact representable decimal result.");
                    }
                    int32_t extra = twos > fives ? twos : fives;
                    int32_t numerator = juno_big_scale_up_h(ua, static_cast<uint32_t>(extra));
                    int32_t quotient = 0;
                    int32_t remainder = 0;
                    juno_big_divrem_h(numerator, ub, &quotient, &remainder);
                    return juno_bd_strip_to(quotient, preferred + extra, preferred);
                  }
                  int32_t digitsA = static_cast<int32_t>(juno_big_digits_h(ua));
                  int32_t digitsB = static_cast<int32_t>(juno_big_digits_h(ub));
                  int32_t shift = precision + 1 + digitsB - digitsA;
                  int32_t numerator = ua;
                  int32_t denominator = ub;
                  if (shift >= 0) {
                    numerator = juno_big_scale_up_h(ua, static_cast<uint32_t>(shift));
                  } else {
                    denominator = juno_big_scale_up_h(ub, static_cast<uint32_t>(-shift));
                  }
                  int32_t quotient = 0;
                  int32_t remainder = 0;
                  juno_big_divrem_h(numerator, denominator, &quotient, &remainder);
                  bool sticky = juno_big_sign_of(remainder) != 0;
                  bool inexact = false;
                  int32_t rounded = juno_bd_round_to(quotient, preferred + shift, precision, mode, sticky, &inexact);
                  if (rounded == 0) return 0;
                  // An exact quotient is reported at the scale closest to the preferred one; an inexact one keeps its digits.
                  return inexact ? rounded : juno_bd_strip_to(juno_bd_unscaled(rounded), juno_bd_scale(rounded), preferred);
                }

                extern "C" int32_t juno_big_decimal_divide(int32_t a, int32_t b) {
                  static const int32_t exactContext[2] = {0, JUNO_BIG_UNNECESSARY};
                  return juno_big_decimal_divide_mc(a, b, juno_big_handle_of(const_cast<int32_t*>(exactContext)));
                }

                extern "C" int32_t juno_big_decimal_sqrt(int32_t a, int32_t context) {
                  int32_t unscaled = juno_bd_unscaled(a);
                  if (juno_big_sign_of(unscaled) < 0) return juno_big_arithmetic("Attempted square root of negative BigDecimal");
                  int32_t precision = juno_big_words(context)[0];
                  int32_t mode = juno_big_words(context)[1];
                  int32_t preferred = (juno_bd_scale(a) + 1) >> 1; // JDK 27: ceil(scale / 2)
                  if (juno_big_sign_of(unscaled) == 0) {
                    juno_big_prepare(64u);
                    return juno_bd_make(unscaled, preferred);
                  }
                  juno_big_prepare(4u * 64u * (juno_big_len_of(unscaled) + static_cast<uint32_t>(precision) / 4u + 16u) * 12u);
                  int32_t scale = juno_bd_scale(a);
                  int32_t radicand = unscaled;
                  if ((scale & 1) != 0) {
                    radicand = juno_big_scale_up_h(radicand, 1u);
                    scale++;
                  }
                  int32_t digits = static_cast<int32_t>(juno_big_digits_h(radicand));
                  // Scale the radicand by 10^(2m) so its integer root has at least precision + 1 digits.
                  int32_t wanted = (precision == 0 ? digits : precision) + 1;
                  int32_t m = wanted - (digits + 1) / 2;
                  if (m < 0) m = 0;
                  int32_t scaled = juno_big_scale_up_h(radicand, static_cast<uint32_t>(2 * m));
                  int32_t root = juno_big_integer_sqrt(scaled);
                  bool sticky = juno_big_compare_h(juno_big_mul_h(root, root), scaled) != 0;
                  int32_t rootScale = scale / 2 + m;
                  if (precision == 0) {
                    if (sticky) return juno_big_arithmetic("Computed square root not exact.");
                    return juno_bd_strip_to(root, rootScale, preferred);
                  }
                  bool inexact = false;
                  int32_t rounded = juno_bd_round_to(root, rootScale, precision, mode, sticky, &inexact);
                  if (rounded == 0) return 0;
                  // The JDK also drops trailing zeros of an inexact root, down to the preferred scale.
                  return juno_bd_strip_to(juno_bd_unscaled(rounded), juno_bd_scale(rounded), preferred);
                }

                // ---- BigDecimal text -----------------------------------------------------------------------------------

                static int32_t juno_bd_text(int32_t a, bool plain) {
                  int32_t unscaled = juno_bd_unscaled(a);
                  int32_t scale = juno_bd_scale(a);
                  juno_big_prepare(4u * (juno_big_len_of(unscaled) + 8u) * 4u + 64u);
                  uint32_t length = 0;
                  char* digits = juno_big_decimal(unscaled, &length);
                  bool negative = juno_big_sign_of(unscaled) < 0;
                  int64_t adjusted = -static_cast<int64_t>(scale) + (static_cast<int64_t>(length) - 1);
                  uint64_t zeros = scale < 0 ? static_cast<uint64_t>(-static_cast<int64_t>(scale)) : 0u;
                  uint64_t capacity = static_cast<uint64_t>(length) + 40u;
                  if (plain) capacity += zeros + static_cast<uint64_t>(scale > 0 ? scale : 0);
                  char* text = static_cast<char*>(juno_big_alloc(static_cast<uint32_t>(capacity)));
                  uint32_t out = 0;
                  if (negative) text[out++] = 45;
                  if (plain || (scale >= 0 && adjusted >= -6)) {
                    if (scale <= 0) {
                      for (uint32_t i = 0; i < length; i++) text[out++] = digits[i];
                      if (juno_big_sign_of(unscaled) != 0) {
                        for (uint64_t i = 0; i < zeros; i++) text[out++] = 48;
                      }
                    } else if (static_cast<int64_t>(length) > scale) {
                      uint32_t whole = length - static_cast<uint32_t>(scale);
                      for (uint32_t i = 0; i < whole; i++) text[out++] = digits[i];
                      text[out++] = 46;
                      for (uint32_t i = whole; i < length; i++) text[out++] = digits[i];
                    } else {
                      text[out++] = 48;
                      text[out++] = 46;
                      for (int64_t i = 0; i < static_cast<int64_t>(scale) - length; i++) text[out++] = 48;
                      for (uint32_t i = 0; i < length; i++) text[out++] = digits[i];
                    }
                  } else {
                    text[out++] = digits[0];
                    if (length > 1u) {
                      text[out++] = 46;
                      for (uint32_t i = 1; i < length; i++) text[out++] = digits[i];
                    }
                    if (adjusted != 0) {
                      text[out++] = 69;
                      uint64_t magnitude;
                      if (adjusted > 0) {
                        text[out++] = 43;
                        magnitude = static_cast<uint64_t>(adjusted);
                      } else {
                        text[out++] = 45;
                        magnitude = static_cast<uint64_t>(-adjusted);
                      }
                      char reversed[12];
                      uint32_t count = 0;
                      do {
                        reversed[count++] = static_cast<char>(48 + magnitude % 10u);
                        magnitude /= 10u;
                      } while (magnitude != 0u);
                      while (count > 0u) text[out++] = reversed[--count];
                    }
                  }
                  text[out] = 0;
                  return juno_big_handle_of(text);
                }

                extern "C" int32_t juno_big_decimal_to_string(int32_t a) { return juno_bd_text(a, false); }
                extern "C" int32_t juno_big_decimal_to_plain_string(int32_t a) { return juno_bd_text(a, true); }

                extern "C" int32_t juno_big_decimal_to_big_integer(int32_t a) {
                  int32_t scale = juno_bd_scale(a);
                  int64_t magnitude = scale < 0 ? -static_cast<int64_t>(scale) : scale;
                  juno_big_prepare(12u * (juno_big_budget(juno_bd_unscaled(a), juno_bd_unscaled(a)) +
                      4u * static_cast<uint32_t>(magnitude / 9 + 4)));
                  if (scale <= 0) return juno_big_scale_up_h(juno_bd_unscaled(a), static_cast<uint32_t>(-static_cast<int64_t>(scale)));
                  int32_t quotient = 0;
                  int32_t remainder = 0;
                  juno_big_divrem_h(juno_bd_unscaled(a), juno_big_pow10_h(static_cast<uint32_t>(scale)), &quotient, &remainder);
                  return quotient;
                }

                extern "C" int32_t juno_big_decimal_int_value(int32_t a) {
                  return juno_big_integer_int_value(juno_big_decimal_to_big_integer(a));
                }

                extern "C" int64_t juno_big_decimal_long_value(int32_t a) {
                  return juno_big_integer_long_value(juno_big_decimal_to_big_integer(a));
                }

                extern "C" JUNO_ASM_ABI double juno_big_decimal_double_value(int32_t a) {
                  int32_t text = juno_bd_text(a, false);
                  return strtod(juno_big_pointer_chars(text), nullptr);
                }
                """ + (valueOfDouble ? """

                extern "C" JUNO_ASM_ABI int32_t juno_big_decimal_value_of_double(double value) {
                  if (value != value || value - value != 0.0) return juno_big_number_format("Infinite or NaN");
                  char text[40];
                  juno_format_double(text, value);
                  return juno_bd_parse(text);
                }
                """ : ""))
                .replace("${JUNO_BIG_ARITHMETIC_CLASS_ID}", Integer.toString(arithmeticClassId))
                .replace("${JUNO_BIG_NUMBER_FORMAT_CLASS_ID}", Integer.toString(numberFormatClassId))
                .replace("${JUNO_BIG_ILLEGAL_ARGUMENT_CLASS_ID}", Integer.toString(illegalArgumentClassId));
    }
}

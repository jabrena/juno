
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
  int32_t message = juno_big_message("For input string: \"", text, "\"");
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

// The JDK's message for a zero divisor given an explicit scale: its long fast path, taken while the
// dividend and its power-of-ten rescale (under 10^19) fit a long, divides by a long zero.
static const char* juno_bd_scaled_zero_divide_message(int32_t a, int32_t b, int32_t scale) {
  const char* bigPath = "BigInteger divide by zero";
  int32_t unscaled = juno_bd_unscaled(a);
  uint32_t n = juno_big_len_of(unscaled);
  if (n > 2u) return bigPath;
  const uint32_t* limbs = juno_big_limbs_of(unscaled);
  uint64_t magnitude = (n > 0u ? limbs[0] : 0u) | (static_cast<uint64_t>(n > 1u ? limbs[1] : 0u) << 32);
  const uint64_t longMax = 0x7FFFFFFFFFFFFFFFull;
  if (magnitude > longMax) return bigPath;
  int64_t raise = static_cast<int64_t>(scale) + juno_bd_scale(b) - juno_bd_scale(a);
  if (raise <= 0) return -raise < 19 ? "/ by zero" : bigPath;
  if (raise >= 19) return bigPath;
  for (int64_t i = 0; i < raise; i++) {
    if (magnitude > longMax / 10u) return bigPath;
    magnitude *= 10u;
  }
  return "/ by zero";
}

extern "C" int32_t juno_big_decimal_divide_scale(int32_t a, int32_t b, int32_t scale, int32_t mode) {
  if (juno_big_sign_of(juno_bd_unscaled(b)) == 0) {
    return juno_big_arithmetic(juno_bd_scaled_zero_divide_message(a, b, scale));
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
    int32_t g = juno_big_gcd_h(ua, ub);
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
  int32_t root = juno_big_sqrt_h(scaled);
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


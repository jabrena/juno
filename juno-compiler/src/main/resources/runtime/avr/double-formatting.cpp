
// Fixed-precision formatting, not Java's shortest-round-trip Double.toString: the fractional part is
// truncated toward zero at 6 digits (trailing zeros trimmed). The same steps as the 32-bit boards, on
// the software binary64, so both print the same digits.
static void juno_format_double(char* buffer, juno_f64 value) {
  if (juno_f64_exp(value) == 0x7FF) juno_panic();
  bool negative = juno_f64_compare(value, 0) < 0;
  juno_f64 magnitude = negative ? value ^ 0x8000000000000000ull : value;
  if (juno_f64_compare(magnitude, ${BILLION}) >= 0) juno_panic();
  uint32_t integerPart = static_cast<uint32_t>(juno_f64_to_i64(magnitude));
  static constexpr uint32_t FRACTION_DIGITS = 6;
  static constexpr uint32_t FRACTION_SCALE = 1000000u;
  juno_f64 fraction = juno_f64_sub(magnitude, juno_f64_from_i64(integerPart));
  uint32_t fractionDigits = static_cast<uint32_t>(juno_f64_to_i64(juno_f64_mul(fraction, ${MILLION})));
  if (fractionDigits >= FRACTION_SCALE) fractionDigits = FRACTION_SCALE - 1u;
  char reversed[JUNO_STRING_SLOT_SIZE];
  uint32_t count = 0;
  uint32_t integerDigits = integerPart;
  do {
    reversed[count++] = static_cast<char>('0' + integerDigits % 10u);
    integerDigits /= 10u;
  } while (integerDigits != 0u);
  uint32_t index = 0;
  if (negative) buffer[index++] = '-';
  while (count > 0u) buffer[index++] = reversed[--count];
  if (fractionDigits != 0u) {
    char fractionText[FRACTION_DIGITS];
    for (uint32_t i = FRACTION_DIGITS; i > 0u; --i) {
      fractionText[i - 1u] = static_cast<char>('0' + fractionDigits % 10u);
      fractionDigits /= 10u;
    }
    uint32_t fractionLength = FRACTION_DIGITS;
    while (fractionLength > 1u && fractionText[fractionLength - 1u] == '0') fractionLength--;
    buffer[index++] = '.';
    for (uint32_t i = 0; i < fractionLength; ++i) buffer[index++] = fractionText[i];
  } else {
    // Java's Double.toString never prints a bare integer: 3.0, not 3.
    buffer[index++] = '.';
    buffer[index++] = '0';
  }
  buffer[index] = '\0';
}

extern "C" int32_t juno_string_value_of_double(juno_f64 value) {
  char* buffer = juno_string_slots[juno_string_slot_cursor];
  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
  juno_format_double(buffer, value);
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(buffer));
}

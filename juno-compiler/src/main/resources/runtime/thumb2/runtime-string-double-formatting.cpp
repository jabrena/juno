// Fixed-precision formatting, not Java's shortest-round-trip Double.toString: the
// fractional part is truncated toward zero at 6 digits (trailing zeros trimmed), so a
// value whose exact binary representation sits just below the intended decimal (e.g.
// 21.2 stored as 21.199999999999999...) can format one unit low in the last digit.
// Formats into a caller-owned buffer so string concatenation can format without taking a pool slot.
static void juno_format_double(char* buffer, double value) {
  if (isnan(value) || isinf(value)) juno_panic();
  bool negative = value < 0.0;
  double magnitude = negative ? -value : value;
  if (magnitude >= 1.0e9) juno_panic();
  auto integerPart = static_cast<uint32_t>(magnitude);
  static constexpr uint32_t FRACTION_DIGITS = 6;
  static constexpr uint32_t FRACTION_SCALE = 1000000u;
  double fraction = magnitude - static_cast<double>(integerPart);
  auto fractionDigits = static_cast<uint32_t>(fraction * static_cast<double>(FRACTION_SCALE));
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

extern "C" JUNO_ASM_ABI int32_t juno_string_value_of_double(double value) {
  char* buffer = juno_string_slots[juno_string_slot_cursor];
  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
  juno_format_double(buffer, value);
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(buffer));
}


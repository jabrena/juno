
static bool juno_json_parse_double(const char* start, const char* end, juno_f64* result) {
  const char* parsedEnd;
  if (!juno_json_parse_number(start, end, &parsedEnd) || parsedEnd != end) return false;
  bool negative = *start == '-';
  const char* p = start + (negative ? 1 : 0);
  juno_f64 significand = 0;
  int32_t significantDigits = 0;
  int64_t decimalExponent = 0;
  while (p < end && *p >= '0' && *p <= '9') {
    int32_t digit = *p++ - '0';
    if (significantDigits < 18 && (significantDigits > 0 || digit != 0)) {
      significand = juno_f64_add(juno_f64_mul(significand, ${TEN}), juno_f64_from_i32(digit));
      significantDigits++;
    } else if (significantDigits >= 18) {
      decimalExponent++;
    }
  }
  if (p < end && *p == '.') {
    p++;
    while (p < end && *p >= '0' && *p <= '9') {
      int32_t digit = *p++ - '0';
      if (significantDigits == 0 && digit == 0) {
        decimalExponent--;
      } else if (significantDigits < 18) {
        significand = juno_f64_add(juno_f64_mul(significand, ${TEN}), juno_f64_from_i32(digit));
        significantDigits++;
        decimalExponent--;
      }
    }
  }
  if (p < end && (*p == 'e' || *p == 'E')) {
    p++;
    bool exponentNegative = false;
    if (*p == '+' || *p == '-') { exponentNegative = *p == '-'; p++; }
    int32_t exponent = 0;
    while (p < end && *p >= '0' && *p <= '9') {
      int32_t digit = *p++ - '0';
      if (exponent < 100000) exponent = exponent * 10 + digit;
    }
    decimalExponent += exponentNegative ? -static_cast<int64_t>(exponent) : exponent;
  }
  juno_f64 signedZero = negative ? 0x8000000000000000ull : 0;
  if ((significand << 1) == 0) { *result = signedZero; return true; }
  if (decimalExponent > 400) return false;
  if (decimalExponent < -400) { *result = signedZero; return true; }
  juno_f64 scale = ${ONE};
  int32_t scalePower = static_cast<int32_t>(decimalExponent < 0 ? -decimalExponent : decimalExponent);
  for (int32_t i = 0; i < scalePower; i++) scale = juno_f64_mul(scale, ${TEN});
  juno_f64 value = decimalExponent < 0 ? juno_f64_div(significand, scale) : juno_f64_mul(significand, scale);
  if (juno_f64_exp(value) == 0x7FF) return false;
  *result = negative ? value ^ 0x8000000000000000ull : value;
  return true;
}

extern "C" juno_f64 juno_json_get_double(const uint8_t* buffer, int32_t length, const char* key) {
  const char* start;
  const char* end;
  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0;
  juno_f64 value;
  return juno_json_parse_double(start, end, &value) ? value : 0;
}


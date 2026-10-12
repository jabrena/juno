static bool juno_json_parse_double(const char* start, const char* end, double* result) {
  const char* parsedEnd;
  if (!juno_json_parse_number(start, end, &parsedEnd) || parsedEnd != end) return false;
  bool negative = *start == '-';
  const char* p = start + (negative ? 1 : 0);
  double significand = 0.0;
  int32_t significantDigits = 0;
  int64_t decimalExponent = 0;
  while (p < end && *p >= '0' && *p <= '9') {
    int32_t digit = *p++ - '0';
    if (significantDigits < 18 && (significantDigits > 0 || digit != 0)) {
      significand = significand * 10.0 + digit;
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
        significand = significand * 10.0 + digit;
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
  if (significand == 0.0) { *result = negative ? -0.0 : 0.0; return true; }
  if (decimalExponent > 400) return false;
  if (decimalExponent < -400) { *result = negative ? -0.0 : 0.0; return true; }
  double scale = 1.0;
  int32_t scalePower = static_cast<int32_t>(decimalExponent < 0 ? -decimalExponent : decimalExponent);
  for (int32_t i = 0; i < scalePower; i++) scale *= 10.0;
  double value = decimalExponent < 0 ? significand / scale : significand * scale;
  if (!isfinite(value)) return false;
  *result = negative ? -value : value;
  return true;
}

extern "C" JUNO_ASM_ABI double juno_json_get_double(const uint8_t* buffer, int32_t length, const char* key) {
  const char* start;
  const char* end;
  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0.0;
  double value;
  return juno_json_parse_double(start, end, &value) ? value : 0.0;
}


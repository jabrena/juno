
// Java's min/max: NaN wins, and -0.0 is less than +0.0.
static juno_f64 juno_java_min(juno_f64 a, juno_f64 b) {
  if (juno_f64_is_nan(a)) return a;
  if (((a | b) << 1) == 0 && (b >> 63) != 0) return b;
  int8_t order = juno_f64_compare(a, b);
  return order == -1 || order == 0 ? a : b;
}
static juno_f64 juno_java_max(juno_f64 a, juno_f64 b) {
  if (juno_f64_is_nan(a)) return a;
  if (((a | b) << 1) == 0 && (a >> 63) != 0) return b;
  int8_t order = juno_f64_compare(a, b);
  return order == 1 || order == 0 ? a : b;
}
static juno_f64 juno_java_clamp(juno_f64 value, juno_f64 lo, juno_f64 hi) {
  if (juno_f64_is_nan(lo) || juno_f64_is_nan(hi) || juno_f64_compare(lo, hi) > 0
      || (((lo | hi) << 1) == 0 && (lo >> 63) == 0 && (hi >> 63) != 0)) juno_panic();
  return juno_java_min(hi, juno_java_max(value, lo));
}

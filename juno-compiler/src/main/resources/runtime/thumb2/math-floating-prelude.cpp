static inline float juno_java_fmin(float a, float b) {
  if (a != a) return a;
  if (a == 0.0f && b == 0.0f && signbit(b)) return b;
  return a <= b ? a : b;
}
static inline float juno_java_fmax(float a, float b) {
  if (a != a) return a;
  if (a == 0.0f && b == 0.0f && signbit(a)) return b;
  return a >= b ? a : b;
}
static inline double juno_java_dmin(double a, double b) {
  if (a != a) return a;
  if (a == 0.0 && b == 0.0 && signbit(b)) return b;
  return a <= b ? a : b;
}
static inline double juno_java_dmax(double a, double b) {
  if (a != a) return a;
  if (a == 0.0 && b == 0.0 && signbit(a)) return b;
  return a >= b ? a : b;
}

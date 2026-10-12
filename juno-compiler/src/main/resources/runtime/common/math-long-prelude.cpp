static inline int64_t juno_java_floor_div_long(int64_t x, int64_t y) {
  if (y == 0) juno_panic();
  if (y == -1) return static_cast<int64_t>(0u - static_cast<uint64_t>(x));
  int64_t q = x / y;
  if ((x % y != 0) && ((x ^ y) < 0)) q--;
  return q;
}
static inline int64_t juno_java_floor_mod_long(int64_t x, int64_t y) {
  if (y == 0) juno_panic();
  if (y == -1) return 0;
  int64_t m = x % y;
  if (m != 0 && ((m ^ y) < 0)) m += y;
  return m;
}

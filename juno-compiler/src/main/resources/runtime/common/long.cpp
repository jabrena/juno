
extern "C" int64_t juno_ladd(int64_t a, int64_t b) {
  return static_cast<int64_t>(static_cast<uint64_t>(a) + static_cast<uint64_t>(b));
}
extern "C" int64_t juno_lsub(int64_t a, int64_t b) {
  return static_cast<int64_t>(static_cast<uint64_t>(a) - static_cast<uint64_t>(b));
}
extern "C" int64_t juno_lmul(int64_t a, int64_t b) {
  return static_cast<int64_t>(static_cast<uint64_t>(a) * static_cast<uint64_t>(b));
}
extern "C" int64_t juno_ldiv(int64_t a, int64_t b) {
  if (b == 0) juno_panic();
  if (a == INT64_MIN && b == -1) return INT64_MIN;
  return a / b;
}
extern "C" int64_t juno_lrem(int64_t a, int64_t b) {
  if (b == 0) juno_panic();
  if (a == INT64_MIN && b == -1) return 0;
  return a % b;
}
extern "C" int64_t juno_lneg(int64_t value) {
  return static_cast<int64_t>(0ull - static_cast<uint64_t>(value));
}
extern "C" int64_t juno_lshl(int64_t a, int32_t b) {
  return static_cast<int64_t>(static_cast<uint64_t>(a) << (b & 63));
}
extern "C" int64_t juno_lshr(int64_t a, int32_t b) {
  uint32_t shift = static_cast<uint32_t>(b) & 63u;
  uint64_t value = static_cast<uint64_t>(a);
  if (shift == 0 || a >= 0) return static_cast<int64_t>(value >> shift);
  return static_cast<int64_t>((value >> shift) | (~0ull << (64u - shift)));
}
extern "C" int64_t juno_lushr(int64_t a, int32_t b) {
  return static_cast<int64_t>(static_cast<uint64_t>(a) >> (b & 63));
}
extern "C" int64_t juno_land(int64_t a, int64_t b) { return a & b; }
extern "C" int64_t juno_lor(int64_t a, int64_t b) { return a | b; }
extern "C" int64_t juno_lxor(int64_t a, int64_t b) { return a ^ b; }
extern "C" int32_t juno_lcmp(int64_t a, int64_t b) {
  return (a > b) - (a < b);
}


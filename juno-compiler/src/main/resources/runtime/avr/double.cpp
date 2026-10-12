
extern "C" juno_f64 juno_dadd(juno_f64 a, juno_f64 b) { return juno_f64_add(a, b); }
extern "C" juno_f64 juno_dsub(juno_f64 a, juno_f64 b) { return juno_f64_sub(a, b); }
extern "C" juno_f64 juno_dmul(juno_f64 a, juno_f64 b) { return juno_f64_mul(a, b); }
extern "C" juno_f64 juno_ddiv(juno_f64 a, juno_f64 b) { return juno_f64_div(a, b); }
extern "C" juno_f64 juno_drem(juno_f64 a, juno_f64 b) { return juno_f64_rem(a, b); }
extern "C" int32_t juno_dcmp(juno_f64 a, juno_f64 b, int32_t nanResult) {
  int8_t order = juno_f64_compare(a, b);
  return order == 2 ? nanResult : order;
}
extern "C" juno_f64 juno_i2d(int32_t value) { return juno_f64_from_i32(value); }
extern "C" int32_t juno_d2i(juno_f64 value) { return juno_f64_to_i32(value); }
extern "C" juno_f64 juno_l2d(int64_t value) { return juno_f64_from_i64(value); }
extern "C" int64_t juno_d2l(juno_f64 value) { return juno_f64_to_i64(value); }
extern "C" juno_f64 juno_f2d(juno_f32 value) { return juno_f64_from_f32(value); }
extern "C" juno_f32 juno_d2f(juno_f64 value) { return juno_f64_to_f32(value); }


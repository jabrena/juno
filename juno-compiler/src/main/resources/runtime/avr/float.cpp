
extern "C" juno_f32 juno_fadd(juno_f32 a, juno_f32 b) { return juno_f64_to_f32(juno_f64_add(juno_f32_wide(a), juno_f32_wide(b))); }
extern "C" juno_f32 juno_fsub(juno_f32 a, juno_f32 b) { return juno_f64_to_f32(juno_f64_sub(juno_f32_wide(a), juno_f32_wide(b))); }
extern "C" juno_f32 juno_fmul(juno_f32 a, juno_f32 b) { return juno_f64_to_f32(juno_f64_mul(juno_f32_wide(a), juno_f32_wide(b))); }
extern "C" juno_f32 juno_fdiv(juno_f32 a, juno_f32 b) { return juno_f64_to_f32(juno_f64_div(juno_f32_wide(a), juno_f32_wide(b))); }
extern "C" juno_f32 juno_frem(juno_f32 a, juno_f32 b) { return juno_f64_to_f32(juno_f64_rem(juno_f32_wide(a), juno_f32_wide(b))); }
extern "C" int32_t juno_fcmp(juno_f32 a, juno_f32 b, int32_t nanResult) {
  int8_t order = juno_f64_compare(juno_f32_wide(a), juno_f32_wide(b));
  return order == 2 ? nanResult : order;
}
extern "C" juno_f32 juno_i2f(int32_t value) { return juno_f64_to_f32(juno_f64_from_i32(value)); }
extern "C" int32_t juno_f2i(juno_f32 value) { return juno_f64_to_i32(juno_f32_wide(value)); }
extern "C" juno_f32 juno_l2f(int64_t value) { return juno_f32_from_i64(value); }
extern "C" int64_t juno_f2l(juno_f32 value) { return juno_f64_to_i64(juno_f32_wide(value)); }


#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>
#include <iostream>
#include <sstream>

#define JUNO_ASM_ABI
struct SerialMock { void print(const char*) {} void print(uint32_t) {} void println(uint32_t) {} } Serial;
static uint8_t juno_arena[1 << 26] __attribute__((aligned(8)));
static uint32_t juno_arena_used = 0;
static constexpr uint32_t JUNO_GC_NO_NEXT = 0xFFFFFFFFu;
static uint32_t juno_gc_free_list_head = JUNO_GC_NO_NEXT;
static uint32_t juno_gc_round_up4(uint32_t v) { return (v + 3u) & ~3u; }
static uint32_t* juno_gc_header_at(uint32_t) { return nullptr; }
static uint32_t juno_gc_block_size(uint32_t h) { return h; }
static void juno_gc_collect() {}
static void* juno_gc_try_allocate(uint32_t padded) {
  if (juno_arena_used + padded + 8u > sizeof(juno_arena)) return nullptr;
  void* p = &juno_arena[juno_arena_used];
  juno_arena_used += padded;
  return p;
}
static void juno_panic() { fprintf(stderr, "PANIC\n"); abort(); }
static void* juno_alloc(uint32_t size, uint32_t) { void* p = juno_gc_try_allocate(juno_gc_round_up4(size)); memset(p, 0, size); return p; }

static std::vector<const void*> handles;
static std::unordered_map<const void*, int32_t> handleIds;
static int32_t juno_big_handle_of(const void* p) {
  if (p == nullptr) return 0;
  auto it = handleIds.find(p);
  if (it != handleIds.end()) return it->second;
  handles.push_back(p);
  int32_t id = (int32_t)handles.size();
  handleIds[p] = id;
  return id;
}
static int32_t* juno_big_pointer(int32_t h) { return (int32_t*)handles[h - 1]; }
static const char* juno_big_pointer_chars(int32_t h) { return (const char*)handles[h - 1]; }

static int32_t pendingClass = 0;
static std::string pendingMessage;
static void juno_throw_raise(int32_t h) {
  int32_t* e = juno_big_pointer(h);
  pendingClass = e[0];
  pendingMessage = juno_big_pointer_chars(e[1]);
}
static void juno_format_double(char* buffer, double v) { snprintf(buffer, 40, "%.17g", v); }

#include "runtime.inc"

static const char* className(int c) { return c == 1 ? "ArithmeticException" : c == 2 ? "NumberFormatException" : "IllegalArgumentException"; }

int main() {
  std::string line;
  while (std::getline(std::cin, line)) {
    std::istringstream in(line);
    std::string op;
    in >> op;
    std::vector<std::string> a;
    std::string t;
    while (in >> t) a.push_back(t);
    pendingClass = 0;
    std::string out;
    auto bi = [&](int i) { return juno_big_integer_parse(a[i].c_str()); };
    auto bd = [&](int i) { return juno_big_decimal_parse(a[i].c_str()); };
    auto mc = [&](int i, int j) { return juno_big_math_context_new(atoi(a[i].c_str()), atoi(a[j].c_str())); };
    auto str = [&](int32_t h) { return std::string(juno_big_pointer_chars(h)); };
    auto bits = [&](double d) { uint64_t u; memcpy(&u, &d, 8); char b[32]; snprintf(b, 32, "%016llx", (unsigned long long)u); return std::string(b); };
    int32_t r = 0;
    bool done = false;
    // BigInteger
    if (op == "bi_add") { r = juno_big_integer_add(bi(0), bi(1)); }
    else if (op == "bi_sub") { r = juno_big_integer_subtract(bi(0), bi(1)); }
    else if (op == "bi_mul") { r = juno_big_integer_multiply(bi(0), bi(1)); }
    else if (op == "bi_div") { r = juno_big_integer_divide(bi(0), bi(1)); }
    else if (op == "bi_rem") { r = juno_big_integer_remainder(bi(0), bi(1)); }
    else if (op == "bi_mod") { r = juno_big_integer_mod(bi(0), bi(1)); }
    else if (op == "bi_pow") { r = juno_big_integer_pow(bi(0), atoi(a[1].c_str())); }
    else if (op == "bi_sqrt") { r = juno_big_integer_sqrt(bi(0)); }
    else if (op == "bi_gcd") { r = juno_big_integer_gcd(bi(0), bi(1)); }
    else if (op == "bi_shl") { r = juno_big_integer_shift_left(bi(0), atoi(a[1].c_str())); }
    else if (op == "bi_shr") { r = juno_big_integer_shift_right(bi(0), atoi(a[1].c_str())); }
    else if (op == "bi_neg") { r = juno_big_integer_negate(bi(0)); }
    else if (op == "bi_abs") { r = juno_big_integer_abs(bi(0)); }
    else if (op == "bi_min") { r = juno_big_integer_min(bi(0), bi(1)); }
    else if (op == "bi_max") { r = juno_big_integer_max(bi(0), bi(1)); }
    else if (op == "bi_cmp") { out = std::to_string(juno_big_integer_compare_to(bi(0), bi(1))); done = true; }
    else if (op == "bi_eq") { out = std::to_string(juno_big_integer_equals(bi(0), bi(1))); done = true; }
    else if (op == "bi_signum") { out = std::to_string(juno_big_integer_signum(bi(0))); done = true; }
    else if (op == "bi_bitlen") { out = std::to_string(juno_big_integer_bit_length(bi(0))); done = true; }
    else if (op == "bi_int") { out = std::to_string(juno_big_integer_int_value(bi(0))); done = true; }
    else if (op == "bi_long") { out = std::to_string((long long)juno_big_integer_long_value(bi(0))); done = true; }
    else if (op == "bi_dbl") { out = bits(juno_big_integer_double_value(bi(0))); done = true; }
    else if (op == "bi_valueof") { r = juno_big_integer_value_of(atoll(a[0].c_str())); }
    else if (op == "bi_str") { out = str(juno_big_integer_to_string(bi(0))); done = true; }
    // BigDecimal
    else if (op == "bd_add") { r = juno_big_decimal_add(bd(0), bd(1)); }
    else if (op == "bd_sub") { r = juno_big_decimal_subtract(bd(0), bd(1)); }
    else if (op == "bd_mul") { r = juno_big_decimal_multiply(bd(0), bd(1)); }
    else if (op == "bd_addmc") { r = juno_big_decimal_add_mc(bd(0), bd(1), mc(2, 3)); }
    else if (op == "bd_submc") { r = juno_big_decimal_subtract_mc(bd(0), bd(1), mc(2, 3)); }
    else if (op == "bd_mulmc") { r = juno_big_decimal_multiply_mc(bd(0), bd(1), mc(2, 3)); }
    else if (op == "bd_div") { r = juno_big_decimal_divide(bd(0), bd(1)); }
    else if (op == "bd_divs") { r = juno_big_decimal_divide_scale(bd(0), bd(1), atoi(a[2].c_str()), atoi(a[3].c_str())); }
    else if (op == "bd_divm") { r = juno_big_decimal_divide_mode(bd(0), bd(1), atoi(a[2].c_str())); }
    else if (op == "bd_divmc") { r = juno_big_decimal_divide_mc(bd(0), bd(1), mc(2, 3)); }
    else if (op == "bd_sqrt") { r = juno_big_decimal_sqrt(bd(0), mc(1, 2)); }
    else if (op == "bd_round") { r = juno_big_decimal_round(bd(0), mc(1, 2)); }
    else if (op == "bd_setscale") { r = juno_big_decimal_set_scale(bd(0), atoi(a[1].c_str()), atoi(a[2].c_str())); }
    else if (op == "bd_setscale0") { r = juno_big_decimal_set_scale_exact(bd(0), atoi(a[1].c_str())); }
    else if (op == "bd_pow") { r = juno_big_decimal_pow(bd(0), atoi(a[1].c_str())); }
    else if (op == "bd_strip") { r = juno_big_decimal_strip_trailing_zeros(bd(0)); }
    else if (op == "bd_neg") { r = juno_big_decimal_negate(bd(0)); }
    else if (op == "bd_abs") { r = juno_big_decimal_abs(bd(0)); }
    else if (op == "bd_min") { r = juno_big_decimal_min(bd(0), bd(1)); }
    else if (op == "bd_max") { r = juno_big_decimal_max(bd(0), bd(1)); }
    else if (op == "bd_mpl") { r = juno_big_decimal_move_point_left(bd(0), atoi(a[1].c_str())); }
    else if (op == "bd_mpr") { r = juno_big_decimal_move_point_right(bd(0), atoi(a[1].c_str())); }
    else if (op == "bd_valueof") { r = juno_big_decimal_value_of_scaled(atoll(a[0].c_str()), atoi(a[1].c_str())); }
    else if (op == "bd_cmp") { out = std::to_string(juno_big_decimal_compare_to(bd(0), bd(1))); done = true; }
    else if (op == "bd_eq") { out = std::to_string(juno_big_decimal_equals(bd(0), bd(1))); done = true; }
    else if (op == "bd_signum") { out = std::to_string(juno_big_decimal_signum(bd(0))); done = true; }
    else if (op == "bd_scale") { out = std::to_string(juno_big_decimal_scale(bd(0))); done = true; }
    else if (op == "bd_prec") { out = std::to_string(juno_big_decimal_precision(bd(0))); done = true; }
    else if (op == "bd_unscaled") { out = str(juno_big_integer_to_string(juno_big_decimal_unscaled_value(bd(0)))); done = true; }
    else if (op == "bd_bi") { out = str(juno_big_integer_to_string(juno_big_decimal_to_big_integer(bd(0)))); done = true; }
    else if (op == "bd_int") { out = std::to_string(juno_big_decimal_int_value(bd(0))); done = true; }
    else if (op == "bd_long") { out = std::to_string((long long)juno_big_decimal_long_value(bd(0))); done = true; }
    else if (op == "bd_dbl") { out = bits(juno_big_decimal_double_value(bd(0))); done = true; }
    else if (op == "bd_str") { out = str(juno_big_decimal_to_string(bd(0))); done = true; }
    else if (op == "bd_plain") { out = str(juno_big_decimal_to_plain_string(bd(0))); done = true; }
    else { out = "?"; done = true; }
    if (pendingClass != 0) {
      printf("EXC %s %s\n", className(pendingClass), pendingMessage.c_str());
    } else if (done) {
      printf("%s\n", out.c_str());
    } else if (op.rfind("bi_", 0) == 0) {
      printf("%s\n", str(juno_big_integer_to_string(r)).c_str());
    } else {
      printf("%s|%d\n", str(juno_big_decimal_to_string(r)).c_str(), juno_big_decimal_scale(r));
    }
    fflush(stdout);
  }
}

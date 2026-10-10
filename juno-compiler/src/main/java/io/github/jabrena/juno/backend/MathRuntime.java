package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.intrinsic.Intrinsic;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code java.lang.Math} lowered to {@code extern "C"} runtime-shim functions, one per supported
 * overload, each emitted only when the program reaches it.
 *
 * <p>Transcendental functions delegate to the toolchain's {@code libm}; everything whose Java semantics
 * differ from C's is spelled out explicitly: {@code min}/{@code max} of {@code -0.0}/{@code NaN},
 * {@code round}'s ties-toward-positive-infinity and saturating narrowing, {@code pow}'s NaN rules, and
 * {@code floorDiv}/{@code floorMod}'s overflow. Where Java throws ({@code ArithmeticException} for a zero
 * divisor, {@code IllegalArgumentException} for {@code clamp(min > max)}), the shim panics, like Juno's
 * native integer division.
 */
final class MathRuntime {
    /** Shared helpers several functions build on; {@code static inline}, so unused ones cost nothing. */
    private static final String PRELUDE = """
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
            """;

    private static final Map<Intrinsic, String> SOURCES = new EnumMap<>(Intrinsic.class);

    static {
        define(Intrinsic.MATH_ABS_INT, "int32_t", "int32_t a",
                "return a < 0 ? static_cast<int32_t>(0u - static_cast<uint32_t>(a)) : a;");
        define(Intrinsic.MATH_ABS_LONG, "int64_t", "int64_t a",
                "return a < 0 ? static_cast<int64_t>(0u - static_cast<uint64_t>(a)) : a;");
        define(Intrinsic.MATH_ABS_FLOAT, "float", "float a", "return fabsf(a);");
        define(Intrinsic.MATH_ABS_DOUBLE, "double", "double a", "return fabs(a);");
        define(Intrinsic.MATH_MIN_INT, "int32_t", "int32_t a, int32_t b", "return a <= b ? a : b;");
        define(Intrinsic.MATH_MIN_LONG, "int64_t", "int64_t a, int64_t b", "return a <= b ? a : b;");
        define(Intrinsic.MATH_MIN_FLOAT, "float", "float a, float b", "return juno_java_fmin(a, b);");
        define(Intrinsic.MATH_MIN_DOUBLE, "double", "double a, double b", "return juno_java_dmin(a, b);");
        define(Intrinsic.MATH_MAX_INT, "int32_t", "int32_t a, int32_t b", "return a >= b ? a : b;");
        define(Intrinsic.MATH_MAX_LONG, "int64_t", "int64_t a, int64_t b", "return a >= b ? a : b;");
        define(Intrinsic.MATH_MAX_FLOAT, "float", "float a, float b", "return juno_java_fmax(a, b);");
        define(Intrinsic.MATH_MAX_DOUBLE, "double", "double a, double b", "return juno_java_dmax(a, b);");
        define(Intrinsic.MATH_CLAMP_INT, "int32_t", "int64_t value, int32_t lo, int32_t hi",
                "if (lo > hi) juno_panic(); "
                        + "return value < lo ? lo : value > hi ? hi : static_cast<int32_t>(value);");
        define(Intrinsic.MATH_CLAMP_LONG, "int64_t", "int64_t value, int64_t lo, int64_t hi",
                "if (lo > hi) juno_panic(); return value < lo ? lo : value > hi ? hi : value;");
        define(Intrinsic.MATH_CLAMP_FLOAT, "float", "float value, float lo, float hi",
                "if (lo != lo || hi != hi || lo > hi || (lo == 0.0f && hi == 0.0f && !signbit(lo) && signbit(hi))) "
                        + "juno_panic(); return juno_java_fmin(hi, juno_java_fmax(value, lo));");
        define(Intrinsic.MATH_CLAMP_DOUBLE, "double", "double value, double lo, double hi",
                "if (lo != lo || hi != hi || lo > hi || (lo == 0.0 && hi == 0.0 && !signbit(lo) && signbit(hi))) "
                        + "juno_panic(); return juno_java_dmin(hi, juno_java_dmax(value, lo));");
        define(Intrinsic.MATH_FLOOR_DIV_INT, "int32_t", "int32_t x, int32_t y",
                "if (y == 0) juno_panic(); "
                        + "if (y == -1) return static_cast<int32_t>(0u - static_cast<uint32_t>(x)); "
                        + "int32_t q = x / y; if ((x % y != 0) && ((x ^ y) < 0)) q--; return q;");
        define(Intrinsic.MATH_FLOOR_DIV_LONG, "int64_t", "int64_t x, int64_t y",
                "return juno_java_floor_div_long(x, y);");
        define(Intrinsic.MATH_FLOOR_DIV_LONG_INT, "int64_t", "int64_t x, int32_t y",
                "return juno_java_floor_div_long(x, y);");
        define(Intrinsic.MATH_FLOOR_MOD_INT, "int32_t", "int32_t x, int32_t y",
                "if (y == 0) juno_panic(); if (y == -1) return 0; "
                        + "int32_t m = x % y; if (m != 0 && ((m ^ y) < 0)) m += y; return m;");
        define(Intrinsic.MATH_FLOOR_MOD_LONG, "int64_t", "int64_t x, int64_t y",
                "return juno_java_floor_mod_long(x, y);");
        define(Intrinsic.MATH_FLOOR_MOD_LONG_INT, "int32_t", "int64_t x, int32_t y",
                "return static_cast<int32_t>(juno_java_floor_mod_long(x, y));");
        define(Intrinsic.MATH_SIGNUM_FLOAT, "float", "float a",
                "if (a != a || a == 0.0f) return a; return a > 0.0f ? 1.0f : -1.0f;");
        define(Intrinsic.MATH_SIGNUM_DOUBLE, "double", "double a",
                "if (a != a || a == 0.0) return a; return a > 0.0 ? 1.0 : -1.0;");
        // x - floor(x) is exact here (Sterbenz), so ties land on exactly 0.5 and round up, as Java requires.
        define(Intrinsic.MATH_ROUND_FLOAT, "int32_t", "float a",
                "if (a != a) return 0; float f = floorf(a); float r = (a - f) >= 0.5f ? f + 1.0f : f; "
                        + "if (r >= 2147483648.0f) return INT32_MAX; if (r <= -2147483648.0f) return INT32_MIN; "
                        + "return static_cast<int32_t>(r);");
        define(Intrinsic.MATH_ROUND_DOUBLE, "int64_t", "double a",
                "if (a != a) return 0; double f = floor(a); double r = (a - f) >= 0.5 ? f + 1.0 : f; "
                        + "if (r >= 9223372036854775808.0) return INT64_MAX; "
                        + "if (r <= -9223372036854775808.0) return INT64_MIN; return static_cast<int64_t>(r);");
        define(Intrinsic.MATH_FLOOR, "double", "double a", "return floor(a);");
        define(Intrinsic.MATH_CEIL, "double", "double a", "return ceil(a);");
        define(Intrinsic.MATH_SQRT, "double", "double a", "return sqrt(a);");
        define(Intrinsic.MATH_CBRT, "double", "double a", "return cbrt(a);");
        // C's pow(1, NaN) and pow(-1, +-inf) are 1; Java's are NaN.
        define(Intrinsic.MATH_POW, "double", "double x, double y",
                "if (y != y) return y; if (fabs(x) == 1.0 && isinf(y)) return NAN; return pow(x, y);");
        define(Intrinsic.MATH_HYPOT, "double", "double x, double y", "return hypot(x, y);");
        define(Intrinsic.MATH_EXP, "double", "double a", "return exp(a);");
        define(Intrinsic.MATH_LOG, "double", "double a", "return log(a);");
        define(Intrinsic.MATH_LOG10, "double", "double a", "return log10(a);");
        define(Intrinsic.MATH_SIN, "double", "double a", "return sin(a);");
        define(Intrinsic.MATH_COS, "double", "double a", "return cos(a);");
        define(Intrinsic.MATH_TAN, "double", "double a", "return tan(a);");
        define(Intrinsic.MATH_ASIN, "double", "double a", "return asin(a);");
        define(Intrinsic.MATH_ACOS, "double", "double a", "return acos(a);");
        define(Intrinsic.MATH_ATAN, "double", "double a", "return atan(a);");
        define(Intrinsic.MATH_ATAN2, "double", "double y, double x", "return atan2(y, x);");
        // Same constants java.lang.Math multiplies by, so results match bit for bit.
        define(Intrinsic.MATH_TO_RADIANS, "double", "double a", "return a * 0.017453292519943295;");
        define(Intrinsic.MATH_TO_DEGREES, "double", "double a", "return a * 57.29577951308232;");
    }

    private MathRuntime() {
    }

    private static void define(Intrinsic intrinsic, String returnType, String parameters, String body) {
        SOURCES.put(intrinsic, "extern \"C\" JUNO_ASM_ABI " + returnType + " " + symbol(intrinsic)
                + "(" + parameters + ") { " + body + " }\n");
    }

    static boolean isMath(Intrinsic intrinsic) {
        return SOURCES.containsKey(intrinsic);
    }

    /** {@code MATH_FLOOR_DIV_INT} becomes {@code juno_math_floor_div_int}. */
    static String symbol(Intrinsic intrinsic) {
        return "juno_" + intrinsic.name().toLowerCase(Locale.ROOT);
    }

    /** The shared prelude plus every used function, in {@link Intrinsic} declaration order (deterministic). */
    static String helpers(Set<Intrinsic> used) {
        StringBuilder shim = new StringBuilder(PRELUDE);
        SOURCES.forEach((intrinsic, source) -> {
            if (used.contains(intrinsic)) {
                shim.append(source);
            }
        });
        return shim.toString();
    }
}

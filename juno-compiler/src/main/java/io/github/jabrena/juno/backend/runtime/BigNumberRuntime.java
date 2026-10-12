package io.github.jabrena.juno.backend.runtime;

/**
 * The runtime-shim side of the {@code java.math} subset (see {@code BigNumberMethods}): {@code BigInteger},
 * {@code BigDecimal} and {@code MathContext} as immutable arena blocks, with the JDK's rounding and text rules.
 * The code is exercised against the JDK classes by {@code BigNumberRuntimeTest} (a native build of this exact text)
 * and under QEMU by the {@code BigNumbers} programs.
 */
final class BigNumberRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();


    /** How a handle maps to an address. A native test build substitutes its own. */
    String handleHelpers() {
        return runtimeSource.load("common/big-number-handles.cpp");
    }

    /**
     * @param valueOfDouble whether {@code BigDecimal.valueOf(double)} is used; it formats the double with the
     *                      runtime-string helpers, which must then be present
     */
    String helpers(int arithmeticClassId, int numberFormatClassId, int illegalArgumentClassId,
                          boolean valueOfDouble) {
        return arithmetic(arithmeticClassId, numberFormatClassId, illegalArgumentClassId, 0)
                + integerDoubleValue()
                + arithmetic(arithmeticClassId, numberFormatClassId, illegalArgumentClassId, 1)
                + decimalDoubleValue()
                + (valueOfDouble ? decimalValueOfDouble() : "");
    }

    /**
     * Everything but the conversions from and to the C {@code double}, for an architecture whose C {@code double} is
     * not binary64 (AVR), which brings its own: {@code juno_big_integer_double_value},
     * {@code juno_big_decimal_double_value} and, when {@code valueOfDouble}, {@code juno_big_decimal_value_of_double}.
     */
    String helpersWithoutDoubles(int arithmeticClassId, int numberFormatClassId, int illegalArgumentClassId) {
        return arithmetic(arithmeticClassId, numberFormatClassId, illegalArgumentClassId, 0)
                + arithmetic(arithmeticClassId, numberFormatClassId, illegalArgumentClassId, 1);
    }

    /** {@code BigInteger.doubleValue} through the C library's correctly rounded {@code strtod}. */
    private String integerDoubleValue() {
        return """
                extern "C" JUNO_ASM_ABI double juno_big_integer_double_value(int32_t a) {
                  juno_big_prepare(4u * (juno_big_len_of(a) + 8u) * 4u);
                  uint32_t length = 0;
                  char* digits = juno_big_decimal(a, &length);
                  double magnitude = strtod(digits, nullptr);
                  return juno_big_sign_of(a) < 0 ? -magnitude : magnitude;
                }
                """;
    }

    /** {@code BigDecimal.doubleValue} through {@code strtod}. */
    private String decimalDoubleValue() {
        return """
                extern "C" JUNO_ASM_ABI double juno_big_decimal_double_value(int32_t a) {
                  int32_t text = juno_bd_text(a, false);
                  return strtod(juno_big_pointer_chars(text), nullptr);
                }
                """;
    }

    /** {@code BigDecimal.valueOf(double)}, through the runtime-string {@code juno_format_double}. */
    private String decimalValueOfDouble() {
        return """

                extern "C" JUNO_ASM_ABI int32_t juno_big_decimal_value_of_double(double value) {
                  if (value != value || value - value != 0.0) return juno_big_number_format("Infinite or NaN");
                  char text[40];
                  juno_format_double(text, value);
                  return juno_bd_parse(text);
                }
                """;
    }

    /** The arithmetic runtime in two parts ({@code part} 0 or 1), split where the double conversions sit. */
    private String arithmetic(int arithmeticClassId, int numberFormatClassId, int illegalArgumentClassId,
                                     int part) {
        String source = part == 0 ? runtimeSource.load("common/big-number-arithmetic-1.cpp") : runtimeSource.load("common/big-number-arithmetic-2.cpp");
        return source.replace("${JUNO_BIG_ARITHMETIC_CLASS_ID}", Integer.toString(arithmeticClassId))
                .replace("${JUNO_BIG_NUMBER_FORMAT_CLASS_ID}", Integer.toString(numberFormatClassId))
                .replace("${JUNO_BIG_ILLEGAL_ARGUMENT_CLASS_ID}", Integer.toString(illegalArgumentClassId));
    }
}

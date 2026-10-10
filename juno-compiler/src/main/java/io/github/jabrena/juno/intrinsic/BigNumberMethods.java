package io.github.jabrena.juno.intrinsic;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The {@code java.math} subset: {@code BigInteger}, {@code BigDecimal} and {@code MathContext}. Every method
 * lowers to one runtime-shim function named after its {@link Intrinsic} (see {@link #shimSymbol}); values are
 * immutable arena blocks addressed by one-word handles, and {@code RoundingMode} is its ordinal.
 */
public final class BigNumberMethods {
    static final Map<MethodRef, Intrinsic> METHODS = Map.ofEntries(
            Map.entry(new MethodRef("java/math/BigInteger", "<init>", "(Ljava/lang/String;)V"), Intrinsic.BIG_INTEGER_PARSE),
            Map.entry(new MethodRef("java/math/BigInteger", "valueOf", "(J)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_VALUE_OF),
            Map.entry(new MethodRef("java/math/BigInteger", "add", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_ADD),
            Map.entry(new MethodRef("java/math/BigInteger", "subtract", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_SUBTRACT),
            Map.entry(new MethodRef("java/math/BigInteger", "multiply", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_MULTIPLY),
            Map.entry(new MethodRef("java/math/BigInteger", "divide", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_DIVIDE),
            Map.entry(new MethodRef("java/math/BigInteger", "remainder", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_REMAINDER),
            Map.entry(new MethodRef("java/math/BigInteger", "mod", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_MOD),
            Map.entry(new MethodRef("java/math/BigInteger", "min", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_MIN),
            Map.entry(new MethodRef("java/math/BigInteger", "max", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_MAX),
            Map.entry(new MethodRef("java/math/BigInteger", "gcd", "(Ljava/math/BigInteger;)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_GCD),
            Map.entry(new MethodRef("java/math/BigInteger", "negate", "()Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_NEGATE),
            Map.entry(new MethodRef("java/math/BigInteger", "abs", "()Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_ABS),
            Map.entry(new MethodRef("java/math/BigInteger", "sqrt", "()Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_SQRT),
            Map.entry(new MethodRef("java/math/BigInteger", "signum", "()I"), Intrinsic.BIG_INTEGER_SIGNUM),
            Map.entry(new MethodRef("java/math/BigInteger", "bitLength", "()I"), Intrinsic.BIG_INTEGER_BIT_LENGTH),
            Map.entry(new MethodRef("java/math/BigInteger", "intValue", "()I"), Intrinsic.BIG_INTEGER_INT_VALUE),
            Map.entry(new MethodRef("java/math/BigInteger", "longValue", "()J"), Intrinsic.BIG_INTEGER_LONG_VALUE),
            Map.entry(new MethodRef("java/math/BigInteger", "doubleValue", "()D"), Intrinsic.BIG_INTEGER_DOUBLE_VALUE),
            Map.entry(new MethodRef("java/math/BigInteger", "compareTo", "(Ljava/math/BigInteger;)I"), Intrinsic.BIG_INTEGER_COMPARE_TO),
            Map.entry(new MethodRef("java/math/BigInteger", "equals", "(Ljava/lang/Object;)Z"), Intrinsic.BIG_INTEGER_EQUALS),
            Map.entry(new MethodRef("java/math/BigInteger", "pow", "(I)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_POW),
            Map.entry(new MethodRef("java/math/BigInteger", "shiftLeft", "(I)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_SHIFT_LEFT),
            Map.entry(new MethodRef("java/math/BigInteger", "shiftRight", "(I)Ljava/math/BigInteger;"), Intrinsic.BIG_INTEGER_SHIFT_RIGHT),
            Map.entry(new MethodRef("java/math/BigInteger", "toString", "()Ljava/lang/String;"), Intrinsic.BIG_INTEGER_TO_STRING),
            Map.entry(new MethodRef("java/math/MathContext", "<init>", "(I)V"), Intrinsic.BIG_MATH_CONTEXT_OF_PRECISION),
            Map.entry(new MethodRef("java/math/MathContext", "<init>", "(ILjava/math/RoundingMode;)V"), Intrinsic.BIG_MATH_CONTEXT_NEW),
            Map.entry(new MethodRef("java/math/MathContext", "getPrecision", "()I"), Intrinsic.BIG_MATH_CONTEXT_PRECISION),
            Map.entry(new MethodRef("java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V"), Intrinsic.BIG_DECIMAL_PARSE),
            Map.entry(new MethodRef("java/math/BigDecimal", "<init>", "(I)V"), Intrinsic.BIG_DECIMAL_OF_INT),
            Map.entry(new MethodRef("java/math/BigDecimal", "<init>", "(J)V"), Intrinsic.BIG_DECIMAL_VALUE_OF),
            Map.entry(new MethodRef("java/math/BigDecimal", "<init>", "(Ljava/math/BigInteger;)V"), Intrinsic.BIG_DECIMAL_OF_INTEGER),
            Map.entry(new MethodRef("java/math/BigDecimal", "<init>", "(Ljava/math/BigInteger;I)V"), Intrinsic.BIG_DECIMAL_OF_INTEGER_SCALED),
            Map.entry(new MethodRef("java/math/BigDecimal", "valueOf", "(J)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_VALUE_OF),
            Map.entry(new MethodRef("java/math/BigDecimal", "valueOf", "(JI)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_VALUE_OF_SCALED),
            Map.entry(new MethodRef("java/math/BigDecimal", "valueOf", "(D)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_VALUE_OF_DOUBLE),
            Map.entry(new MethodRef("java/math/BigDecimal", "add", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_ADD),
            Map.entry(new MethodRef("java/math/BigDecimal", "subtract", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_SUBTRACT),
            Map.entry(new MethodRef("java/math/BigDecimal", "multiply", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_MULTIPLY),
            Map.entry(new MethodRef("java/math/BigDecimal", "divide", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_DIVIDE),
            Map.entry(new MethodRef("java/math/BigDecimal", "min", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_MIN),
            Map.entry(new MethodRef("java/math/BigDecimal", "max", "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_MAX),
            Map.entry(new MethodRef("java/math/BigDecimal", "add", "(Ljava/math/BigDecimal;Ljava/math/MathContext;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_ADD_MC),
            Map.entry(new MethodRef("java/math/BigDecimal", "subtract", "(Ljava/math/BigDecimal;Ljava/math/MathContext;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_SUBTRACT_MC),
            Map.entry(new MethodRef("java/math/BigDecimal", "multiply", "(Ljava/math/BigDecimal;Ljava/math/MathContext;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_MULTIPLY_MC),
            Map.entry(new MethodRef("java/math/BigDecimal", "divide", "(Ljava/math/BigDecimal;Ljava/math/MathContext;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_DIVIDE_MC),
            Map.entry(new MethodRef("java/math/BigDecimal", "divide", "(Ljava/math/BigDecimal;ILjava/math/RoundingMode;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_DIVIDE_SCALE),
            Map.entry(new MethodRef("java/math/BigDecimal", "divide", "(Ljava/math/BigDecimal;Ljava/math/RoundingMode;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_DIVIDE_MODE),
            Map.entry(new MethodRef("java/math/BigDecimal", "sqrt", "(Ljava/math/MathContext;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_SQRT),
            Map.entry(new MethodRef("java/math/BigDecimal", "round", "(Ljava/math/MathContext;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_ROUND),
            Map.entry(new MethodRef("java/math/BigDecimal", "setScale", "(ILjava/math/RoundingMode;)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_SET_SCALE),
            Map.entry(new MethodRef("java/math/BigDecimal", "setScale", "(I)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_SET_SCALE_EXACT),
            Map.entry(new MethodRef("java/math/BigDecimal", "negate", "()Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_NEGATE),
            Map.entry(new MethodRef("java/math/BigDecimal", "abs", "()Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_ABS),
            Map.entry(new MethodRef("java/math/BigDecimal", "stripTrailingZeros", "()Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_STRIP_TRAILING_ZEROS),
            Map.entry(new MethodRef("java/math/BigDecimal", "movePointLeft", "(I)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_MOVE_POINT_LEFT),
            Map.entry(new MethodRef("java/math/BigDecimal", "movePointRight", "(I)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_MOVE_POINT_RIGHT),
            Map.entry(new MethodRef("java/math/BigDecimal", "pow", "(I)Ljava/math/BigDecimal;"), Intrinsic.BIG_DECIMAL_POW),
            Map.entry(new MethodRef("java/math/BigDecimal", "signum", "()I"), Intrinsic.BIG_DECIMAL_SIGNUM),
            Map.entry(new MethodRef("java/math/BigDecimal", "scale", "()I"), Intrinsic.BIG_DECIMAL_SCALE),
            Map.entry(new MethodRef("java/math/BigDecimal", "precision", "()I"), Intrinsic.BIG_DECIMAL_PRECISION),
            Map.entry(new MethodRef("java/math/BigDecimal", "unscaledValue", "()Ljava/math/BigInteger;"), Intrinsic.BIG_DECIMAL_UNSCALED_VALUE),
            Map.entry(new MethodRef("java/math/BigDecimal", "compareTo", "(Ljava/math/BigDecimal;)I"), Intrinsic.BIG_DECIMAL_COMPARE_TO),
            Map.entry(new MethodRef("java/math/BigDecimal", "equals", "(Ljava/lang/Object;)Z"), Intrinsic.BIG_DECIMAL_EQUALS),
            Map.entry(new MethodRef("java/math/BigDecimal", "toBigInteger", "()Ljava/math/BigInteger;"), Intrinsic.BIG_DECIMAL_TO_BIG_INTEGER),
            Map.entry(new MethodRef("java/math/BigDecimal", "intValue", "()I"), Intrinsic.BIG_DECIMAL_INT_VALUE),
            Map.entry(new MethodRef("java/math/BigDecimal", "longValue", "()J"), Intrinsic.BIG_DECIMAL_LONG_VALUE),
            Map.entry(new MethodRef("java/math/BigDecimal", "doubleValue", "()D"), Intrinsic.BIG_DECIMAL_DOUBLE_VALUE),
            Map.entry(new MethodRef("java/math/BigDecimal", "toString", "()Ljava/lang/String;"), Intrinsic.BIG_DECIMAL_TO_STRING),
            Map.entry(new MethodRef("java/math/BigDecimal", "toPlainString", "()Ljava/lang/String;"), Intrinsic.BIG_DECIMAL_TO_PLAIN_STRING));

    /** The intrinsics whose shim function can leave an {@code ArithmeticException} or similar pending. */
    private static final Set<Intrinsic> MAY_THROW = EnumSet.of(
            Intrinsic.BIG_INTEGER_PARSE,
            Intrinsic.BIG_INTEGER_DIVIDE,
            Intrinsic.BIG_INTEGER_REMAINDER,
            Intrinsic.BIG_INTEGER_MOD,
            Intrinsic.BIG_INTEGER_POW,
            Intrinsic.BIG_INTEGER_SQRT,
            Intrinsic.BIG_MATH_CONTEXT_OF_PRECISION,
            Intrinsic.BIG_MATH_CONTEXT_NEW,
            Intrinsic.BIG_DECIMAL_PARSE,
            Intrinsic.BIG_DECIMAL_VALUE_OF_DOUBLE,
            Intrinsic.BIG_DECIMAL_ADD_MC,
            Intrinsic.BIG_DECIMAL_SUBTRACT_MC,
            Intrinsic.BIG_DECIMAL_MULTIPLY_MC,
            Intrinsic.BIG_DECIMAL_DIVIDE,
            Intrinsic.BIG_DECIMAL_DIVIDE_MC,
            Intrinsic.BIG_DECIMAL_DIVIDE_SCALE,
            Intrinsic.BIG_DECIMAL_DIVIDE_MODE,
            Intrinsic.BIG_DECIMAL_SQRT,
            Intrinsic.BIG_DECIMAL_ROUND,
            Intrinsic.BIG_DECIMAL_SET_SCALE,
            Intrinsic.BIG_DECIMAL_SET_SCALE_EXACT,
            Intrinsic.BIG_DECIMAL_POW);

    private BigNumberMethods() {
    }

    public static boolean isBigNumber(Intrinsic intrinsic) {
        return intrinsic.name().startsWith("BIG_");
    }

    public static boolean mayThrow(Intrinsic intrinsic) {
        return MAY_THROW.contains(intrinsic);
    }

    /** The runtime-shim function that implements {@code intrinsic}. */
    public static String shimSymbol(Intrinsic intrinsic) {
        return "juno_" + intrinsic.name().toLowerCase(Locale.ROOT);
    }
}

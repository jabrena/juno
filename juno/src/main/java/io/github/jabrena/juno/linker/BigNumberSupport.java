package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Closed-world plumbing for the {@code java.math} subset: {@code BigInteger}, {@code BigDecimal} and
 * {@code MathContext} are one-word handles to immutable arena blocks, and {@code RoundingMode} is its ordinal.
 * The JDK classes are never linked; every supported method is an intrinsic (see {@code BigNumberMethods}) and
 * everything else is rejected here with a pointer at what is supported.
 */
public final class BigNumberSupport {
    public static final String BIG_INTEGER = "java/math/BigInteger";
    public static final String BIG_DECIMAL = "java/math/BigDecimal";
    public static final String MATH_CONTEXT = "java/math/MathContext";
    public static final String ROUNDING_MODE = "java/math/RoundingMode";

    private static final Set<String> OWNERS = Set.of(BIG_INTEGER, BIG_DECIMAL, MATH_CONTEXT, ROUNDING_MODE);

    /** A {@code static final} field Juno materializes itself: a constructed value or a plain ordinal. */
    public record Constant(Optional<Intrinsic> intrinsic, int value) {
    }

    private static final Map<FieldRef, Constant> CONSTANTS = constants();

    private BigNumberSupport() {
    }

    private static Map<FieldRef, Constant> constants() {
        Map<FieldRef, Constant> constants = new java.util.HashMap<>();
        String[] numbers = {"ZERO", "ONE", "TWO", "TEN"};
        int[] values = {0, 1, 2, 10};
        for (int index = 0; index < numbers.length; index++) {
            constants.put(new FieldRef(BIG_INTEGER, numbers[index], "L" + BIG_INTEGER + ";"),
                    new Constant(Optional.of(Intrinsic.BIG_INTEGER_CONSTANT), values[index]));
            constants.put(new FieldRef(BIG_DECIMAL, numbers[index], "L" + BIG_DECIMAL + ";"),
                    new Constant(Optional.of(Intrinsic.BIG_DECIMAL_CONSTANT), values[index]));
        }
        String[] contexts = {"UNLIMITED", "DECIMAL32", "DECIMAL64", "DECIMAL128"};
        for (int index = 0; index < contexts.length; index++) {
            constants.put(new FieldRef(MATH_CONTEXT, contexts[index], "L" + MATH_CONTEXT + ";"),
                    new Constant(Optional.of(Intrinsic.BIG_MATH_CONTEXT_CONSTANT), index));
        }
        String[] modes = {"UP", "DOWN", "CEILING", "FLOOR", "HALF_UP", "HALF_DOWN", "HALF_EVEN", "UNNECESSARY"};
        for (int index = 0; index < modes.length; index++) {
            constants.put(new FieldRef(ROUNDING_MODE, modes[index], "L" + ROUNDING_MODE + ";"),
                    new Constant(Optional.empty(), index));
        }
        return Map.copyOf(constants);
    }

    /** Whether {@code descriptor} is one of the four {@code java.math} types: one word, like a thread handle. */
    public static boolean isBigNumberType(String descriptor) {
        return descriptor.length() > 2 && descriptor.charAt(0) == 'L' && descriptor.endsWith(";")
                && OWNERS.contains(descriptor.substring(1, descriptor.length() - 1));
    }

    public static boolean isBigNumberOwner(String owner) {
        return OWNERS.contains(owner);
    }

    /** The constant a {@code getstatic} of {@code field} stands for, if it is one of the supported ones. */
    public static Optional<Constant> constant(FieldRef field) {
        return Optional.ofNullable(CONSTANTS.get(field));
    }

    /** Whether {@code called} is a supported {@code new BigInteger(...)}/{@code new BigDecimal(...)}/{@code new MathContext(...)}. */
    public static boolean isConstruction(MethodRef called) {
        return called.name().equals("<init>") && isBigNumberOwner(called.owner())
                && IntrinsicRegistry.isIntrinsic(called);
    }

    /** Rejects a {@code java.math} call outside the deliberately small subset with a useful diagnostic. */
    public static void validateCall(MethodRef called) {
        if (isBigNumberOwner(called.owner()) && !IntrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno's java.math subset supports BigInteger (valueOf, new BigInteger(String), "
                    + "add, subtract, multiply, divide, remainder, mod, pow, sqrt, gcd, negate, abs, min, max, "
                    + "shiftLeft, shiftRight, signum, bitLength, compareTo, equals, intValue, longValue, doubleValue, "
                    + "toString), BigDecimal (valueOf, new BigDecimal(String|int|long|BigInteger), add, subtract, "
                    + "multiply, divide with a scale, RoundingMode or MathContext, sqrt, round, setScale, pow, "
                    + "negate, abs, min, max, stripTrailingZeros, movePointLeft/Right, signum, scale, precision, "
                    + "unscaledValue, compareTo, equals, toBigInteger, intValue, longValue, doubleValue, toString, "
                    + "toPlainString) and MathContext (new MathContext(precision[, mode]), getPrecision, the "
                    + "constants); not supported: " + called.displayName());
        }
    }
}

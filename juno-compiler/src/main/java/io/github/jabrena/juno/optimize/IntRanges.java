package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.UnaryOp;

/**
 * Signed 32-bit ranges and the rules {@link BoundsCheckElimination} uses to propagate and narrow them. Endpoints are
 * longs so that their own arithmetic cannot overflow; a result that leaves the {@code int} range is the full range,
 * because the JVM operation itself would wrap.
 */
final class IntRanges {
    private IntRanges() {
    }

    record Range(long low, long high) {
        static final Range TOP = new Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

        static Range of(long low, long high) {
            return low < Integer.MIN_VALUE || high > Integer.MAX_VALUE ? TOP : new Range(low, high);
        }

        static Range constant(long value) {
            return new Range(value, value);
        }

        boolean isEmpty() {
            return low > high;
        }

        Range hull(Range other) {
            return new Range(Math.min(low, other.low), Math.max(high, other.high));
        }

        Range intersect(Range other) {
            return new Range(Math.max(low, other.low), Math.min(high, other.high));
        }

        boolean within(long from, long to) {
            return low >= from && high <= to;
        }
    }

    /** A pair of narrowed operand ranges; either is empty when the comparison can never hold. */
    record Narrowed(Range left, Range right) {
        boolean feasible() {
            return !left.isEmpty() && !right.isEmpty();
        }
    }

    static Range binary(BinaryOp operation, Range left, Range right) {
        return switch (operation) {
            case ADD -> Range.of(left.low() + right.low(), left.high() + right.high());
            case SUBTRACT -> Range.of(left.low() - right.high(), left.high() - right.low());
            case AND -> right.low() >= 0 ? new Range(0, right.high())
                    : left.low() >= 0 ? new Range(0, left.high()) : Range.TOP;
            case REMAINDER -> remainder(left, right);
            case UNSIGNED_SHIFT_RIGHT -> right.low() == right.high() && (right.low() & 31) > 0
                    ? new Range(0, 0xFFFFFFFFL >>> (right.low() & 31)) : Range.TOP;
            default -> Range.TOP;
        };
    }

    /** Java's remainder takes the dividend's sign and is smaller in magnitude than the divisor. */
    private static Range remainder(Range left, Range right) {
        if (right.low() <= 0) {
            return Range.TOP;
        }
        return left.low() >= 0 ? new Range(0, right.high() - 1) : new Range(-(right.high() - 1), right.high() - 1);
    }

    static Range unary(UnaryOp operation, Range operand) {
        return switch (operation) {
            case NEGATE -> Range.of(-operand.high(), -operand.low());
            case TO_BYTE -> new Range(Byte.MIN_VALUE, Byte.MAX_VALUE);
            case TO_CHAR -> new Range(Character.MIN_VALUE, Character.MAX_VALUE);
            case TO_SHORT -> new Range(Short.MIN_VALUE, Short.MAX_VALUE);
        };
    }

    static Range element(ArrayElementType type) {
        return switch (type) {
            case BYTE -> new Range(Byte.MIN_VALUE, Byte.MAX_VALUE);
            case CHAR -> new Range(Character.MIN_VALUE, Character.MAX_VALUE);
            case SHORT -> new Range(Short.MIN_VALUE, Short.MAX_VALUE);
            default -> Range.TOP;
        };
    }

    /** Both operands narrowed to where {@code left <condition> right} holds. */
    static Narrowed narrow(Condition condition, Range left, Range right) {
        return switch (condition) {
            case LESS_THAN -> new Narrowed(left.intersect(new Range(Integer.MIN_VALUE, right.high() - 1)),
                    right.intersect(new Range(left.low() + 1, Integer.MAX_VALUE)));
            case LESS_EQUAL -> new Narrowed(left.intersect(new Range(Integer.MIN_VALUE, right.high())),
                    right.intersect(new Range(left.low(), Integer.MAX_VALUE)));
            case GREATER_THAN -> new Narrowed(left.intersect(new Range(right.low() + 1, Integer.MAX_VALUE)),
                    right.intersect(new Range(Integer.MIN_VALUE, left.high() - 1)));
            case GREATER_EQUAL -> new Narrowed(left.intersect(new Range(right.low(), Integer.MAX_VALUE)),
                    right.intersect(new Range(Integer.MIN_VALUE, left.high())));
            case EQUAL -> new Narrowed(left.intersect(right), left.intersect(right));
            case NOT_EQUAL -> new Narrowed(excluding(left, right), excluding(right, left));
        };
    }

    /** {@code range} without {@code excluded}'s single value, where that value is one of its ends. */
    private static Range excluding(Range range, Range excluded) {
        if (excluded.low() != excluded.high()) {
            return range;
        }
        long point = excluded.low();
        if (range.low() == point) {
            return new Range(point + 1, range.high());
        }
        return range.high() == point ? new Range(range.low(), point - 1) : range;
    }

    static Condition inverse(Condition condition) {
        return switch (condition) {
            case EQUAL -> Condition.NOT_EQUAL;
            case NOT_EQUAL -> Condition.EQUAL;
            case LESS_THAN -> Condition.GREATER_EQUAL;
            case GREATER_EQUAL -> Condition.LESS_THAN;
            case GREATER_THAN -> Condition.LESS_EQUAL;
            case LESS_EQUAL -> Condition.GREATER_THAN;
        };
    }
}

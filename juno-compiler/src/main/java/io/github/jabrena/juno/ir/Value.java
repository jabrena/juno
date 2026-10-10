package io.github.jabrena.juno.ir;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** A typed symbolic virtual register: one value, produced by exactly one {@link IrInstruction}. */
public record Value(int id, JunoType type) {
    public Value {
        if (id < 0) {
            throw new IllegalArgumentException("Value id must be non-negative: " + id);
        }
        Objects.requireNonNull(type, "type");
    }

    public static Value int32(int id) {
        return new Value(id, JunoType.INT32);
    }

    public static Value float32(int id) {
        return new Value(id, JunoType.FLOAT32);
    }

    public static Value int64(int id) {
        return new Value(id, JunoType.INT64);
    }

    public static Value float64(int id) {
        return new Value(id, JunoType.FLOAT64);
    }

    public static List<Value> int32Values(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Value count must be non-negative: " + count);
        }
        return IntStream.range(0, count).mapToObj(Value::int32).toList();
    }
}

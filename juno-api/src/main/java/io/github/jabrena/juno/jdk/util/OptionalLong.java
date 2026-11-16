package io.github.jabrena.juno.jdk.util;

import java.util.NoSuchElementException;

/**
 * The board-side {@code java.util.OptionalLong}: the compiler reads this class as {@code java.util.OptionalLong}. It
 * covers {@code empty}, {@code of}, {@code isPresent}, {@code isEmpty}, {@code getAsLong}, {@code orElse} and
 * {@code orElseThrow}.
 */
public final class OptionalLong {
    private static final OptionalLong EMPTY = new OptionalLong(false, 0L);

    private final boolean present;
    private final long value;

    private OptionalLong(boolean present, long value) {
        this.present = present;
        this.value = value;
    }

    /** The one empty {@code OptionalLong}. */
    public static OptionalLong empty() {
        return EMPTY;
    }

    /** An {@code OptionalLong} holding {@code value}. */
    public static OptionalLong of(long value) {
        return new OptionalLong(true, value);
    }

    /** Whether a value is present. */
    public boolean isPresent() {
        return present;
    }

    /** Whether no value is present. */
    public boolean isEmpty() {
        return !present;
    }

    /**
     * The value.
     *
     * @throws NoSuchElementException if no value is present
     */
    public long getAsLong() {
        return orElseThrow();
    }

    /**
     * The value.
     *
     * @throws NoSuchElementException if no value is present
     */
    public long orElseThrow() {
        if (!present) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    /** The value if present, otherwise {@code other}. */
    public long orElse(long other) {
        return present ? value : other;
    }
}

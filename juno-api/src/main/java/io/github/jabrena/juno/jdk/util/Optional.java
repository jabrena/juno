package io.github.jabrena.juno.jdk.util;

import org.jspecify.annotations.Nullable;

import java.util.NoSuchElementException;

/**
 * The board-side {@code java.util.Optional}: the compiler reads this class as {@code java.util.Optional}, so a program
 * written against the JDK's {@code Optional} runs this code on the board. It covers the methods that need no lambda:
 * {@code empty}, {@code of}, {@code ofNullable}, {@code isPresent}, {@code isEmpty}, {@code get}, {@code orElse} and
 * {@code orElseThrow}.
 *
 * @param <T> the type of the value
 */
public final class Optional<T> {
    private static final Optional<?> EMPTY = new Optional<>(null);

    private final @Nullable T value;

    private Optional(@Nullable T value) {
        this.value = value;
    }

    /** The one empty {@code Optional}. */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> empty() {
        return (Optional<T>) EMPTY;
    }

    /**
     * An {@code Optional} holding {@code value}.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static <T> Optional<T> of(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return new Optional<>(value);
    }

    /** An {@code Optional} holding {@code value}, or the empty one when it is {@code null}. */
    public static <T> Optional<T> ofNullable(@Nullable T value) {
        return value == null ? empty() : new Optional<>(value);
    }

    /** Whether a value is present. */
    public boolean isPresent() {
        return value != null;
    }

    /** Whether no value is present. */
    public boolean isEmpty() {
        return value == null;
    }

    /**
     * The value.
     *
     * @throws NoSuchElementException if no value is present
     */
    public T get() {
        return orElseThrow();
    }

    /**
     * The value.
     *
     * @throws NoSuchElementException if no value is present
     */
    public T orElseThrow() {
        T present = value;
        if (present == null) {
            throw new NoSuchElementException("No value present");
        }
        return present;
    }

    /** The value if present, otherwise {@code other}. */
    public @Nullable T orElse(@Nullable T other) {
        return value != null ? value : other;
    }
}

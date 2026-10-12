package io.github.jabrena.juno.backend.runtime;

/**
 * Java's {@code float} and {@code double} in software, for an architecture whose C {@code double} is narrower than
 * IEEE binary64 (avr-gcc's is 32 bits). A {@code juno_f64} is the raw binary64 bit pattern and a binary32 value is
 * its bit pattern in a {@code uint32_t}; every operation rounds to nearest-even with gradual underflow, saturates on
 * Java's narrowing conversions, and treats NaN as the JVM does, so results are bit-identical to a JVM's. binary32
 * arithmetic runs in binary64 and rounds once more, which is exact for {@code +}, {@code -}, {@code *}, {@code /} and
 * {@code sqrt} (53 >= 2 * 24 + 2); {@code long}-to-{@code float} rounds straight from the integer.
 *
 * <p>Modeled on Berkeley SoftFloat 3's round-and-pack structure. Every helper is {@code static}, so the C++ compiler
 * drops whatever a program does not reach.
 */
public final class SoftFloatRuntime {
    private SoftFloatRuntime() {
    }

    static String helpers() {
        return RuntimeSource.load("avr/soft-float.cpp");
    }
}

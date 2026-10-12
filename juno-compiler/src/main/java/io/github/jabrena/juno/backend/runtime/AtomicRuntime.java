package io.github.jabrena.juno.backend.runtime;

/**
 * The {@code AtomicInteger}/{@code AtomicBoolean}/{@code AtomicLong} cells: a word (or a low/high word pair) in the
 * arena. These functions are runtime code, which the preemptive scheduler never interrupts part-way (it preempts
 * only generated code, or waits for a safepoint), so a plain read-modify-write is already atomic and needs no
 * monitor. An {@code AtomicBoolean}
 * is an {@code AtomicInteger} holding 0 or 1. A {@code long} travels as two {@code uint32_t} words, which keeps the
 * C signatures free of AAPCS 64-bit register alignment.
 */
final class AtomicRuntime {
    private AtomicRuntime() {
    }

    static String helpers() {
        return RuntimeSource.load("common/atomics.cpp");
    }
}

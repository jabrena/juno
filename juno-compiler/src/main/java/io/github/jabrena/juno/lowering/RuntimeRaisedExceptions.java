package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.intrinsic.RandomAccessFileMethods;
import io.github.jabrena.juno.linker.BigNumberSupport;
import io.github.jabrena.juno.linker.ScopedValueSupport;
import io.github.jabrena.juno.linker.StructuredTaskSupport;
import io.github.jabrena.juno.linker.UuidSupport;

import java.util.List;

/**
 * The built-in exceptions the runtime itself raises on behalf of a JDK class, never through a {@code new} in the
 * program: a program calling into that class needs their class ids even if it never names them.
 */
final class RuntimeRaisedExceptions {
    private final RandomAccessFileMethods randomAccessFileMethods = new RandomAccessFileMethods();

    private final ScopedValueSupport scopedValueSupport = new ScopedValueSupport();

    private final BigNumberSupport bigNumberSupport = new BigNumberSupport();

    private final UuidSupport uuidSupport = new UuidSupport();

    private final StructuredTaskSupport structuredTaskSupport = new StructuredTaskSupport();

    /** The exceptions the runtime may raise while running a method of {@code owner}. */
    List<String> raisedBy(String owner) {
        if (structuredTaskSupport.isStructuredTaskOwner(owner)) {
            return List.of("java/lang/IllegalStateException", "java/util/concurrent/ExecutionException");
        }
        if (scopedValueSupport.isScopedValueOwner(owner)) {
            return List.of("java/util/NoSuchElementException");
        }
        if (bigNumberSupport.isBigNumberOwner(owner)) {
            return List.of(ExceptionRouting.ARITHMETIC_EXCEPTION, "java/lang/NumberFormatException",
                    "java/lang/IllegalArgumentException");
        }
        if (uuidSupport.isUuidOwner(owner)) {
            return List.of("java/lang/IllegalArgumentException");
        }
        if (randomAccessFileMethods.isOwner(owner)) {
            return RandomAccessFileMethods.EXCEPTIONS;
        }
        return List.of();
    }
}

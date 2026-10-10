package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.intrinsic.RandomAccessFileMethods;
import io.github.jabrena.juno.linker.BigNumberSupport;
import io.github.jabrena.juno.linker.ScopedValueSupport;
import io.github.jabrena.juno.linker.StructuredTaskSupport;

import java.util.List;

/**
 * The built-in exceptions the runtime itself raises on behalf of a JDK class, never through a {@code new} in the
 * program: a program calling into that class needs their class ids even if it never names them.
 */
final class RuntimeRaisedExceptions {
    private RuntimeRaisedExceptions() {
    }

    static List<String> of(String owner) {
        if (StructuredTaskSupport.isStructuredTaskOwner(owner)) {
            return List.of("java/lang/IllegalStateException", "java/util/concurrent/ExecutionException");
        }
        if (ScopedValueSupport.isScopedValueOwner(owner)) {
            return List.of("java/util/NoSuchElementException");
        }
        if (BigNumberSupport.isBigNumberOwner(owner)) {
            return List.of(ControlFlowLowering.ARITHMETIC_EXCEPTION, "java/lang/NumberFormatException",
                    "java/lang/IllegalArgumentException");
        }
        if (RandomAccessFileMethods.isOwner(owner)) {
            return RandomAccessFileMethods.EXCEPTIONS;
        }
        return List.of();
    }
}

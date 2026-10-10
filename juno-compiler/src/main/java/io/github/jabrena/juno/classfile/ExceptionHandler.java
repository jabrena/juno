package io.github.jabrena.juno.classfile;

import org.jspecify.annotations.Nullable;

/**
 * One {@code Code} attribute exception-table entry: instructions in {@code [startPc, endPc)} that throw
 * a {@code catchType} (or any throwable when {@code catchType} is {@code null}, as {@code finally} uses)
 * continue at {@code handlerPc}. Entries are kept in class-file order, which is the JVM's matching order.
 */
public record ExceptionHandler(int startPc, int endPc, int handlerPc, @Nullable String catchType) {
    public boolean covers(int offset) {
        return offset >= startPc && offset < endPc;
    }

    public boolean catchesAny() {
        return catchType == null;
    }
}

package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;
import java.util.Optional;

/**
 * {@code throwableClasses} lists every exception class the program allocates; a class's index is the
 * id stored in each of its objects' headers (see {@code ThrowableTypes}).
 */
public record IrProgram(MethodRef entryPoint, List<IrMethod> methods, Optional<Integer> watchdogTimeoutMillis,
                        List<String> throwableClasses) {
    public IrProgram {
        methods = List.copyOf(methods);
        throwableClasses = List.copyOf(throwableClasses);
    }

    public IrProgram(MethodRef entryPoint, List<IrMethod> methods, Optional<Integer> watchdogTimeoutMillis) {
        this(entryPoint, methods, watchdogTimeoutMillis, List.of());
    }

    public IrProgram(MethodRef entryPoint, List<IrMethod> methods) {
        this(entryPoint, methods, Optional.empty());
    }

    /** This program with its methods replaced, as an optimization pass produces it. */
    public IrProgram withMethods(List<IrMethod> replacement) {
        return new IrProgram(entryPoint, replacement, watchdogTimeoutMillis, throwableClasses);
    }
}

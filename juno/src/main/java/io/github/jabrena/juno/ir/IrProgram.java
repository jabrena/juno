package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;
import java.util.Optional;

public record IrProgram(MethodRef entryPoint, List<IrMethod> methods, Optional<Integer> watchdogTimeoutMillis) {
    public IrProgram(MethodRef entryPoint, List<IrMethod> methods) {
        this(entryPoint, methods, Optional.empty());
    }
}

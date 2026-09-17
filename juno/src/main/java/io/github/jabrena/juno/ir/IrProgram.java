package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;

public record IrProgram(MethodRef entryPoint, List<IrMethod> methods) {
}

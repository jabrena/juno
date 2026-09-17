package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;

public record IrMethod(MethodRef reference, int maxLocals, int valueCount, List<IrBasicBlock> blocks) {
}

package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;

import java.util.List;

public record LinkedMethod(JavaClass owner, JavaMethod method, List<Instruction> instructions) {
}

package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.MethodRef;

/** A polymorphic bytecode call site, uniquely identified within its caller. */
public record InterfaceCallSite(MethodRef caller, int bytecodeOffset) {
}

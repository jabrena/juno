package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.MethodRef;

/** A bytecode {@code invokeinterface} site, uniquely identified within its caller. */
public record InterfaceCallSite(MethodRef caller, int bytecodeOffset) {
}

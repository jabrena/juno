package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.MethodRef;

/** Identity of one {@code invokedynamic} lambda factory in a reachable method. */
public record LambdaCallSite(MethodRef caller, int bytecodeOffset) {
}

package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;

/** One object type-id branch and its concrete method in a closed-world interface dispatch. */
public record InterfaceTarget(int typeId, MethodRef method) {
}

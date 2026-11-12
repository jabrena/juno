package io.github.jabrena.juno.classfile;

/** A resolved {@code CONSTANT_MethodHandle} whose target is an instance field. */
public record FieldHandleRef(int referenceKind, FieldRef field) {
    public static final int REF_GET_FIELD = 1;
}

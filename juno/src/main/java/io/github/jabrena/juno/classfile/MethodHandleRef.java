package io.github.jabrena.juno.classfile;

/** A resolved CONSTANT_MethodHandle whose target is a method or constructor. */
public record MethodHandleRef(int referenceKind, MethodRef method) {
    public static final int REF_INVOKE_VIRTUAL = 5;
    public static final int REF_INVOKE_STATIC = 6;
    public static final int REF_INVOKE_SPECIAL = 7;
    public static final int REF_NEW_INVOKE_SPECIAL = 8;
    public static final int REF_INVOKE_INTERFACE = 9;

    public boolean isSupportedInvocation() {
        return referenceKind >= REF_INVOKE_VIRTUAL && referenceKind <= REF_INVOKE_INTERFACE;
    }
}

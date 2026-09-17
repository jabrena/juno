package io.github.jabrena.juno.classfile;

public record JavaMethod(
        String owner,
        int accessFlags,
        String name,
        String descriptor,
        int maxStack,
        int maxLocals,
        byte[] code) {

    public static final int ACC_STATIC = 0x0008;
    public static final int ACC_NATIVE = 0x0100;

    public boolean isStatic() {
        return (accessFlags & ACC_STATIC) != 0;
    }

    public boolean isNative() {
        return (accessFlags & ACC_NATIVE) != 0;
    }

    public MethodRef reference() {
        return new MethodRef(owner, name, descriptor);
    }
}

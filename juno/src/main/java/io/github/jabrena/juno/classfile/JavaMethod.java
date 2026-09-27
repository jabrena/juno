package io.github.jabrena.juno.classfile;

import java.util.List;

public record JavaMethod(
        String owner,
        int accessFlags,
        String name,
        String descriptor,
        int maxStack,
        int maxLocals,
        byte[] code,
        List<ExceptionHandler> exceptionHandlers) {

    public JavaMethod {
        exceptionHandlers = List.copyOf(exceptionHandlers);
    }

    public JavaMethod(String owner, int accessFlags, String name, String descriptor, int maxStack, int maxLocals,
                      byte[] code) {
        this(owner, accessFlags, name, descriptor, maxStack, maxLocals, code, List.of());
    }

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

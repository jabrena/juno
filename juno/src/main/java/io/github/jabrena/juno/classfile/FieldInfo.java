package io.github.jabrena.juno.classfile;

public record FieldInfo(String name, String descriptor, int accessFlags) {
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_ENUM = 0x4000;

    public boolean isStatic() {
        return (accessFlags & ACC_STATIC) != 0;
    }

    public boolean isEnumConstant() {
        return (accessFlags & ACC_ENUM) != 0;
    }
}

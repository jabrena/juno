package io.github.jabrena.juno.classfile;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("ArrayRecordComponent")
public record JavaMethod(
        String owner,
        int accessFlags,
        String name,
        String descriptor,
        int maxStack,
        int maxLocals,
        byte @Nullable [] code,
        List<ExceptionHandler> exceptionHandlers) {

    public JavaMethod {
        exceptionHandlers = List.copyOf(exceptionHandlers);
    }

    public JavaMethod(String owner, int accessFlags, String name, String descriptor, int maxStack, int maxLocals,
                      byte @Nullable [] code) {
        this(owner, accessFlags, name, descriptor, maxStack, maxLocals, code, List.of());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JavaMethod that)) {
            return false;
        }
        return accessFlags == that.accessFlags
                && maxStack == that.maxStack
                && maxLocals == that.maxLocals
                && owner.equals(that.owner)
                && name.equals(that.name)
                && descriptor.equals(that.descriptor)
                && Arrays.equals(code, that.code)
                && exceptionHandlers.equals(that.exceptionHandlers);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(owner, accessFlags, name, descriptor, maxStack, maxLocals, exceptionHandlers);
        return 31 * result + Arrays.hashCode(code);
    }

    @Override
    public String toString() {
        return "JavaMethod[owner=" + owner + ", accessFlags=" + accessFlags + ", name=" + name
                + ", descriptor=" + descriptor + ", maxStack=" + maxStack + ", maxLocals=" + maxLocals
                + ", code=" + Arrays.toString(code) + ", exceptionHandlers=" + exceptionHandlers + "]";
    }

    public static final int ACC_STATIC = 0x0008;
    public static final int ACC_SYNCHRONIZED = 0x0020;
    public static final int ACC_NATIVE = 0x0100;
    public static final int ACC_ABSTRACT = 0x0400;

    public boolean isStatic() {
        return (accessFlags & ACC_STATIC) != 0;
    }

    public boolean isSynchronized() {
        return (accessFlags & ACC_SYNCHRONIZED) != 0;
    }

    public boolean isNative() {
        return (accessFlags & ACC_NATIVE) != 0;
    }

    public boolean isAbstract() {
        return (accessFlags & ACC_ABSTRACT) != 0;
    }

    public MethodRef reference() {
        return new MethodRef(owner, name, descriptor);
    }
}

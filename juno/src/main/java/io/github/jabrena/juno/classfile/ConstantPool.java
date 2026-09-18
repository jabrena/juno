package io.github.jabrena.juno.classfile;

import io.github.jabrena.juno.CompileException;

public final class ConstantPool {
    public record ClassEntry(int nameIndex) {
    }

    public record NameAndTypeEntry(int nameIndex, int descriptorIndex) {
    }

    public record RefEntry(int classIndex, int nameAndTypeIndex) {
    }

    private final Object[] entries;

    ConstantPool(Object[] entries) {
        this.entries = entries;
    }

    public String utf8(int index) {
        Object value = entry(index);
        if (value instanceof String string) {
            return string;
        }
        throw new CompileException("Constant pool entry " + index + " is not UTF-8 text");
    }

    public String className(int index) {
        Object value = entry(index);
        if (value instanceof ClassEntry classEntry) {
            return utf8(classEntry.nameIndex());
        }
        throw new CompileException("Constant pool entry " + index + " is not a class");
    }

    public MethodRef methodRef(int index) {
        Object value = entry(index);
        if (!(value instanceof RefEntry ref)) {
            throw new CompileException("Constant pool entry " + index + " is not a method reference");
        }
        Object nameAndTypeValue = entry(ref.nameAndTypeIndex());
        if (!(nameAndTypeValue instanceof NameAndTypeEntry nameAndType)) {
            throw new CompileException("Malformed method reference at constant pool entry " + index);
        }
        return new MethodRef(
                className(ref.classIndex()),
                utf8(nameAndType.nameIndex()),
                utf8(nameAndType.descriptorIndex()));
    }

    public FieldRef fieldRef(int index) {
        Object value = entry(index);
        if (!(value instanceof RefEntry ref)) {
            throw new CompileException("Constant pool entry " + index + " is not a field reference");
        }
        Object nameAndTypeValue = entry(ref.nameAndTypeIndex());
        if (!(nameAndTypeValue instanceof NameAndTypeEntry nameAndType)) {
            throw new CompileException("Malformed field reference at constant pool entry " + index);
        }
        return new FieldRef(
                className(ref.classIndex()),
                utf8(nameAndType.nameIndex()),
                utf8(nameAndType.descriptorIndex()));
    }

    public int integer(int index) {
        Object value = entry(index);
        if (value instanceof Integer integer) {
            return integer;
        }
        throw new CompileException("Only integer constants are supported by ldc (constant pool entry " + index + ")");
    }

    public long longValue(int index) {
        Object value = entry(index);
        if (value instanceof Long longValue) {
            return longValue;
        }
        throw new CompileException("Only long constants are supported by ldc2_w (constant pool entry " + index + ")");
    }

    private Object entry(int index) {
        if (index <= 0 || index >= entries.length || entries[index] == null) {
            throw new CompileException("Invalid constant pool index " + index);
        }
        return entries[index];
    }
}

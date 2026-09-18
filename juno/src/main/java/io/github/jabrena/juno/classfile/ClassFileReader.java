package io.github.jabrena.juno.classfile;

import io.github.jabrena.juno.CompileException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ClassFileReader {
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    public JavaClass read(byte[] bytes) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != CLASS_FILE_MAGIC) {
                throw new CompileException("Input is not a JVM class file");
            }
            input.readUnsignedShort(); // minor version
            int majorVersion = input.readUnsignedShort();
            if (majorVersion < 52) {
                throw new CompileException("Juno requires class files targeting Java 8 or newer");
            }

            ConstantPool pool = readConstantPool(input);
            int classAccessFlags = input.readUnsignedShort();
            String className = pool.className(input.readUnsignedShort());
            input.readUnsignedShort(); // super class
            skipInterfaces(input);
            List<String> enumConstantNames = readFields(input, pool);
            List<JavaMethod> methods = readMethods(input, pool, className);
            skipAttributes(input, pool);
            return new JavaClass(className, classAccessFlags, pool, List.copyOf(methods), enumConstantNames);
        } catch (IOException exception) {
            throw new CompileException("Cannot read class file", exception);
        }
    }

    private ConstantPool readConstantPool(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        Object[] entries = new Object[count];
        for (int index = 1; index < count; index++) {
            int tag = input.readUnsignedByte();
            entries[index] = switch (tag) {
                case 1 -> input.readUTF();
                case 3 -> input.readInt();
                case 4 -> {
                    input.readInt();
                    yield new Object();
                }
                case 5 -> {
                    long value = input.readLong();
                    index++; // long/double constants occupy two consecutive pool entries; the second is unusable
                    yield value;
                }
                case 6 -> {
                    input.readLong(); // double: not supported, but still consumed to keep later indices aligned
                    index++;
                    yield new Object();
                }
                case 7 -> new ConstantPool.ClassEntry(input.readUnsignedShort());
                case 8, 16, 19, 20 -> {
                    input.readUnsignedShort();
                    yield new Object();
                }
                case 9, 10, 11 -> new ConstantPool.RefEntry(input.readUnsignedShort(), input.readUnsignedShort());
                case 12 -> new ConstantPool.NameAndTypeEntry(input.readUnsignedShort(), input.readUnsignedShort());
                case 15 -> {
                    input.readUnsignedByte();
                    input.readUnsignedShort();
                    yield new Object();
                }
                case 17, 18 -> {
                    input.readUnsignedShort();
                    input.readUnsignedShort();
                    yield new Object();
                }
                default -> throw new CompileException("Unsupported constant-pool tag " + tag);
            };
        }
        return new ConstantPool(entries);
    }

    private void skipInterfaces(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        input.skipNBytes((long) count * 2);
    }

    /** Returns the names of fields flagged {@code ACC_ENUM}, in declaration order (see {@link JavaClass}). */
    private List<String> readFields(DataInputStream input, ConstantPool pool) throws IOException {
        final int accEnum = 0x4000;
        int count = input.readUnsignedShort();
        List<String> enumConstantNames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int accessFlags = input.readUnsignedShort();
            String name = pool.utf8(input.readUnsignedShort());
            input.readUnsignedShort(); // descriptor
            skipAttributes(input, pool);
            if ((accessFlags & accEnum) != 0) {
                enumConstantNames.add(name);
            }
        }
        return List.copyOf(enumConstantNames);
    }

    private List<JavaMethod> readMethods(DataInputStream input, ConstantPool pool, String owner) throws IOException {
        int count = input.readUnsignedShort();
        List<JavaMethod> methods = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int accessFlags = input.readUnsignedShort();
            String name = pool.utf8(input.readUnsignedShort());
            String descriptor = pool.utf8(input.readUnsignedShort());
            int attributeCount = input.readUnsignedShort();
            int maxStack = 0;
            int maxLocals = 0;
            byte[] code = null;
            for (int j = 0; j < attributeCount; j++) {
                String attributeName = pool.utf8(input.readUnsignedShort());
                int length = input.readInt();
                if (attributeName.equals("Code")) {
                    maxStack = input.readUnsignedShort();
                    maxLocals = input.readUnsignedShort();
                    code = input.readNBytes(input.readInt());
                    int exceptionTableLength = input.readUnsignedShort();
                    input.skipNBytes((long) exceptionTableLength * 8);
                    skipAttributes(input, pool);
                } else {
                    input.skipNBytes(Integer.toUnsignedLong(length));
                }
            }
            methods.add(new JavaMethod(owner, accessFlags, name, descriptor, maxStack, maxLocals, code));
        }
        return methods;
    }

    private void skipAttributes(DataInputStream input, ConstantPool pool) throws IOException {
        int count = input.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            pool.utf8(input.readUnsignedShort());
            input.skipNBytes(Integer.toUnsignedLong(input.readInt()));
        }
    }
}

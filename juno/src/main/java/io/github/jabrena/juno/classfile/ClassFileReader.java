package io.github.jabrena.juno.classfile;

import io.github.jabrena.juno.CompileException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ClassFileReader {
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
    private static final String BOARD_ANNOTATION_DESCRIPTOR = "Lio/github/jabrena/juno/annotations/Board;";

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
            int superClassIndex = input.readUnsignedShort();
            String superClassName = superClassIndex == 0 ? null : pool.className(superClassIndex);
            skipInterfaces(input);
            List<FieldInfo> fields = readFields(input, pool);
            List<JavaMethod> methods = readMethods(input, pool, className);
            Optional<String> boardApiClassName = readClassAttributes(input, pool);
            return new JavaClass(className, classAccessFlags, superClassName, pool, List.copyOf(methods), fields,
                    boardApiClassName);
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
                case 4 -> Float.intBitsToFloat(input.readInt());
                case 5 -> {
                    long value = input.readLong();
                    index++; // long/double constants occupy two consecutive pool entries; the second is unusable
                    yield value;
                }
                case 6 -> {
                    double value = Double.longBitsToDouble(input.readLong());
                    index++;
                    yield value;
                }
                case 7 -> new ConstantPool.ClassEntry(input.readUnsignedShort());
                case 8 -> new ConstantPool.StringEntry(input.readUnsignedShort());
                case 16, 19, 20 -> {
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

    /** Returns every declared field, in class-file declaration order (see {@link JavaClass}). */
    private List<FieldInfo> readFields(DataInputStream input, ConstantPool pool) throws IOException {
        int count = input.readUnsignedShort();
        List<FieldInfo> fields = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int accessFlags = input.readUnsignedShort();
            String name = pool.utf8(input.readUnsignedShort());
            String descriptor = pool.utf8(input.readUnsignedShort());
            skipAttributes(input, pool);
            fields.add(new FieldInfo(name, descriptor, accessFlags));
        }
        return List.copyOf(fields);
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

    /** Reads the class's own attribute table, extracting {@code @Board}'s value if present. */
    private Optional<String> readClassAttributes(DataInputStream input, ConstantPool pool) throws IOException {
        int count = input.readUnsignedShort();
        String boardApiClassName = null;
        for (int i = 0; i < count; i++) {
            String attributeName = pool.utf8(input.readUnsignedShort());
            int length = input.readInt();
            if (attributeName.equals("RuntimeVisibleAnnotations")) {
                String found = readAnnotations(input, pool);
                if (found != null) {
                    boardApiClassName = found;
                }
            } else {
                input.skipNBytes(Integer.toUnsignedLong(length));
            }
        }
        return Optional.ofNullable(boardApiClassName);
    }

    /** Reads a {@code RuntimeVisibleAnnotations}/{@code RuntimeInvisibleAnnotations} body. */
    private String readAnnotations(DataInputStream input, ConstantPool pool) throws IOException {
        int numAnnotations = input.readUnsignedShort();
        String boardApiClassName = null;
        for (int i = 0; i < numAnnotations; i++) {
            String found = readAnnotation(input, pool);
            if (found != null) {
                boardApiClassName = found;
            }
        }
        return boardApiClassName;
    }

    /** Reads one {@code annotation} structure; returns {@code @Board}'s class value, or {@code null} otherwise. */
    private String readAnnotation(DataInputStream input, ConstantPool pool) throws IOException {
        String typeDescriptor = pool.utf8(input.readUnsignedShort());
        boolean isBoard = typeDescriptor.equals(BOARD_ANNOTATION_DESCRIPTOR);
        int numPairs = input.readUnsignedShort();
        String boardApiClassName = null;
        for (int i = 0; i < numPairs; i++) {
            input.readUnsignedShort(); // element_name_index
            String classValue = readElementValue(input, pool);
            if (isBoard && classValue != null) {
                boardApiClassName = classValue;
            }
        }
        return boardApiClassName;
    }

    /** Reads one {@code element_value}; returns the referenced class's internal name for a class-typed (tag 'c') value. */
    private String readElementValue(DataInputStream input, ConstantPool pool) throws IOException {
        int tag = input.readUnsignedByte();
        return switch (tag) {
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's' -> {
                input.readUnsignedShort();
                yield null;
            }
            case 'e' -> {
                input.readUnsignedShort();
                input.readUnsignedShort();
                yield null;
            }
            case 'c' -> {
                String descriptor = pool.utf8(input.readUnsignedShort());
                yield descriptor.startsWith("L") && descriptor.endsWith(";")
                        ? descriptor.substring(1, descriptor.length() - 1)
                        : descriptor;
            }
            case '@' -> {
                readAnnotation(input, pool);
                yield null;
            }
            case '[' -> {
                int numValues = input.readUnsignedShort();
                for (int i = 0; i < numValues; i++) {
                    readElementValue(input, pool);
                }
                yield null;
            }
            default -> throw new CompileException("Unsupported annotation element_value tag " + tag);
        };
    }
}

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
    private static final String WATCHDOG_ANNOTATION_DESCRIPTOR = "Lio/github/jabrena/juno/annotations/Watchdog;";
    private static final int WATCHDOG_DEFAULT_TIMEOUT_MILLIS = 5000;

    /** One class's {@code @Board}/{@code @Watchdog} annotation values, as read from its class file. */
    private record ClassAnnotations(Optional<String> boardApiClassName, Optional<Integer> watchdogTimeoutMillis) {
        private static final ClassAnnotations NONE = new ClassAnnotations(Optional.empty(), Optional.empty());
    }

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
            ClassAnnotations annotations = readClassAttributes(input, pool);
            return new JavaClass(className, classAccessFlags, superClassName, pool, List.copyOf(methods), fields,
                    annotations.boardApiClassName(), annotations.watchdogTimeoutMillis());
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

    /** Reads the class's own attribute table, extracting {@code @Board}/{@code @Watchdog}'s values if present. */
    private ClassAnnotations readClassAttributes(DataInputStream input, ConstantPool pool) throws IOException {
        int count = input.readUnsignedShort();
        ClassAnnotations annotations = ClassAnnotations.NONE;
        for (int i = 0; i < count; i++) {
            String attributeName = pool.utf8(input.readUnsignedShort());
            int length = input.readInt();
            if (attributeName.equals("RuntimeVisibleAnnotations")) {
                annotations = readAnnotations(input, pool, annotations);
            } else {
                input.skipNBytes(Integer.toUnsignedLong(length));
            }
        }
        return annotations;
    }

    /** Reads a {@code RuntimeVisibleAnnotations}/{@code RuntimeInvisibleAnnotations} body. */
    private ClassAnnotations readAnnotations(DataInputStream input, ConstantPool pool, ClassAnnotations annotations)
            throws IOException {
        int numAnnotations = input.readUnsignedShort();
        for (int i = 0; i < numAnnotations; i++) {
            annotations = readAnnotation(input, pool, annotations);
        }
        return annotations;
    }

    /** Reads one {@code annotation} structure, folding {@code @Board}/{@code @Watchdog}'s values into {@code annotations}. */
    private ClassAnnotations readAnnotation(DataInputStream input, ConstantPool pool, ClassAnnotations annotations)
            throws IOException {
        String typeDescriptor = pool.utf8(input.readUnsignedShort());
        boolean isBoard = typeDescriptor.equals(BOARD_ANNOTATION_DESCRIPTOR);
        boolean isWatchdog = typeDescriptor.equals(WATCHDOG_ANNOTATION_DESCRIPTOR);
        int numPairs = input.readUnsignedShort();
        Optional<String> boardApiClassName = annotations.boardApiClassName();
        Optional<Integer> watchdogTimeoutMillis = annotations.watchdogTimeoutMillis();
        if (isWatchdog) {
            // @Watchdog carries a default (see Watchdog#timeoutMillis) the class file omits entirely
            // when the source never overrides it — apply that same default here so the annotation's
            // mere presence is enough, exactly like a real annotation processor would resolve it.
            watchdogTimeoutMillis = Optional.of(WATCHDOG_DEFAULT_TIMEOUT_MILLIS);
        }
        for (int i = 0; i < numPairs; i++) {
            String elementName = pool.utf8(input.readUnsignedShort());
            ElementValue value = readElementValue(input, pool);
            if (isBoard && value.classInternalName() != null) {
                boardApiClassName = Optional.of(value.classInternalName());
            }
            if (isWatchdog && elementName.equals("timeoutMillis") && value.intValue() != null) {
                watchdogTimeoutMillis = Optional.of(value.intValue());
            }
        }
        return new ClassAnnotations(boardApiClassName, watchdogTimeoutMillis);
    }

    /** One {@code element_value}'s parsed payload — only the fields a supported tag can populate are non-null. */
    private record ElementValue(String classInternalName, Integer intValue) {
        private static final ElementValue EMPTY = new ElementValue(null, null);
    }

    /** Reads one {@code element_value}. */
    private ElementValue readElementValue(DataInputStream input, ConstantPool pool) throws IOException {
        int tag = input.readUnsignedByte();
        return switch (tag) {
            case 'I' -> new ElementValue(null, pool.integer(input.readUnsignedShort()));
            case 'B', 'C', 'D', 'F', 'J', 'S', 'Z', 's' -> {
                input.readUnsignedShort();
                yield ElementValue.EMPTY;
            }
            case 'e' -> {
                input.readUnsignedShort();
                input.readUnsignedShort();
                yield ElementValue.EMPTY;
            }
            case 'c' -> {
                String descriptor = pool.utf8(input.readUnsignedShort());
                String internalName = descriptor.startsWith("L") && descriptor.endsWith(";")
                        ? descriptor.substring(1, descriptor.length() - 1)
                        : descriptor;
                yield new ElementValue(internalName, null);
            }
            case '@' -> {
                readAnnotation(input, pool, ClassAnnotations.NONE);
                yield ElementValue.EMPTY;
            }
            case '[' -> {
                int numValues = input.readUnsignedShort();
                for (int i = 0; i < numValues; i++) {
                    readElementValue(input, pool);
                }
                yield ElementValue.EMPTY;
            }
            default -> throw new CompileException("Unsupported annotation element_value tag " + tag);
        };
    }
}

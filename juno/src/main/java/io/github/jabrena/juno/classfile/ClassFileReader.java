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
    /**
     * Payload sizes, in bytes and indexed by tag, of the constant-pool entries Juno still treats as
     * opaque ({@code Dynamic}, {@code Module}, {@code Package}); 0 marks every other tag.
     */
    private static final int[] SKIPPED_CONSTANT_SIZES = new int[256];
    /** {@code element_value} tags whose payload is one ignored {@code const_value_index}. */
    private static final String SKIPPED_CONST_ELEMENT_TAGS = "BCDFJSZs";

    static {
        SKIPPED_CONSTANT_SIZES[17] = 4;
        SKIPPED_CONSTANT_SIZES[19] = 2;
        SKIPPED_CONSTANT_SIZES[20] = 2;
    }

    /** One class's {@code @Board}/{@code @Watchdog} annotation values. */
    private record ClassAnnotations(List<String> boardApiClassNames, Optional<Integer> watchdogTimeoutMillis) {
        private static final ClassAnnotations NONE = new ClassAnnotations(List.of(), Optional.empty());
    }

    /** One class's selected attributes, as read from its class file. */
    private record ClassAttributes(List<String> boardApiClassNames, Optional<Integer> watchdogTimeoutMillis,
                                   List<BootstrapMethod> bootstrapMethods) {
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
            List<String> interfaces = ClassHeaderReader.readInterfaces(input, pool);
            List<FieldInfo> fields = readFields(input, pool);
            List<JavaMethod> methods = readMethods(input, pool, className);
            ClassAttributes attributes = readClassAttributes(input, pool);
            return new JavaClass(className, classAccessFlags, superClassName, interfaces, pool,
                    List.copyOf(methods), fields, attributes.boardApiClassNames(),
                    attributes.watchdogTimeoutMillis(), attributes.bootstrapMethods());
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
                case 9, 10, 11 -> new ConstantPool.RefEntry(input.readUnsignedShort(), input.readUnsignedShort());
                case 12 -> new ConstantPool.NameAndTypeEntry(input.readUnsignedShort(), input.readUnsignedShort());
                case 15 -> new ConstantPool.MethodHandleEntry(input.readUnsignedByte(), input.readUnsignedShort());
                case 16 -> new ConstantPool.MethodTypeEntry(input.readUnsignedShort());
                case 18 -> new ConstantPool.InvokeDynamicEntry(input.readUnsignedShort(), input.readUnsignedShort());
                default -> {
                    int size = SKIPPED_CONSTANT_SIZES[tag];
                    if (size == 0) {
                        throw new CompileException("Unsupported constant-pool tag " + tag);
                    }
                    input.skipNBytes(size);
                    yield new Object();
                }
            };
        }
        return new ConstantPool(entries);
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
            CodeAttribute codeAttribute = CodeAttribute.ABSENT;
            for (int j = 0; j < attributeCount; j++) {
                String attributeName = pool.utf8(input.readUnsignedShort());
                int length = input.readInt();
                if (attributeName.equals("Code")) {
                    codeAttribute = readCodeAttribute(input, pool);
                } else {
                    input.skipNBytes(Integer.toUnsignedLong(length));
                }
            }
            methods.add(new JavaMethod(owner, accessFlags, name, descriptor, codeAttribute.maxStack(),
                    codeAttribute.maxLocals(), codeAttribute.code(), codeAttribute.exceptionHandlers()));
        }
        return methods;
    }

    /** A method's {@code Code} attribute; {@link #ABSENT} for abstract and native methods. */
    private record CodeAttribute(int maxStack, int maxLocals, byte[] code, List<ExceptionHandler> exceptionHandlers) {
        private static final CodeAttribute ABSENT = new CodeAttribute(0, 0, null, List.of());
    }

    /** Reads a {@code Code} attribute body, just past its name and length. */
    private CodeAttribute readCodeAttribute(DataInputStream input, ConstantPool pool) throws IOException {
        int maxStack = input.readUnsignedShort();
        int maxLocals = input.readUnsignedShort();
        byte[] code = input.readNBytes(input.readInt());
        int exceptionTableLength = input.readUnsignedShort();
        List<ExceptionHandler> handlers = new ArrayList<>(exceptionTableLength);
        for (int k = 0; k < exceptionTableLength; k++) {
            int startPc = input.readUnsignedShort();
            int endPc = input.readUnsignedShort();
            int handlerPc = input.readUnsignedShort();
            int catchTypeIndex = input.readUnsignedShort();
            handlers.add(new ExceptionHandler(startPc, endPc, handlerPc,
                    catchTypeIndex == 0 ? null : pool.className(catchTypeIndex)));
        }
        skipAttributes(input, pool);
        return new CodeAttribute(maxStack, maxLocals, code, List.copyOf(handlers));
    }

    private void skipAttributes(DataInputStream input, ConstantPool pool) throws IOException {
        int count = input.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            pool.utf8(input.readUnsignedShort());
            input.skipNBytes(Integer.toUnsignedLong(input.readInt()));
        }
    }

    /** Reads the class's own attribute table, extracting {@code @Board}/{@code @Watchdog}'s values if present. */
    private ClassAttributes readClassAttributes(DataInputStream input, ConstantPool pool) throws IOException {
        int count = input.readUnsignedShort();
        ClassAnnotations annotations = ClassAnnotations.NONE;
        List<BootstrapMethod> bootstrapMethods = List.of();
        for (int i = 0; i < count; i++) {
            String attributeName = pool.utf8(input.readUnsignedShort());
            int length = input.readInt();
            if (attributeName.equals("RuntimeVisibleAnnotations")) {
                annotations = readAnnotations(input, pool, annotations);
            } else if (attributeName.equals("BootstrapMethods")) {
                bootstrapMethods = BootstrapMethodsReader.read(input);
            } else {
                input.skipNBytes(Integer.toUnsignedLong(length));
            }
        }
        return new ClassAttributes(annotations.boardApiClassNames(), annotations.watchdogTimeoutMillis(),
                bootstrapMethods);
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
        List<String> boardApiClassNames = annotations.boardApiClassNames();
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
            if (isBoard) {
                // @Board's value() is Class<? extends ArduinoBoard>[]; javac always wraps even a bare
                // `@Board(X.class)` shorthand as a one-element array in the class file, but a single
                // 'c' tag is accepted too for class files compiled against an older, non-array Board.
                if (value.classInternalName() != null) {
                    boardApiClassNames = List.of(value.classInternalName());
                } else if (!value.classInternalNames().isEmpty()) {
                    boardApiClassNames = value.classInternalNames();
                }
            }
            if (isWatchdog && elementName.equals("timeoutMillis") && value.intValue() != null) {
                watchdogTimeoutMillis = Optional.of(value.intValue());
            }
        }
        if (isBoard && boardApiClassNames.isEmpty()) {
            throw new CompileException("@Board requires at least one board, e.g. @Board(ArduinoUnoR4WiFi.class)");
        }
        return new ClassAnnotations(boardApiClassNames, watchdogTimeoutMillis);
    }

    /** One {@code element_value}'s parsed payload — only the fields a supported tag can populate are non-empty. */
    private record ElementValue(String classInternalName, Integer intValue, List<String> classInternalNames) {
        private static final ElementValue EMPTY = new ElementValue(null, null, List.of());
    }

    /** Reads one {@code element_value}. */
    private ElementValue readElementValue(DataInputStream input, ConstantPool pool) throws IOException {
        int tag = input.readUnsignedByte();
        return switch (tag) {
            case 'I' -> new ElementValue(null, pool.integer(input.readUnsignedShort()), List.of());
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
                yield new ElementValue(internalName, null, List.of());
            }
            case '@' -> {
                readAnnotation(input, pool, ClassAnnotations.NONE);
                yield ElementValue.EMPTY;
            }
            case '[' -> {
                int numValues = input.readUnsignedShort();
                List<String> classInternalNames = new ArrayList<>();
                for (int i = 0; i < numValues; i++) {
                    ElementValue element = readElementValue(input, pool);
                    if (element.classInternalName() != null) {
                        classInternalNames.add(element.classInternalName());
                    }
                }
                yield new ElementValue(null, null, List.copyOf(classInternalNames));
            }
            default -> skipConstElementValue(input, tag);
        };
    }

    /** Skips an {@code element_value} tagged with one of {@link #SKIPPED_CONST_ELEMENT_TAGS}. */
    private ElementValue skipConstElementValue(DataInputStream input, int tag) throws IOException {
        if (SKIPPED_CONST_ELEMENT_TAGS.indexOf(tag) < 0) {
            throw new CompileException("Unsupported annotation element_value tag " + tag);
        }
        input.readUnsignedShort();
        return ElementValue.EMPTY;
    }
}

package io.github.jabrena.juno.ir;

import java.util.Optional;

/**
 * The element type of a Juno-supported array. Integer-like elements are widened to (on load) or narrowed
 * from (on store) an {@code int32_t} {@link Value}; float elements retain {@link JunoType#FLOAT32}.
 */
public enum ArrayElementType {
    BYTE(JunoType.INT32),
    CHAR(JunoType.INT32),
    SHORT(JunoType.INT32),
    INT(JunoType.INT32),
    REFERENCE(JunoType.INT32),
    LONG(JunoType.INT64),
    FLOAT(JunoType.FLOAT32),
    DOUBLE(JunoType.FLOAT64);

    private final JunoType valueType;

    ArrayElementType(JunoType valueType) {
        this.valueType = valueType;
    }

    public JunoType valueType() {
        return valueType;
    }

    /** Maps a {@code newarray} {@code atype} operand (JVMS 6.5.newarray) to the element types Juno supports. */
    public static Optional<ArrayElementType> fromAtype(int atype) {
        return switch (atype) {
            case 4, 8 -> Optional.of(BYTE); // boolean, byte: both a single signed byte, baload/bastore
            case 5 -> Optional.of(CHAR);
            case 6 -> Optional.of(FLOAT);
            case 7 -> Optional.of(DOUBLE);
            case 11 -> Optional.of(LONG);
            case 9 -> Optional.of(SHORT);
            case 10 -> Optional.of(INT);
            default -> Optional.empty();
        };
    }

    /** Maps an array type descriptor's element character ({@code [B} &#8594; {@code B}) to its element type. */
    public static Optional<ArrayElementType> fromDescriptor(char descriptor) {
        return switch (descriptor) {
            case 'Z', 'B' -> Optional.of(BYTE);
            case 'C' -> Optional.of(CHAR);
            case 'S' -> Optional.of(SHORT);
            case 'I' -> Optional.of(INT);
            case 'J' -> Optional.of(LONG);
            case 'F' -> Optional.of(FLOAT);
            case 'D' -> Optional.of(DOUBLE);
            default -> Optional.empty();
        };
    }

    public static Optional<ArrayElementType> fromArrayDescriptor(String descriptor) {
        if (!descriptor.startsWith("[") || descriptor.length() < 2) {
            return Optional.empty();
        }
        char component = descriptor.charAt(1);
        if (component == '[' || component == 'L') {
            return Optional.of(REFERENCE);
        }
        return fromDescriptor(component);
    }
}

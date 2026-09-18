package io.github.jabrena.juno.ir;

import java.util.Optional;

/**
 * The element type of a Juno-supported array. Every element is widened to (on load) or narrowed from (on
 * store) an {@code int32_t} {@link Value}, matching how Juno already represents {@code boolean}/{@code byte}/
 * {@code char}/{@code short} scalars.
 */
public enum ArrayElementType {
    BYTE, CHAR, SHORT, INT;

    /** Maps a {@code newarray} {@code atype} operand (JVMS 6.5.newarray) to the element types Juno supports. */
    public static Optional<ArrayElementType> fromAtype(int atype) {
        return switch (atype) {
            case 4, 8 -> Optional.of(BYTE); // boolean, byte: both a single signed byte, baload/bastore
            case 5 -> Optional.of(CHAR);
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
            default -> Optional.empty();
        };
    }
}

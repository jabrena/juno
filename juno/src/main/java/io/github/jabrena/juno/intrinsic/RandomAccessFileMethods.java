package io.github.jabrena.juno.intrinsic;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;

/**
 * The read-only {@code java.io.RandomAccessFile} subset, backed by the SD card's open-file table: a file opened
 * with mode {@code "r"} by a compile-time literal path, then {@code seek}/{@code getFilePointer}/{@code length},
 * single-byte and buffer reads, {@code skipBytes} and {@code close}. Failures raise the JDK's own exceptions, so
 * the same code runs on a desktop JVM against a local file.
 */
public final class RandomAccessFileMethods {
    public static final String OWNER = "java/io/RandomAccessFile";
    public static final MethodRef CONSTRUCTOR = method("<init>", "(Ljava/lang/String;Ljava/lang/String;)V");

    /** The only mode Juno opens a file with: read-only. */
    public static final String READ_ONLY_MODE = "r";

    /** Every exception a {@code RandomAccessFile} call can raise, so a program that uses one has their class ids. */
    public static final List<String> EXCEPTIONS = List.of("java/io/IOException", "java/io/FileNotFoundException",
            "java/io/EOFException", "java/lang/IndexOutOfBoundsException");

    private RandomAccessFileMethods() {
    }

    public static MethodRef method(String name, String descriptor) {
        return new MethodRef(OWNER, name, descriptor);
    }

    public static boolean isOwner(String className) {
        return OWNER.equals(className);
    }

    /** Every {@code RandomAccessFile} intrinsic can fail: a closed file, a missing one, a short read. */
    public static boolean mayThrow(Intrinsic intrinsic) {
        return intrinsic.name().startsWith("RAF_");
    }

    /** {@code read(byte[]...)}/{@code readFully(byte[]...)}: the front end adds the buffer's known capacity. */
    public static boolean isBufferRead(MethodRef called) {
        return isOwner(called.owner()) && (called.name().equals("read") || called.name().equals("readFully"))
                && called.descriptor().startsWith("([B");
    }
}

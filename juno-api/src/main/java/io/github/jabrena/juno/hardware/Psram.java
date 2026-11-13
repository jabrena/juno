package io.github.jabrena.juno.hardware;

/**
 * Explicit access to the board's external pseudo-static RAM (PSRAM).
 *
 * <p>PSRAM is an off-heap byte store. A successful {@link #allocate(int)} returns a positive opaque handle, not a native
 * address and not a Java object. The caller owns the handle and must call {@link #release(int)} exactly once when the
 * payload is no longer needed. The garbage collector does not scan or reclaim the payload.
 *
 * <p>Buffers are zero-initialized, at least four-byte aligned and byte-addressable. Generic buffers are CPU-accessible
 * only; they are not an implicit DMA or interrupt-safe allocation. On dual-core ESP32 boards Juno serializes each
 * intrinsic call across carriers, including registry mutation and payload copies; callers still need higher-level
 * synchronization when several calls form one logical transaction. The runtime never falls back to internal SRAM.
 */
public final class Psram {

    /** Successful scalar mutation or release. Successful bulk operations return their byte count instead. */
    public static final int OK = 0;

    /** The requested size, value, or array range is invalid. */
    public static final int ERROR_INVALID_ARGUMENT = -1;

    /** No usable PSRAM was detected by the runtime. */
    public static final int ERROR_UNAVAILABLE = -2;

    /** The request cannot fit in the current largest PSRAM block. */
    public static final int ERROR_EXHAUSTED = -3;

    /** The handle is unknown, released, forged or stale. */
    public static final int ERROR_INVALID_HANDLE = -4;

    /** The requested PSRAM range does not fit the buffer. */
    public static final int ERROR_OUT_OF_BOUNDS = -5;

    /** The fixed registry has no free live-handle slot or token space. */
    public static final int ERROR_HANDLE_LIMIT = -6;

    /** Maximum number of simultaneously live generic PSRAM handles. */
    public static final int MAX_LIVE_HANDLES = 16;

    private Psram() {
    }

    /** Whether the runtime currently exposes usable PSRAM. */
    public static native boolean isAvailable();

    /** The board's nominal PSRAM capacity, or zero for an unsupported runtime. */
    public static native int nominalCapacityBytes();

    /** Total PSRAM detected by the runtime allocator, independent of current free space. */
    public static native int totalBytes();

    /** Current free PSRAM bytes; fragmentation can make the largest request smaller. */
    public static native int freeBytes();

    /** Largest currently allocatable contiguous PSRAM block. */
    public static native int largestFreeBlockBytes();

    /**
     * Allocates a zero-initialized caller-owned PSRAM buffer.
     *
     * @param byteCount positive number of bytes
     * @return a positive opaque handle, or a negative {@code ERROR_*} result
     */
    public static native int allocate(int byteCount);

    /**
     * Returns a live buffer's capacity.
     *
     * @param handle an opaque handle returned by {@link #allocate(int)}
     * @return the capacity in bytes, or {@link #ERROR_INVALID_HANDLE}
     */
    public static native int capacity(int handle);

    /**
     * Reads one unsigned byte.
     *
     * @param handle an opaque handle returned by {@link #allocate(int)}
     * @param offset byte offset within the buffer
     * @return {@code 0..255}, or a negative {@code ERROR_*} result
     */
    public static native int readByte(int handle, int offset);

    /**
     * Writes one byte. The value must be in {@code 0..255}.
     *
     * @return {@link #OK}, or a negative {@code ERROR_*} result
     */
    public static native int writeByte(int handle, int offset, int value);

    /**
     * Copies bytes from PSRAM into a caller-owned Java array. A rejected request copies nothing.
     *
     * @return {@code length} on success, or a negative {@code ERROR_*} result
     */
    public static int read(int handle, int sourceOffset, byte[] destination, int destinationOffset, int length) {
        return read(handle, sourceOffset, destination, destination.length, destinationOffset, length);
    }

    /**
     * Copies bytes from a caller-owned Java array into PSRAM. A rejected request writes nothing.
     *
     * @return {@code length} on success, or a negative {@code ERROR_*} result
     */
    public static int write(int handle, int destinationOffset, byte[] source, int sourceOffset, int length) {
        return write(handle, destinationOffset, source, source.length, sourceOffset, length);
    }

    /** Native overload carrying the Java array capacity for complete bounds validation. */
    static native int read(int handle, int sourceOffset, byte[] destination, int destinationCapacity,
                           int destinationOffset, int length);

    /** Native overload carrying the Java array capacity for complete bounds validation. */
    static native int write(int handle, int destinationOffset, byte[] source, int sourceCapacity, int sourceOffset,
                            int length);

    /**
     * Releases a live buffer exactly once.
     *
     * @return {@link #OK}, or {@link #ERROR_INVALID_HANDLE}
     */
    public static native int release(int handle);
}

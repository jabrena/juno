package io.github.jabrena.juno.tny2;

/**
 * The four dimensions in a TNY2 header, with the limits the board engine accepts.
 *
 * @param width embedding width D, a multiple of {@code heads}
 * @param layers number of transformer blocks L
 * @param heads attention heads per block H
 * @param hidden feed-forward width HID
 */
public record Tny2Config(int width, int layers, int heads, int hidden) {
    /** Positions the model attends over; the position table always has this many rows. */
    public static final int CONTEXT = 128;
    public static final int MIN_WIDTH = 8;
    public static final int MAX_WIDTH = 128;
    public static final int MAX_LAYERS = 8;
    public static final int MIN_HIDDEN = 8;
    public static final int MAX_HIDDEN = 512;

    public Tny2Config {
        if (width < MIN_WIDTH || width > MAX_WIDTH) {
            throw new Tny2FormatException("width must be %d to %d, not %d".formatted(MIN_WIDTH, MAX_WIDTH, width));
        }
        if (layers < 1 || layers > MAX_LAYERS) {
            throw new Tny2FormatException("layers must be 1 to %d, not %d".formatted(MAX_LAYERS, layers));
        }
        if (heads < 1 || width % heads != 0) {
            throw new Tny2FormatException("heads must divide the width %d, not %d".formatted(width, heads));
        }
        if (hidden < MIN_HIDDEN || hidden > MAX_HIDDEN) {
            throw new Tny2FormatException(
                    "hidden must be %d to %d, not %d".formatted(MIN_HIDDEN, MAX_HIDDEN, hidden));
        }
    }

    /** The shape of the TINY 2.1 model: width 96, 4 layers, 4 heads, 256 hidden. */
    public static Tny2Config tiny21() {
        return new Tny2Config(96, 4, 4, 256);
    }

    public int headWidth() {
        return width / heads;
    }

    /** Number of trainable values: tables, attention and feed-forward matrices, and gains. */
    public int parameterCount() {
        int perLayer = 2 * width + 4 * width * width + 2 * hidden * width;
        return Tny2Vocabulary.SIZE * width + CONTEXT * width + layers * perLayer + width;
    }

    /** Size in bytes of the TNY2 file with these dimensions. */
    public int fileSize() {
        int perLayer = 2 * gainBytes() + 4 * tableBytes(width, width) + tableBytes(hidden, width)
                + tableBytes(width, hidden);
        return Tny2Writer.HEADER_BYTES + tableBytes(Tny2Vocabulary.SIZE, width) + tableBytes(CONTEXT, width)
                + layers * perLayer + gainBytes();
    }

    private int gainBytes() {
        return Float.BYTES * width;
    }

    private static int tableBytes(int rows, int cols) {
        return rows * (Float.BYTES + cols);
    }
}

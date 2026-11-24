package io.github.jabrena.juno.lm;

/**
 * Everything a JLM1 header declares, with the limits the board engine accepts.
 *
 * @param width embedding width D, a multiple of {@code heads}
 * @param layers number of transformer blocks L
 * @param heads attention heads per block H
 * @param hidden feed-forward width HID
 * @param context positions the model attends over; the position table has this many rows
 * @param vocabulary the tokens, whose alphabet the header lists
 */
public record JlmConfig(int width, int layers, int heads, int hidden, int context, JlmVocabulary vocabulary) {
    /** The context the trainer uses. */
    public static final int DEFAULT_CONTEXT = 128;
    /** Shortest context: the longest question, its closing {@code END} and one answer character. */
    public static final int MIN_CONTEXT = JlmVocabulary.MAX_QUESTION + 2;
    public static final int MAX_CONTEXT = 128;
    public static final int MIN_WIDTH = 8;
    public static final int MAX_WIDTH = 128;
    public static final int MAX_LAYERS = 8;
    public static final int MIN_HIDDEN = 8;
    public static final int MAX_HIDDEN = 512;

    public JlmConfig {
        if (width < MIN_WIDTH || width > MAX_WIDTH) {
            throw new JlmFormatException("width must be %d to %d, not %d".formatted(MIN_WIDTH, MAX_WIDTH, width));
        }
        if (layers < 1 || layers > MAX_LAYERS) {
            throw new JlmFormatException("layers must be 1 to %d, not %d".formatted(MAX_LAYERS, layers));
        }
        if (heads < 1 || width % heads != 0) {
            throw new JlmFormatException("heads must divide the width %d, not %d".formatted(width, heads));
        }
        if (hidden < MIN_HIDDEN || hidden > MAX_HIDDEN) {
            throw new JlmFormatException(
                    "hidden must be %d to %d, not %d".formatted(MIN_HIDDEN, MAX_HIDDEN, hidden));
        }
        if (context < MIN_CONTEXT || context > MAX_CONTEXT) {
            throw new JlmFormatException(
                    "context must be %d to %d, not %d".formatted(MIN_CONTEXT, MAX_CONTEXT, context));
        }
    }

    /** A shape over the {@link JlmVocabulary#ASCII} vocabulary and the {@link #DEFAULT_CONTEXT}, as the trainer uses. */
    public JlmConfig(int width, int layers, int heads, int hidden) {
        this(width, layers, heads, hidden, DEFAULT_CONTEXT, JlmVocabulary.ASCII);
    }

    /** The bundled model's shape: width 96, 4 layers, 4 heads, 256 hidden. */
    public static JlmConfig standard() {
        return new JlmConfig(96, 4, 4, 256);
    }

    public int headWidth() {
        return width / heads;
    }

    /** Number of trainable values: tables, attention and feed-forward matrices, and gains. */
    public int parameterCount() {
        int perLayer = 2 * width + 4 * width * width + 2 * hidden * width;
        return vocabulary.size() * width + context * width + layers * perLayer + width;
    }

    /** Size in bytes of the header: the magic, six dimensions and the alphabet. */
    public int headerSize() {
        return JlmWriter.FIXED_HEADER_BYTES + vocabulary.alphabet().length();
    }

    /** Size in bytes of the JLM1 file with these dimensions. */
    public int fileSize() {
        int perLayer = 2 * gainBytes() + 4 * tableBytes(width, width) + tableBytes(hidden, width)
                + tableBytes(width, hidden);
        return headerSize() + tableBytes(vocabulary.size(), width) + tableBytes(context, width) + layers * perLayer
                + gainBytes();
    }

    private int gainBytes() {
        return Float.BYTES * width;
    }

    private static int tableBytes(int rows, int cols) {
        return rows * (Float.BYTES + cols);
    }
}

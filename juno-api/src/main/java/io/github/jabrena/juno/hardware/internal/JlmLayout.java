package io.github.jabrena.juno.hardware.internal;

/**
 * Where everything is in a loaded JLM1 file: the header's dimensions, checked as the board engine accepts them, the
 * alphabet as token tables, the gains copied out for the RMS norms, and the offset of every table. A JLM1 file is a
 * header (the magic {@code JLM1}; width, layers, heads, hidden, context and vocabulary size as little-endian 16-bit
 * values; the alphabet, one byte per token from 2 up), then the embedding and position tables, each layer's attention
 * gains, query, key, value and output tables, feed-forward gains, up and down tables, and the final gains. A table row
 * is a {@code float} scale and its signed bytes. One layout is allocated with its engine and reused for every model.
 */
final class JlmLayout {
    static final int MAX_WIDTH = 128;
    static final int MAX_LAYERS = 8;
    static final int MAX_HIDDEN = 512;
    static final int MAX_CONTEXT = 128;
    static final int MIN_CONTEXT = JlmQuestion.MAX_LENGTH + 2;
    static final int RESERVED = 2;
    static final int MAX_ALPHABET = 95;
    static final int MAX_VOCAB = RESERVED + MAX_ALPHABET;
    private static final int FIXED_HEADER = 16;
    private static final int GAIN_VECTORS = 2 * MAX_LAYERS + 1;
    private static final int FIRST = ' ';
    private static final int LAST = '~';

    private final float[] gains = new float[GAIN_VECTORS * MAX_WIDTH];
    private final char[] alphabet = new char[MAX_ALPHABET];
    /** The token of each printable character, indexed by {@code character - ' '}; 0 outside the alphabet. */
    private final int[] tokenOf = new int[MAX_ALPHABET];

    private int width;
    private int layers;
    private int heads;
    private int hidden;
    private int context;
    private int vocabulary;
    private int rowStride;
    private int embeddingOffset;
    private int positionOffset;
    private int firstLayerOffset;
    private int layerBytes;

    /** Reads the header, the alphabet and the gains of {@code file}; false when the engine cannot run it. */
    boolean read(ModelMemory file) {
        if (file.size() < FIXED_HEADER || file.readByte(0) != 'J' || file.readByte(1) != 'L'
                || file.readByte(2) != 'M' || file.readByte(3) != '1') {
            return false;
        }
        int w = short16(file, 4);
        int l = short16(file, 6);
        int h = short16(file, 8);
        int hid = short16(file, 10);
        int ctx = short16(file, 12);
        int vocab = short16(file, 14);
        if (w < 8 || w > MAX_WIDTH || l < 1 || l > MAX_LAYERS || h < 1 || w % h != 0 || hid < 8 || hid > MAX_HIDDEN
                || ctx < MIN_CONTEXT || ctx > MAX_CONTEXT || vocab <= RESERVED || vocab > MAX_VOCAB) {
            return false;
        }
        int header = FIXED_HEADER + vocab - RESERVED;
        int stride = Float.BYTES + w;
        int perLayer = 2 * Float.BYTES * w + 4 * w * stride + hid * stride + w * (Float.BYTES + hid);
        int expected = header + vocab * stride + ctx * stride + l * perLayer + Float.BYTES * w;
        if (file.size() != expected || !readAlphabet(file, vocab)) {
            return false;
        }
        width = w;
        layers = l;
        heads = h;
        hidden = hid;
        context = ctx;
        vocabulary = vocab;
        rowStride = stride;
        embeddingOffset = header;
        positionOffset = header + vocab * stride;
        firstLayerOffset = positionOffset + ctx * stride;
        layerBytes = perLayer;
        for (int layer = 0; layer < l; layer++) {
            file.readFloats(attentionGainOffset(layer), gains, attentionGain(layer), w);
            file.readFloats(feedForwardGainOffset(layer), gains, feedForwardGain(layer), w);
        }
        file.readFloats(firstLayerOffset + l * perLayer, gains, finalGain(), w);
        return true;
    }

    /** Reads the alphabet into the token tables: distinct printable characters that include the space. */
    private boolean readAlphabet(ModelMemory file, int vocab) {
        for (int i = 0; i < MAX_ALPHABET; i++) {
            tokenOf[i] = 0;
        }
        for (int token = RESERVED; token < vocab; token++) {
            int character = file.readByte(FIXED_HEADER + token - RESERVED);
            if (character < FIRST || character > LAST || tokenOf[character - FIRST] != 0) {
                return false;
            }
            tokenOf[character - FIRST] = token;
            alphabet[token - RESERVED] = (char) character;
        }
        return tokenOf[0] != 0;
    }

    private static int short16(ModelMemory file, int offset) {
        return (short) (file.readByte(offset) | file.readByte(offset + 1) << 8);
    }

    /** The token of a normalized question character; one outside the alphabet reads as the space. */
    int token(char character) {
        int token = character >= FIRST && character <= LAST ? tokenOf[character - FIRST] : 0;
        return token != 0 ? token : tokenOf[0];
    }

    /** The character of a token from {@link #RESERVED} up. */
    char character(int token) {
        return alphabet[token - RESERVED];
    }

    float[] gains() {
        return gains;
    }

    int width() {
        return width;
    }

    int layers() {
        return layers;
    }

    int heads() {
        return heads;
    }

    int headWidth() {
        return width / heads;
    }

    int hidden() {
        return hidden;
    }

    int context() {
        return context;
    }

    int vocabulary() {
        return vocabulary;
    }

    /** Bytes between two rows of a table with {@link #width()} columns, and of the attention cache. */
    int rowStride() {
        return rowStride;
    }

    /** Bytes between two rows of the down table, which has {@link #hidden()} columns. */
    int downStride() {
        return Float.BYTES + hidden;
    }

    /** Bytes of the attention cache: a key and a value row per layer and position. */
    int cacheBytes() {
        return 2 * layers * context * rowStride;
    }

    int embeddingOffset() {
        return embeddingOffset;
    }

    int positionOffset() {
        return positionOffset;
    }

    /** Where in {@link #gains()} the attention gains of {@code layer} start. */
    int attentionGain(int layer) {
        return 2 * layer * width;
    }

    /** Where in {@link #gains()} the feed-forward gains of {@code layer} start. */
    int feedForwardGain(int layer) {
        return (2 * layer + 1) * width;
    }

    /** Where in {@link #gains()} the final gains start. */
    int finalGain() {
        return 2 * layers * width;
    }

    private int attentionGainOffset(int layer) {
        return firstLayerOffset + layer * layerBytes;
    }

    int queryOffset(int layer) {
        return attentionGainOffset(layer) + Float.BYTES * width;
    }

    int keyOffset(int layer) {
        return queryOffset(layer) + width * rowStride;
    }

    int valueOffset(int layer) {
        return keyOffset(layer) + width * rowStride;
    }

    int outputOffset(int layer) {
        return valueOffset(layer) + width * rowStride;
    }

    private int feedForwardGainOffset(int layer) {
        return outputOffset(layer) + width * rowStride;
    }

    int upOffset(int layer) {
        return feedForwardGainOffset(layer) + Float.BYTES * width;
    }

    int downOffset(int layer) {
        return upOffset(layer) + hidden * rowStride;
    }

    int keyCacheOffset(int layer) {
        return 2 * layer * context * rowStride;
    }

    int valueCacheOffset(int layer) {
        return (2 * layer + 1) * context * rowStride;
    }
}

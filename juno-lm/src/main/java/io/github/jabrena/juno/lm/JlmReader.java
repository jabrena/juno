package io.github.jabrena.juno.lm;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads a JLM1 file with the same checks as the board engine: magic, dimension limits, alphabet and exact length.
 */
public final class JlmReader {

    public JlmModel read(Path file) throws IOException {
        return read(Files.readAllBytes(file));
    }

    public JlmModel read(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        try {
            byte[] magic = new byte[JlmWriter.MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, JlmWriter.MAGIC)) {
                throw new JlmFormatException("not a JLM1 file: the magic is not JLM1");
            }
            int width = buffer.getShort();
            int layers = buffer.getShort();
            int heads = buffer.getShort();
            int hidden = buffer.getShort();
            int context = buffer.getShort();
            int size = buffer.getShort();
            if (size <= JlmVocabulary.RESERVED) {
                throw new JlmFormatException("vocabulary must have more than %d tokens, not %d"
                        .formatted(JlmVocabulary.RESERVED, size));
            }
            byte[] alphabet = new byte[size - JlmVocabulary.RESERVED];
            buffer.get(alphabet);
            JlmVocabulary vocabulary = new JlmVocabulary(new String(alphabet, StandardCharsets.ISO_8859_1));
            return body(buffer, new JlmConfig(width, layers, heads, hidden, context, vocabulary));
        } catch (BufferUnderflowException truncated) {
            throw new JlmFormatException("JLM1 file is truncated at byte " + buffer.position());
        }
    }

    /** Reads the tables and gains after a header, which must be the rest of the buffer. */
    static JlmModel body(ByteBuffer buffer, JlmConfig config) {
        int width = config.width();
        QuantizedTable embedding = table(buffer, config.vocabulary().size(), width);
        QuantizedTable position = table(buffer, config.context(), width);
        List<JlmLayer> layers = new ArrayList<>(config.layers());
        for (int l = 0; l < config.layers(); l++) {
            layers.add(new JlmLayer(gains(buffer, width), table(buffer, width, width), table(buffer, width, width),
                    table(buffer, width, width), table(buffer, width, width), gains(buffer, width),
                    table(buffer, config.hidden(), width), table(buffer, width, config.hidden())));
        }
        GainVector finalGain = gains(buffer, width);
        if (buffer.hasRemaining()) {
            throw new JlmFormatException("model file has %d bytes after the model, expected %d bytes in all"
                    .formatted(buffer.remaining(), buffer.position()));
        }
        return new JlmModel(config, embedding, position, layers, finalGain);
    }

    private static QuantizedTable table(ByteBuffer buffer, int rows, int cols) {
        float[] scales = new float[rows];
        byte[] weights = new byte[rows * cols];
        for (int r = 0; r < rows; r++) {
            scales[r] = buffer.getFloat();
            buffer.get(weights, r * cols, cols);
        }
        return new QuantizedTable(rows, cols, scales, weights);
    }

    private static GainVector gains(ByteBuffer buffer, int size) {
        float[] values = new float[size];
        for (int i = 0; i < size; i++) {
            values[i] = buffer.getFloat();
        }
        return new GainVector(values);
    }
}

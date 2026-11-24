package io.github.jabrena.juno.lm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes the JLM1 binary layout. The header is the magic {@code JLM1}; width, layers, heads, hidden, context and
 * vocabulary size as little-endian 16-bit values; and the alphabet, one ASCII byte per token from 2 up. Then come the
 * embedding and position tables, each layer's gains and tables, and the final gains. A table is written row by row as
 * a little-endian {@code float} scale followed by its signed-byte weights.
 */
public final class JlmWriter {
    static final byte[] MAGIC = {'J', 'L', 'M', '1'};
    /** The header before the alphabet: the magic and six 16-bit dimensions. */
    static final int FIXED_HEADER_BYTES = MAGIC.length + 6 * Short.BYTES;

    public byte[] toBytes(JlmModel model) {
        JlmConfig config = model.config();
        ByteBuffer buffer = ByteBuffer.allocate(config.fileSize()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MAGIC).putShort((short) config.width()).putShort((short) config.layers())
                .putShort((short) config.heads()).putShort((short) config.hidden()).putShort((short) config.context())
                .putShort((short) config.vocabulary().size())
                .put(config.vocabulary().alphabet().getBytes(StandardCharsets.US_ASCII));
        put(buffer, model.embedding());
        put(buffer, model.position());
        for (JlmLayer layer : model.layers()) {
            put(buffer, layer.attentionGain());
            put(buffer, layer.query());
            put(buffer, layer.key());
            put(buffer, layer.value());
            put(buffer, layer.output());
            put(buffer, layer.feedForwardGain());
            put(buffer, layer.up());
            put(buffer, layer.down());
        }
        put(buffer, model.finalGain());
        return buffer.array();
    }

    /** Writes the model, creating the parent directories when needed. */
    public void write(JlmModel model, Path output) throws IOException {
        Path absolute = output.toAbsolutePath();
        Files.createDirectories(Objects.requireNonNull(absolute.getParent(), "output has no parent directory"));
        Files.write(absolute, toBytes(model));
    }

    private static void put(ByteBuffer buffer, QuantizedTable table) {
        for (int r = 0; r < table.rows(); r++) {
            buffer.putFloat(table.scale(r));
            for (int c = 0; c < table.cols(); c++) {
                buffer.put(table.weight(r, c));
            }
        }
    }

    private static void put(ByteBuffer buffer, GainVector gains) {
        for (int i = 0; i < gains.size(); i++) {
            buffer.putFloat(gains.get(i));
        }
    }
}

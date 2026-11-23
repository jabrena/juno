package io.github.jabrena.juno.tny2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes the TNY2 binary layout: the magic {@code TNY2}, width, layers, heads and hidden as little-endian 16-bit
 * values, the embedding and position tables, each layer's gains and tables, and the final gains. A table is written
 * row by row as a little-endian {@code float} scale followed by its signed-byte weights.
 */
public final class Tny2Writer {
    static final byte[] MAGIC = {'T', 'N', 'Y', '2'};
    static final int HEADER_BYTES = MAGIC.length + 4 * Short.BYTES;

    public byte[] toBytes(Tny2Model model) {
        Tny2Config config = model.config();
        ByteBuffer buffer = ByteBuffer.allocate(config.fileSize()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MAGIC).putShort((short) config.width()).putShort((short) config.layers())
                .putShort((short) config.heads()).putShort((short) config.hidden());
        put(buffer, model.embedding());
        put(buffer, model.position());
        for (Tny2Layer layer : model.layers()) {
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
    public void write(Tny2Model model, Path output) throws IOException {
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

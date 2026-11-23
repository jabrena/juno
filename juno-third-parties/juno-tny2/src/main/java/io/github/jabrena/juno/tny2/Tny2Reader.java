package io.github.jabrena.juno.tny2;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reads a TNY2 file with the same checks as the board engine: magic, dimension limits and exact length. */
public final class Tny2Reader {

    public Tny2Model read(Path file) throws IOException {
        return read(Files.readAllBytes(file));
    }

    public Tny2Model read(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        try {
            byte[] magic = new byte[Tny2Writer.MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, Tny2Writer.MAGIC)) {
                throw new Tny2FormatException("not a TNY2 file: the magic is not TNY2");
            }
            Tny2Config config = new Tny2Config(buffer.getShort(), buffer.getShort(), buffer.getShort(),
                    buffer.getShort());
            int width = config.width();
            QuantizedTable embedding = table(buffer, Tny2Vocabulary.SIZE, width);
            QuantizedTable position = table(buffer, Tny2Config.CONTEXT, width);
            List<Tny2Layer> layers = new ArrayList<>(config.layers());
            for (int l = 0; l < config.layers(); l++) {
                layers.add(new Tny2Layer(gains(buffer, width), table(buffer, width, width),
                        table(buffer, width, width), table(buffer, width, width), table(buffer, width, width),
                        gains(buffer, width), table(buffer, config.hidden(), width),
                        table(buffer, width, config.hidden())));
            }
            GainVector finalGain = gains(buffer, width);
            if (buffer.hasRemaining()) {
                throw new Tny2FormatException("TNY2 file has %d bytes after the model, expected %d bytes in all"
                        .formatted(buffer.remaining(), config.fileSize()));
            }
            return new Tny2Model(config, embedding, position, layers, finalGain);
        } catch (BufferUnderflowException truncated) {
            throw new Tny2FormatException("TNY2 file is truncated at byte " + buffer.position());
        }
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

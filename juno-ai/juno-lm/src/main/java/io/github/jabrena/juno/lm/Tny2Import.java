package io.github.jabrena.juno.lm;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Reads a model in the TNY2 layout of TINY 2.1, so it can be written again as JLM1. A TNY2 header is the magic
 * {@code TNY2} and width, layers, heads and hidden as little-endian 16-bit values; its vocabulary is always
 * {@link JlmVocabulary#ASCII} and its context always 128 positions, and the tables follow in the same order as in
 * JLM1. The board engine reads JLM1 only.
 */
public final class Tny2Import {
    private static final byte[] MAGIC = {'T', 'N', 'Y', '2'};
    private static final int CONTEXT = 128;

    public JlmModel read(Path file) throws IOException {
        return read(Files.readAllBytes(file));
    }

    public JlmModel read(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        try {
            byte[] magic = new byte[MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new JlmFormatException("not a TNY2 file: the magic is not TNY2");
            }
            JlmConfig config = new JlmConfig(buffer.getShort(), buffer.getShort(), buffer.getShort(),
                    buffer.getShort(), CONTEXT, JlmVocabulary.ASCII);
            return JlmReader.body(buffer, config);
        } catch (BufferUnderflowException truncated) {
            throw new JlmFormatException("TNY2 file is truncated at byte " + buffer.position());
        }
    }
}

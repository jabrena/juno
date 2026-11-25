package io.github.jabrena.juno.dm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Reads and writes the compact JEV1 quantized model format. */
public final class JevModelCodec {
    private static final int HEADER_SIZE = 9;
    private static final int VERSION = 1;
    private static final byte[] MAGIC = {'J', 'E', 'V', '1'};

    public byte[] write(QuantizedJevModel model) {
        if (model.actionCount() > 0xffff || model.featureCount() > 0xffff) {
            throw new JevModelException("JEV1 dimensions must fit in an unsigned short");
        }
        int rowSize = Float.BYTES + Float.BYTES + model.featureCount();
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + model.actionCount() * rowSize)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.put(MAGIC).put((byte) VERSION).putShort((short) model.actionCount()).putShort((short) model.featureCount());
        for (int action = 0; action < model.actionCount(); action++) {
            buffer.putFloat(model.scale(action)).putFloat(model.bias(action));
            for (int feature = 0; feature < model.featureCount(); feature++) {
                buffer.put(model.weight(action, feature));
            }
        }
        return buffer.array();
    }

    public QuantizedJevModel read(byte[] encoded) {
        if (encoded.length < HEADER_SIZE) {
            throw new JevModelException("JEV1 data is shorter than its header");
        }
        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        if (!Arrays.equals(MAGIC, magic) || Byte.toUnsignedInt(buffer.get()) != VERSION) {
            throw new JevModelException("unsupported JEV1 header");
        }
        int actionCount = Short.toUnsignedInt(buffer.getShort());
        int featureCount = Short.toUnsignedInt(buffer.getShort());
        if (actionCount == 0 || featureCount == 0) {
            throw new JevModelException("JEV1 dimensions must be positive");
        }
        int rowSize = Float.BYTES + Float.BYTES + featureCount;
        int expectedLength = HEADER_SIZE + actionCount * rowSize;
        if (encoded.length != expectedLength) {
            throw new JevModelException("JEV1 data length does not match its dimensions");
        }
        byte[][] weights = new byte[actionCount][featureCount];
        float[] scales = new float[actionCount];
        float[] biases = new float[actionCount];
        for (int action = 0; action < actionCount; action++) {
            scales[action] = buffer.getFloat();
            biases[action] = buffer.getFloat();
            buffer.get(weights[action]);
        }
        return QuantizedJevModel.encoded(weights, scales, biases);
    }
}

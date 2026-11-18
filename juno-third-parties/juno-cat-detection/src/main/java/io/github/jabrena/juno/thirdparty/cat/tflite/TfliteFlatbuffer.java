package io.github.jabrena.juno.thirdparty.cat.tflite;

import com.google.flatbuffers.FlatBufferBuilder;

/**
 * Writes TFLite FlatBuffers by hand, field by field, against TensorFlow's {@code schema.fbs} (the TFLM copy at
 * {@code tensorflow/lite/micro/schema/schema.fbs}). A field's vtable slot is its position in the table declaration, so
 * the writer names each slot next to the schema field it fills. Build-machine only.
 */
public final class TfliteFlatbuffer {
    /** The {@code Model} table's field positions in the schema, in declaration order. */
    static final int MODEL_VERSION = 0;
    /** The schema version this writer emits. */
    public static final int SCHEMA_VERSION = 3;
    private static final int MODEL_FIELD_COUNT = 8;

    private TfliteFlatbuffer() {
    }

    /** A {@code Model} with only its {@code version} field set: the first piece of the writer, round-trip tested. */
    public static byte[] modelWithVersion(int version) {
        FlatBufferBuilder builder = new FlatBufferBuilder(64);
        builder.startTable(MODEL_FIELD_COUNT);
        builder.addInt(MODEL_VERSION, version, 0);
        int model = builder.endTable();
        builder.finish(model);
        return builder.sizedByteArray();
    }
}

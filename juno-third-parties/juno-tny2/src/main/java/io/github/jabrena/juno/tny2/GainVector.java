package io.github.jabrena.juno.tny2;

import java.util.Arrays;

/** The per-channel {@code float} gains of one RMS normalization, stored unquantized. */
public final class GainVector {
    private final float[] values;

    public GainVector(float[] values) {
        if (values.length == 0) {
            throw new Tny2FormatException("gain vector is empty");
        }
        this.values = values.clone();
    }

    public int size() {
        return values.length;
    }

    public float get(int index) {
        return values[index];
    }

    public void copyTo(float[] target, int offset) {
        System.arraycopy(values, 0, target, offset, values.length);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GainVector gains && Arrays.equals(values, gains.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}

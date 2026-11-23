package io.github.jabrena.juno.tny2.training;

/**
 * Where one weight matrix or gain vector lives in the network's flat parameter array, row-major.
 *
 * @param offset index of the first value
 * @param rows output size; 1 for a gain vector
 * @param cols input size
 */
record Slice(int offset, int rows, int cols) {

    int size() {
        return rows * cols;
    }

    int end() {
        return offset + size();
    }
}

package io.github.jabrena.juno.language;

/**
 * Deliberately outside Juno's supported subset: generated record identity methods reject array
 * components because arrays have no content-identity operations in the board runtime. Juno accepts
 * primitive and String record components, but rejects this reachable {@code toString()} with a
 * diagnostic naming the record component and its array descriptor.
 */
public record UnsupportedFeature(int[] temperatures) {

    public static void main(String[] args) {
        String message = new UnsupportedFeature(new int[] {5}).toString();
    }
}

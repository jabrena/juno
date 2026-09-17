package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.classfile.MethodRef;

final class CppNames {
    private CppNames() {
    }

    static String method(MethodRef reference) {
        String base = (reference.owner() + "_" + reference.name()).replaceAll("[^A-Za-z0-9_]", "_");
        return "juno_" + base + "_" + Integer.toUnsignedString(reference.descriptor().hashCode(), 16);
    }
}

package io.github.jabrena.juno.classfile;

import java.util.List;

/** One entry in a class file's {@code BootstrapMethods} attribute. */
public record BootstrapMethod(int methodHandleIndex, List<Integer> argumentIndexes) {
    public BootstrapMethod {
        argumentIndexes = List.copyOf(argumentIndexes);
    }
}

package io.github.jabrena.juno.classfile;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads the class-level {@code BootstrapMethods} attribute body. */
final class BootstrapMethodsReader {
    private BootstrapMethodsReader() {
    }

    static List<BootstrapMethod> read(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        List<BootstrapMethod> methods = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int handleIndex = input.readUnsignedShort();
            int argumentCount = input.readUnsignedShort();
            List<Integer> arguments = new ArrayList<>(argumentCount);
            for (int j = 0; j < argumentCount; j++) {
                arguments.add(input.readUnsignedShort());
            }
            methods.add(new BootstrapMethod(handleIndex, arguments));
        }
        return List.copyOf(methods);
    }
}

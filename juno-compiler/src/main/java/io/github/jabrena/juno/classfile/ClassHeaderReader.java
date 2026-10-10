package io.github.jabrena.juno.classfile;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads variable-length structural tables from the class-file header. */
final class ClassHeaderReader {
    private ClassHeaderReader() {
    }

    static List<String> readInterfaces(DataInputStream input, ConstantPool pool) throws IOException {
        int count = input.readUnsignedShort();
        List<String> interfaces = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            interfaces.add(pool.className(input.readUnsignedShort()));
        }
        return List.copyOf(interfaces);
    }
}

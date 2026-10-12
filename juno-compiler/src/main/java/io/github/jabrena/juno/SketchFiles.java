package io.github.jabrena.juno;

import java.nio.file.Path;

/**
 * The three generated files of one sketch, side by side: {@code <Name>.S}, {@code <Name>Shim.cpp} and
 * {@code <Name>.ino}, named after the assembly file.
 */
record SketchFiles(Path assembly) {
    Path runtimeShim() {
        return assembly.resolveSibling(baseName() + "Shim.cpp");
    }

    Path wrapper() {
        return assembly.resolveSibling(baseName() + ".ino");
    }

    private String baseName() {
        String fileName = assembly.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}

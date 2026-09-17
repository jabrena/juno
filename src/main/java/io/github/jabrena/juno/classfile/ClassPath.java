package io.github.jabrena.juno.classfile;

import io.github.jabrena.juno.CompileException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Loads class files from directories, individual class files, and JARs. */
public final class ClassPath {
    private final ClassFileReader reader = new ClassFileReader();

    public Map<String, JavaClass> load(List<Path> entries) {
        Map<String, JavaClass> classes = new LinkedHashMap<>();
        for (Path entry : entries) {
            if (!Files.exists(entry)) {
                throw new CompileException("Classpath entry does not exist: " + entry);
            }
            try {
                if (Files.isDirectory(entry)) {
                    loadDirectory(entry, classes);
                } else if (entry.toString().endsWith(".jar")) {
                    loadJar(entry, classes);
                } else if (entry.toString().endsWith(".class")) {
                    add(classes, reader.read(Files.readAllBytes(entry)), entry.toString());
                } else {
                    throw new CompileException("Unsupported classpath entry: " + entry);
                }
            } catch (IOException exception) {
                throw new CompileException("Cannot read classpath entry " + entry, exception);
            }
        }
        return classes;
    }

    private void loadDirectory(Path directory, Map<String, JavaClass> classes) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted()
                    .toList()) {
                add(classes, reader.read(Files.readAllBytes(file)), file.toString());
            }
        }
    }

    private void loadJar(Path path, Map<String, JavaClass> classes) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            List<JarEntry> entries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().equals("module-info.class"))
                    .sorted((left, right) -> left.getName().compareTo(right.getName()))
                    .toList();
            for (JarEntry entry : entries) {
                try (InputStream input = jar.getInputStream(entry)) {
                    add(classes, reader.read(input.readAllBytes()), path + "!" + entry.getName());
                }
            }
        }
    }

    private void add(Map<String, JavaClass> classes, JavaClass javaClass, String source) {
        JavaClass previous = classes.putIfAbsent(javaClass.name(), javaClass);
        if (previous != null) {
            throw new CompileException("Duplicate class " + javaClass.name().replace('/', '.') + " in " + source);
        }
    }
}

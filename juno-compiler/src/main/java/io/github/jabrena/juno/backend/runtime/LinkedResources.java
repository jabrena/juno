package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Classpath resources requested by {@code Class.getResourceAsStream}, embedded as read-only board streams. */
public final class LinkedResources {
    private static final int BYTES_PER_LINE = 32;

    private final List<Resource> resources;

    private LinkedResources(List<Resource> resources) {
        this.resources = List.copyOf(resources);
    }

    /** Finds every absolute resource path used by the optimized program and reads it from the closed-world classpath. */
    public static LinkedResources read(List<Path> classPath, IrProgram program) {
        List<String> paths = new ArrayList<>();
        for (IrMethod method : program.methods()) {
            for (IrBasicBlock block : method.blocks()) {
                for (IrInstruction instruction : block.instructions()) {
                    if (instruction instanceof IrInstruction.IntrinsicCall call
                            && call.intrinsic() == Intrinsic.CLASS_GET_RESOURCE_AS_STREAM) {
                        paths.add(call.literalArguments().getFirst());
                    }
                }
            }
        }
        return new LinkedResources(paths.stream().distinct().sorted().map(path -> new Resource(path,
                bytes(classPath, path))).toList());
    }

    /** The C++ flash tables and callbacks used by the board-side {@code InputStream} route. */
    public String source() {
        if (resources.isEmpty()) {
            return "";
        }
        StringBuilder source = new StringBuilder(estimateSize());
        source.append("""

                // Classpath resources linked by Class.getResourceAsStream.
                struct JunoClasspathResource {
                  const uint8_t* bytes;
                  int32_t size;
                  int32_t position;
                };
                extern "C" void juno_input_stream_register(int32_t handle,
                    int32_t (*available)(int32_t), int32_t (*read)(int32_t),
                    int32_t (*readBytes)(int32_t, uint8_t*, int32_t, int32_t));
                """);
        for (int index = 0; index < resources.size(); index++) {
            byte[] bytes = resources.get(index).bytes();
            source.append("static const uint8_t juno_resource_").append(index).append("[")
                    .append(bytes.length).append("] = {");
            for (int byteIndex = 0; byteIndex < bytes.length; byteIndex++) {
                source.append(byteIndex % BYTES_PER_LINE == 0 ? "\n  " : " ")
                        .append(bytes[byteIndex] & 0xFF).append(',');
            }
            source.append("\n};\n");
        }
        source.append("static JunoClasspathResource juno_resources[] = {\n");
        for (int index = 0; index < resources.size(); index++) {
            source.append("  {juno_resource_").append(index).append(", ")
                    .append(resources.get(index).bytes().length).append(", 0},\n");
        }
        source.append("};\n\n");
        source.append("static int32_t juno_resource_index(int32_t handle) {\n")
                .append("  int32_t index = handle - 1;\n")
                .append("  return index >= 0 && index < ").append(resources.size()).append(" ? index : -1;\n")
                .append("}\n\n");
        source.append("static int32_t juno_resource_available(int32_t handle) {\n")
                .append("  int32_t index = juno_resource_index(handle);\n")
                .append("  return index < 0 ? 0 : juno_resources[index].size - juno_resources[index].position;\n")
                .append("}\n\n");
        source.append("static int32_t juno_resource_read_bytes(int32_t handle, uint8_t* buffer, int32_t offset, int32_t length) {\n")
                .append("  int32_t index = juno_resource_index(handle);\n")
                .append("  if (index < 0 || buffer == nullptr || offset < 0 || length < 0) return -1;\n")
                .append("  JunoClasspathResource& resource = juno_resources[index];\n")
                .append("  int32_t count = resource.size - resource.position;\n")
                .append("  if (count > length) count = length;\n")
                .append("  if (count == 0) return 0;\n")
                .append("  memcpy(buffer + offset, resource.bytes + resource.position, static_cast<size_t>(count));\n")
                .append("  resource.position += count;\n")
                .append("  return count;\n")
                .append("}\n\n");
        source.append("static int32_t juno_resource_read(int32_t handle) {\n")
                .append("  uint8_t value = 0;\n")
                .append("  return juno_resource_read_bytes(handle, &value, 0, 1) == 1 ? value : -1;\n")
                .append("}\n\n");
        source.append("extern \"C\" int32_t juno_resource_open(const char* path) {\n")
                .append("  if (path == nullptr) return 0;\n");
        for (int index = 0; index < resources.size(); index++) {
            source.append("  if (strcmp(path, ").append(cString(resources.get(index).path())).append(") == 0) {\n")
                    .append("    juno_resources[").append(index).append("].position = 0;\n")
                    .append("    juno_input_stream_register(").append(index + 1)
                    .append(", juno_resource_available, juno_resource_read, juno_resource_read_bytes);\n")
                    .append("    return ").append(index + 1).append(";\n  }\n");
        }
        return source.append("  return 0;\n}\n").toString();
    }

    private int estimateSize() {
        return resources.stream().mapToInt(resource -> resource.bytes().length * 4).sum() + 4096;
    }

    private static byte[] bytes(List<Path> classPath, String path) {
        if (!path.startsWith("/") || path.contains("..")) {
            throw new CompileException("Class.getResourceAsStream requires an absolute resource path without '..': " + path);
        }
        String resource = path.substring(1);
        for (Path entry : classPath) {
            try {
                if (Files.isDirectory(entry)) {
                    Path root = entry.toAbsolutePath().normalize();
                    Path candidate = root.resolve(resource).normalize();
                    if (candidate.startsWith(root) && Files.isRegularFile(candidate)) {
                        return Files.readAllBytes(candidate);
                    }
                } else if (entry.toString().endsWith(".jar")) {
                    try (JarFile jar = new JarFile(entry.toFile())) {
                        var found = jar.getJarEntry(resource);
                        if (found != null) {
                            try (InputStream input = jar.getInputStream(found)) {
                                return input.readAllBytes();
                            }
                        }
                    }
                }
            } catch (IOException exception) {
                throw new CompileException("Could not read classpath resource " + path + " from " + entry + ": "
                        + exception.getMessage());
            }
        }
        throw new CompileException("Classpath resource not found for Class.getResourceAsStream: " + path);
    }

    private static String cString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class Resource {
        private final String path;
        private final byte[] bytes;

        private Resource(String path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }

        private String path() {
            return path;
        }

        private byte[] bytes() {
            return bytes;
        }
    }
}

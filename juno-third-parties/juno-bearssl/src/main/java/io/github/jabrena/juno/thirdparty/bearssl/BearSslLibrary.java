package io.github.jabrena.juno.thirdparty.bearssl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * BearSSL 0.6 (MIT, https://bearssl.org/), unmodified, as an Arduino library: the TLS server for boards whose core has
 * none, such as the UNO Q. The sources travel inside this jar; {@link #installInto} copies them out for
 * {@code arduino-cli compile --library}.
 */
public final class BearSslLibrary {
    /** The library's folder name, and the name {@code arduino-cli} reports for it. */
    public static final String NAME = "JunoBearSSL";

    private static final String ROOT = "arduino-libraries/" + NAME;
    private static final String MARKER = "library.properties";

    private final ClassLoader loader;

    public BearSslLibrary() {
        this(BearSslLibrary.class.getClassLoader());
    }

    BearSslLibrary(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Copies the library into {@code directory/JunoBearSSL} and returns that folder. A copy whose
     * {@code library.properties} already matches is kept, so repeated builds skip the copy; the marker is written last,
     * so an interrupted copy is redone.
     */
    public Path installInto(Path directory) throws IOException {
        Path target = directory.resolve(NAME);
        URL marker = loader.getResource(ROOT + "/" + MARKER);
        if (marker == null) {
            throw new IOException("The " + NAME + " sources are missing from the classpath");
        }
        byte[] properties;
        try (InputStream input = marker.openStream()) {
            properties = input.readAllBytes();
        }
        Path installedMarker = target.resolve(MARKER);
        if (Files.isRegularFile(installedMarker) && Arrays.equals(Files.readAllBytes(installedMarker), properties)) {
            return target;
        }
        URI location = uri(marker);
        if ("jar".equals(location.getScheme())) {
            copyFromJar(location, target);
        } else {
            copyTree(Objects.requireNonNull(Path.of(location).getParent(), "library root"), target);
        }
        return target;
    }

    private void copyFromJar(URI location, Path target) throws IOException {
        try {
            try (FileSystem jar = FileSystems.newFileSystem(location, Map.of())) {
                copyTree(jar.getPath(ROOT), target);
            }
        } catch (FileSystemAlreadyExistsException alreadyOpen) {
            // Someone else in this class loader opened the jar; use theirs and leave it open.
            copyTree(FileSystems.getFileSystem(location).getPath(ROOT), target);
        }
    }

    private void copyTree(Path source, Path target) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(source)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        Path marker = null;
        for (Path file : files) {
            String relative = source.relativize(file).toString();
            if (relative.equals(MARKER)) {
                marker = file;
            } else {
                copy(file, target.resolve(relative));
            }
        }
        if (marker != null) {
            copy(marker, target.resolve(MARKER));
        }
    }

    private void copy(Path file, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private URI uri(URL resource) throws IOException {
        try {
            return resource.toURI();
        } catch (URISyntaxException exception) {
            throw new IOException("Cannot locate " + resource, exception);
        }
    }
}

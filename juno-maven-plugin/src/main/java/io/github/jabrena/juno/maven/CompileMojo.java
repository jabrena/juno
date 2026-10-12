package io.github.jabrena.juno.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Generates a complete Arduino sketch: the assembly, its C++ runtime shim and the {@code .ino} wrapper. */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public final class CompileMojo extends AbstractJunoMojo {
    @Override
    void run() {
        compileSketch();
    }
}

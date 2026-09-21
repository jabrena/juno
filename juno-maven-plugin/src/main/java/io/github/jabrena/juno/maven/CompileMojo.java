package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.CompileException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Generates a complete Arduino sketch with the selected ASM or C++ backend. */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public final class CompileMojo extends AbstractJunoMojo {
    @Override
    public void execute() throws MojoFailureException {
        try {
            compileSketch();
        } catch (CompileException | ArduinoCliException exception) {
            throw failure(exception);
        }
    }
}

package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.CompileException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Generates the sketch and compiles it with Arduino CLI without touching a physical board. */
@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public final class VerifyMojo extends AbstractJunoMojo {
    @Override
    public void execute() throws MojoFailureException {
        try {
            CompiledSketch sketch = compileSketch();
            verifySketch(arduinoCli(), sketch);
        } catch (CompileException | ArduinoCliException exception) {
            throw failure(exception);
        }
    }
}

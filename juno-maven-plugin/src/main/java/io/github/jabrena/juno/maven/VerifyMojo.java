package io.github.jabrena.juno.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Generates the sketch and compiles it with Arduino CLI without touching a physical board. */
@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public final class VerifyMojo extends AbstractJunoMojo {
    @Override
    void run() {
        verifySketch(arduinoCli(), compileSketch());
    }
}

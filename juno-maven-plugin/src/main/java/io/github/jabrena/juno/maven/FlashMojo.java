package io.github.jabrena.juno.maven;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Generates and verifies the sketch, discovers the board port, and flashes it with Arduino CLI. */
@Mojo(name = "flash", requiresDependencyResolution = ResolutionScope.COMPILE)
public final class FlashMojo extends AbstractJunoMojo {
    @Override
    void run() {
        CompiledSketch sketch = compileSketch();
        ArduinoCli cli = arduinoCli();
        verifySketch(cli, sketch);
        String port = resolvePort(cli, sketch.fqbn());
        getLog().info("Flashing Arduino sketch");
        cli.upload(sketch.fqbn(), port, sketch.directory());
    }
}

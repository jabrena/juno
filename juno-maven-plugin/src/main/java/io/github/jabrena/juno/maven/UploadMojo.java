package io.github.jabrena.juno.maven;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Generates and verifies the sketch, discovers the board port, and uploads it with Arduino CLI. */
@Mojo(name = "upload", requiresDependencyResolution = ResolutionScope.COMPILE)
public final class UploadMojo extends AbstractJunoMojo {
    @Override
    void run() {
        CompiledSketch sketch = compileSketch();
        ArduinoCli cli = arduinoCli();
        verifySketch(cli, sketch);
        String port = resolvePort(cli, sketch.fqbn());
        getLog().info("Uploading Arduino sketch");
        cli.upload(sketch.fqbn(), port, sketch.directory());
    }
}

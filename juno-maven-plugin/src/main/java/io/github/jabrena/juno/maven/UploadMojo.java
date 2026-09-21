package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.CompileException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Generates and verifies the sketch, discovers the board port, and uploads it with Arduino CLI. */
@Mojo(name = "upload", requiresDependencyResolution = ResolutionScope.COMPILE)
public final class UploadMojo extends AbstractJunoMojo {
    @Override
    public void execute() throws MojoFailureException {
        try {
            CompiledSketch sketch = compileSketch();
            ArduinoCli cli = arduinoCli();
            verifySketch(cli, sketch);
            String port = resolvePort(cli, sketch.fqbn());
            getLog().info("Uploading Arduino sketch");
            cli.upload(sketch.fqbn(), port, sketch.directory());
        } catch (CompileException | ArduinoCliException exception) {
            throw failure(exception);
        }
    }
}

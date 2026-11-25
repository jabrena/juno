package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.CompilationReport;
import io.github.jabrena.juno.CompilationRequest;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.analysis.ConfigSuggestionFormatter;
import io.github.jabrena.juno.analysis.RuntimeConfigurationFormatter;
import io.github.jabrena.juno.analysis.RuntimeRiskReportFormatter;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A goal that first compiles the configured entry point into an Arduino sketch. */
abstract class AbstractJunoMojo extends AbstractArduinoMojo {
    private static final long RETRY_DELAY_MILLIS = 4000L;
    private final RuntimeConfigurationFormatter configurationFormatter = new RuntimeConfigurationFormatter();
    private final RuntimeRiskReportFormatter riskFormatter = new RuntimeRiskReportFormatter();
    private final ConfigSuggestionFormatter suggestionFormatter = new ConfigSuggestionFormatter();

    /**
     * Fully qualified Java entry-point class: the one program to compile. There is no default, so a project with several
     * programs always says which one; when it is missing the goal fails with how to pass it.
     */
    @Parameter(property = "juno.main")
    private @Nullable String mainClass;

    /** Maven compile classpath used as Juno's closed-world classpath. */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private @Nullable List<String> classpathElements;

    /** Directory under which one Arduino sketch directory is created per entry point. */
    @Parameter(property = "juno.outputDirectory", defaultValue = "${project.build.directory}/juno", required = true)
    private File outputDirectory;

    /**
     * Have the generated garbage collector print one Serial line per collection (arena bytes used
     * before/after), visible via {@code juno:monitor}. Off by default: costs no extra
     * flash/RAM/time when disabled, since the print statements aren't emitted at all.
     */
    @Parameter(property = "juno.gcLog", defaultValue = "false")
    private boolean gcLog;

    /**
     * Target board (e.g. {@code arduino-uno-r4-wifi}, {@code arduino-nano-esp32}), required when the entry point has no
     * {@code @Board} annotation or declares more than one board; the compiler fails rather than picking one silently.
     * Ignored when the entry point declares a single board.
     */
    @Parameter(property = "juno.board")
    private @Nullable String board;

    /**
     * Size of the garbage-collected arena, in the JVM's {@code -Xmx} syntax ({@code 49152}, {@code 48k},
     * {@code 1m}); a multiple of 8. Defaults to the board's own arena (8 KB on most boards, 1 KB on the AVR boards); the Maven build log prints the effective size and its maximum. Every {@code new} array, string and object
     * lives in the arena, so a program that keeps large data sets in RAM (for example, tables loaded from a file at
     * startup) needs a bigger one; the compiler's startup estimate suggests a size when the default is too small.
     */
    @Parameter(property = "juno.Xmx")
    private @Nullable String arenaSize;

    /**
     * Stack of each carrier, the stack virtual threads run on (on AVR boards, of each extra task), in the JVM's
     * {@code -Xss} syntax; a multiple of 8. It must fit the deepest virtual thread. Defaults to the board's own size (512 bytes on the AVR boards, 2048 on the UNO R4 WiFi, 4096 on the Pico 2 W, 8192 on the ESP32 boards and the Portenta H7). Each board also has a maximum; a larger value fails the build with the maximum named.
     */
    @Parameter(property = "juno.Xss")
    private @Nullable String threadStackSize;

    /**
     * Compatibility override for pipelines whose emulator cannot run both cores. Absent by default, so dual-core
     * boards use both carriers; set {@code -Djuno.carriers=1} only for such constrained test environments.
     */
    @Parameter(property = "juno.carriers")
    private @Nullable String carrierCount;

    /**
     * The JEV1 decision model that {@code DecisionModel.begin()} links into the firmware, so {@code juno:flash} uploads
     * the program and its model together (for example
     * {@code -Djuno.dm.model=../juno-ai/juno-models/src/main/resources/dm/TIC_TAC_TOE.JEV}). A relative path is resolved
     * against the project's directory. Required only by a program that calls {@code begin()}; the build fails without
     * it, and a program that loads its model with {@code begin(SdCard, String)} needs none.
     */
    @Parameter(property = "juno.dm.model")
    private @Nullable File decisionModel;

    /**
     * The JLM1 language model that {@code LanguageModel.begin()} links into the firmware, so {@code juno:flash} uploads
     * the program and its model together (for example
     * {@code -Djuno.lm.model=../juno-ai/juno-lm/src/main/resources/lm/JUNO.BIN}, the model {@code juno-lm} trains from the Juno
     * facts). A relative path is resolved against the project's directory. Required only by a program that calls
     * {@code begin()}; the build fails without it, and a program that loads its model with
     * {@code begin(SdCard, String)} needs none.
     */
    @Parameter(property = "juno.lm.model")
    private @Nullable File languageModel;

    /**
     * The program this goal compiles when no {@code juno.main} is given. Most goals have none, since a project's
     * programs are its own; a goal that talks to a program of the plugin's (such as {@code juno:secret}) ships it.
     */
    Optional<BundledProgram> defaultProgram() {
        return Optional.empty();
    }

    final CompiledSketch compileSketch() {
        Path output = outputDirectory.toPath().toAbsolutePath().normalize();
        Optional<String> configuredMain = configured(mainClass);
        Optional<BundledProgram> bundled = configuredMain.isPresent() ? Optional.empty() : defaultProgram();
        String entryPoint = configuredMain.or(() -> bundled.map(BundledProgram::mainClass))
                .orElseThrow(() -> new ArduinoCliException(
                        "Missing required Juno entry point: choose the program to compile with -Djuno.main=<class> "
                                + "(e.g. -Djuno.main=com.example.Main) or configure <mainClass>"));
        List<Path> classpath;
        if (bundled.isPresent()) {
            getLog().info("No -Djuno.main given: using the plugin's own " + entryPoint);
            classpath = bundled.get().classpath(output.resolve("bundled-classes"));
        } else {
            if (classpathElements == null || classpathElements.isEmpty()) {
                throw new ArduinoCliException("Maven compile classpath is empty");
            }
            classpath = classpathElements.stream().map(Path::of).toList();
        }
        SketchLayout layout = new SketchLayout(output, entryPoint);

        RuntimeConfig runtimeConfig = RuntimeConfig.of(Optional.ofNullable(arenaSize),
                Optional.ofNullable(threadStackSize), Optional.ofNullable(carrierCount));
        CompilationResult result = new JunoCompiler().compileTo(
                new CompilationRequest(classpath, entryPoint, gcLog, configured(board), runtimeConfig,
                        Optional.ofNullable(decisionModel).map(File::toPath),
                        Optional.ofNullable(languageModel).map(File::toPath)),
                layout.assembly(), layout.runtimeShim());
        result.sketchWrapper().writeTo(layout.wrapper());

        CompilationReport report = result.report();
        String targetFqbn = targetFqbn(result.fqbn());
        if (!targetFqbn.equals(result.fqbn())) {
            getLog().warn("Configured FQBN " + targetFqbn + " overrides @Board target " + result.fqbn());
        }
        getLog().info("Generated " + layout.assembly() + ", " + layout.runtimeShim() + ", and " + layout.wrapper()
                + " for " + report.board().displayName() + " with the ASM backend (fqbn " + targetFqbn + ")");
        configurationFormatter.format(report.runtimeConfig()).forEach(getLog()::info);
        riskFormatter.format(report.runtimeRisks()).forEach(getLog()::info);
        suggestionFormatter.format(report.configSuggestions()).forEach(getLog()::info);
        Optional<Coprocessor> coprocessor = result.coprocessor().map(image -> {
            writeSketch(layout.coprocessorSketch(), image.sketch());
            String fqbn = targetFqbn + image.fqbnOptions();
            getLog().info("Generated " + layout.coprocessorSketch() + ", the image of the board's second core (fqbn "
                    + fqbn + ")");
            return new Coprocessor(layout.coprocessorDirectory(), fqbn);
        });
        return new CompiledSketch(layout.directory(), targetFqbn, report.usesPicoSecureFirmware(), coprocessor);
    }

    private void writeSketch(Path file, String source) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, source);
        } catch (IOException e) {
            throw new ArduinoCliException("Cannot write " + file + ": " + e.getMessage());
        }
    }

    final void verifySketch(ArduinoCli cli, CompiledSketch sketch) {
        sketch.coprocessor().ifPresent(coprocessor -> {
            getLog().info("Compiling the second core's image with arduino-cli");
            cli.compile(coprocessor.fqbn(), coprocessor.directory());
        });
        getLog().info("Compiling ASM Arduino sketch with arduino-cli");
        if (!sketch.picoSecureFirmware()) {
            cli.compile(sketch.fqbn(), sketch.directory());
            return;
        }
        cli.compileForPicoSecureFirmware(sketch.fqbn(), sketch.directory(), sketch.buildDirectory());
        Path uf2 = sketch.buildDirectory().resolve(sketch.directory().getFileName() + ".ino.uf2");
        new PicoSecureFirmwareInstaller().install(uf2);
        getLog().info("Installed Juno's TrustZone secure firmware in the boot slot of " + uf2);
    }

    /**
     * Uploads a sketch {@link #verifySketch} built. A Pico 2 W program with the secure firmware is uploaded from its own
     * build directory, where the firmware was installed into the UF2: Arduino CLI's default location holds no UF2 for
     * it, and a plain upload would reset the board into BOOTSEL and then fail, leaving it there.
     */
    final void upload(ArduinoCli cli, CompiledSketch sketch, String port) {
        if (sketch.coprocessor().isPresent()) {
            Coprocessor coprocessor = sketch.coprocessor().get();
            // The board stays in its bootloader after the first image, so the old program never runs between the two
            // uploads: an old image that crashes would take the USB port down before the second upload.
            getLog().info("Flashing the second core's image");
            retryingOnce(() -> cli.uploadStaying(coprocessor.fqbn(), port, coprocessor.directory(), dfuSerial()));
            getLog().info("Flashing the program image");
            retryingOnce(() -> cli.upload(sketch.fqbn(), port, sketch.directory(), dfuSerial()));
            return;
        }
        if (sketch.picoSecureFirmware()) {
            cli.upload(sketch.fqbn(), port, sketch.directory(), sketch.buildDirectory(), dfuSerial());
        } else {
            cli.upload(sketch.fqbn(), port, sketch.directory(), dfuSerial());
        }
    }

    /**
     * The first DFU upload after a running program often fails before the board has re-enumerated in DFU mode; the
     * same upload a few seconds later succeeds.
     */
    private void retryingOnce(Runnable upload) {
        try {
            upload.run();
        } catch (ArduinoCliException e) {
            getLog().warn("Upload failed, retrying once: " + e.getMessage());
            try {
                Thread.sleep(RETRY_DELAY_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw e;
            }
            upload.run();
        }
    }

    /**
     * A generated sketch directory and the FQBN to build it for. {@code picoSecureFirmware} marks a Pico 2 W program
     * that uses the TrustZone secret store: it builds into {@link #buildDirectory()}, where the secure firmware is
     * installed into its UF2, and flashes from there. {@code coprocessor} is the image of the board's second core,
     * which is built and flashed first.
     */
    record CompiledSketch(Path directory, String fqbn, boolean picoSecureFirmware, Optional<Coprocessor> coprocessor) {
        CompiledSketch(Path directory, String fqbn) {
            this(directory, fqbn, false, Optional.empty());
        }

        CompiledSketch(Path directory, String fqbn, boolean picoSecureFirmware) {
            this(directory, fqbn, picoSecureFirmware, Optional.empty());
        }

        Path buildDirectory() {
            return directory.resolve("build");
        }
    }

    /** The sketch of the board's second core and the FQBN that selects that core. */
    record Coprocessor(Path directory, String fqbn) {
    }

    /**
     * Where one entry point's sketch goes: {@code <output>/<Main>Asm/}, holding {@code <Main>.S},
     * {@code <Main>Shim.cpp} and the {@code <Main>Asm.ino} wrapper Arduino CLI requires to match the directory.
     */
    private record SketchLayout(Path outputDirectory, String mainClass) {
        String simpleName() {
            return mainClass.substring(mainClass.lastIndexOf('.') + 1);
        }

        Path directory() {
            return outputDirectory.resolve(simpleName() + "Asm");
        }

        Path assembly() {
            return directory().resolve(simpleName() + ".S");
        }

        Path runtimeShim() {
            return directory().resolve(simpleName() + "Shim.cpp");
        }

        Path wrapper() {
            return directory().resolve(simpleName() + "Asm.ino");
        }

        Path coprocessorDirectory() {
            return outputDirectory.resolve(simpleName() + "AsmM4");
        }

        Path coprocessorSketch() {
            return coprocessorDirectory().resolve(simpleName() + "AsmM4.ino");
        }
    }
}

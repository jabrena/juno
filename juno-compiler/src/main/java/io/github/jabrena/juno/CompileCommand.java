package io.github.jabrena.juno;

import io.github.jabrena.juno.analysis.ConfigSuggestionFormatter;
import io.github.jabrena.juno.analysis.RuntimeRiskReportFormatter;
import io.github.jabrena.juno.board.Board;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * {@code juno compile}: generates the {@code .S} assembly, the {@code Shim.cpp} runtime and the {@code .ino}
 * wrapper side by side, then prints the runtime-risk findings.
 *
 * @param assemblyOutput the generated {@code .S} file; the shim and wrapper are written next to it
 */
record CompileCommand(ProgramSelection program, Path assemblyOutput, boolean gcLog, boolean showMetrics)
        implements CliCommand {

    static CompileCommand parse(CommandLineArguments arguments) {
        ProgramSelection.Parser program = new ProgramSelection.Parser();
        Optional<Path> output = Optional.empty();
        boolean gcLog = false;
        boolean showMetrics = false;
        while (arguments.hasNext()) {
            String option = arguments.next();
            if (program.accept(option, arguments)) {
                continue;
            }
            switch (option) {
                case "--output", "-o" -> output = Optional.of(Path.of(arguments.valueFor(option)));
                case "--gc-log" -> gcLog = true;
                case "--metrics" -> showMetrics = true;
                default -> throw arguments.unknownOption(option);
            }
        }
        ProgramSelection selection = program.build();
        return new CompileCommand(selection, output.orElseGet(selection::defaultAssemblyOutput), gcLog, showMetrics);
    }

    @Override
    public void run(PrintStream out) {
        SketchFiles files = new SketchFiles(assemblyOutput);
        CompilationResult result = new JunoCompiler().compileTo(new CompilationRequest(program.classPath(),
                program.mainClass(), gcLog, program.board()), files.assembly(), files.runtimeShim());
        result.sketchWrapper().writeTo(files.wrapper());

        Board board = result.report().board();
        out.println("Generated " + files.assembly() + ", " + files.runtimeShim() + ", and " + files.wrapper()
                + " for " + board.displayName() + " (fqbn " + board.fqbn() + ")");
        out.println();
        new RuntimeRiskReportFormatter().format(result.report().runtimeRisks()).forEach(out::println);
        new ConfigSuggestionFormatter().format(result.report().configSuggestions()).forEach(out::println);
        if (showMetrics) {
            out.println();
            result.metrics().summary().forEach(out::println);
        }
    }

}

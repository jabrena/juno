package io.github.jabrena.juno;

import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.Program;

import java.io.PrintStream;

/** {@code juno inspect}: prints what the compiler sees, optionally with each method's control flow graph and IR. */
record InspectCommand(ProgramSelection program, boolean showIr, boolean showCfg) implements CliCommand {

    static InspectCommand parse(CommandLineArguments arguments) {
        ProgramSelection.Parser program = new ProgramSelection.Parser();
        boolean showIr = false;
        boolean showCfg = false;
        while (arguments.hasNext()) {
            String option = arguments.next();
            if (program.accept(option, arguments)) {
                continue;
            }
            switch (option) {
                case "--ir" -> showIr = true;
                case "--cfg" -> showCfg = true;
                default -> throw arguments.unknownOption(option);
            }
        }
        return new InspectCommand(program.build(), showIr, showCfg);
    }

    @Override
    public void run(PrintStream out) {
        CompilationPipeline pipeline = new CompilationPipeline();
        Program linked = pipeline.link(program.classPath(), program.mainClass(), program.board());
        IrProgram lowered = pipeline.lower(linked);
        IrProgram optimized = pipeline.optimize(lowered);
        CompilationReport report = CompilationReport.from(linked, lowered, optimized);

        out.println("Entry point: " + report.entryPoint().displayName());
        out.println("Board: " + report.board().displayName() + " (fqbn " + report.board().fqbn() + ")");
        out.println("Reachable methods: " + report.reachableMethods());
        out.println("IR basic blocks: " + report.irBlocks());
        out.println("Intrinsics used: " + (report.intrinsics().isEmpty() ? "(none)" : report.intrinsics()));
        if (showCfg) {
            printControlFlowGraphs(out, linked);
        }
        if (showIr) {
            printIr(out, optimized);
        }
    }

    private void printControlFlowGraphs(PrintStream out, Program program) {
        out.println();
        out.println("Control flow graphs:");
        for (LinkedMethod linked : program.methods()) {
            out.println("  " + linked.method().reference().displayName());
            for (BasicBlock block : linked.controlFlowGraph().blocks()) {
                out.println("    block " + block.start() + " -> " + block.terminator());
            }
        }
    }

    private void printIr(PrintStream out, IrProgram program) {
        out.println();
        out.println("Juno IR:");
        for (IrMethod method : program.methods()) {
            out.println("  " + method.reference().displayName());
            for (IrBasicBlock block : method.blocks()) {
                out.println("    block " + block.start() + ":");
                for (IrInstruction instruction : block.instructions()) {
                    out.println("      " + instruction);
                }
                out.println("      " + block.terminator());
            }
        }
    }
}

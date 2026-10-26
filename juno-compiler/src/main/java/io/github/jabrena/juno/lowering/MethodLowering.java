package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.jabrena.juno.bytecode.Opcodes.CHECKCAST;
import static io.github.jabrena.juno.bytecode.Opcodes.GETSTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEDYNAMIC;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEINTERFACE;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEVIRTUAL;
import static io.github.jabrena.juno.bytecode.Opcodes.MONITORENTER;
import static io.github.jabrena.juno.bytecode.Opcodes.MONITOREXIT;
import static io.github.jabrena.juno.bytecode.Opcodes.NEW;
import static io.github.jabrena.juno.bytecode.Opcodes.PUTFIELD;

/**
 * Lowers one method: walks its basic blocks and hands each instruction to the lowering its {@link OpcodeTable}
 * registers for the opcode. Every lowering shares one {@link MethodContext}.
 */
final class MethodLowering {
    private final MethodContext context;
    private final OpcodeTable table = new OpcodeTable();

    MethodLowering(ProgramContext program, LinkedMethod method) {
        this.context = new MethodContext(program, method);
        CallLowering calls = new CallLowering(context);
        ObjectCreationLowering objects = new ObjectCreationLowering(context, calls);
        LambdaLowering lambdas = new LambdaLowering(context, calls);

        List.of(new ConstantLowering(context), new LocalVariableLowering(context), new ArrayLowering(context),
                new StackShuffleLowering(context.stack()), new ArithmeticLowering(context),
                new ConversionLowering(context), new ControlFlowLowering(context))
                .forEach(group -> group.registerOn(table));
        table.on(GETSTATIC, PUTFIELD, new FieldLowering(context));
        table.on(INVOKEVIRTUAL, INVOKEINTERFACE,
                new CallGuard(context, new InvokeLowering(context, calls, objects, lambdas)));
        table.on(INVOKEDYNAMIC, new InvokeDynamicLowering(context, lambdas, new StringConcatLowering(context, calls)));
        table.on(NEW, objects);
        // References are untyped words and javac has already proved the cast, so checkcast generates no code.
        table.on(CHECKCAST, instruction -> { });
        table.on(MONITORENTER, MONITOREXIT, new MonitorLowering(context));
    }

    IrMethod lower() {
        LinkedMethod method = context.method();
        Map<Integer, Integer> entryDepths = new StackDepths(method).entryDepths();
        // A flash table's <clinit> initializer is straight-line code with no net stack effect, so dropping it
        // leaves every block's entry depth unchanged; its getstatic reads become ConstantTableRef instead.
        Set<Integer> tableInitializer = context.program().constantTables()
                .initializerOffsets(method.method().reference());
        boolean firstBlock = true;
        for (BasicBlock block : method.controlFlowGraph().blocks()) {
            context.enterBlock(block);
            context.code().startBlock(block.start());
            context.stack().startBlock(entryDepths.getOrDefault(block.start(), 0));
            if (firstBlock) {
                context.enterSynchronized();
                firstBlock = false;
            }
            block.instructions().stream()
                    .filter(instruction -> !tableInitializer.contains(instruction.offset()))
                    .forEach(table::lower);
            context.code().finishBlock(
                    () -> new IrTerminator.Jump(((Terminator.Fallthrough) block.terminator()).target()));
        }
        return IrMethod.withInferredValues(method.method().reference(), method.method().isStatic(),
                method.method().maxLocals() + method.method().maxStack(), context.values().count(),
                List.copyOf(context.arrays()), context.finishBlocks());
    }
}

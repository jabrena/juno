package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ConstantPool;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.ArrayDeclaration;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.github.jabrena.juno.lowering.IrBlockWriter.PROPAGATE_BLOCK;

/**
 * The state one method's lowering shares between its opcode-group lowerers: the program facts, the method being
 * lowered, its operand stack, and the IR being written.
 */
final class MethodContext {
    private final ProgramContext program;
    private final LinkedMethod method;
    private final FreshValues values = new FreshValues();
    private final IrBlockWriter code = new IrBlockWriter();
    private final ValueTracking facts = new ValueTracking();
    private final OperandStack stack;
    private final LocalSlots locals;
    private final ExceptionRouting exceptions;
    private final List<ArrayDeclaration> arrays = new ArrayList<>();
    private Optional<Value> synchronizedMonitor = Optional.empty();
    private BasicBlock currentBlock;

    MethodContext(ProgramContext program, LinkedMethod method) {
        this.program = program;
        this.method = method;
        this.stack = new OperandStack(method.method().maxLocals(), values, code, facts);
        this.locals = new LocalSlots(method);
        this.exceptions = new ExceptionRouting(method, program.classes());
        this.currentBlock = method.controlFlowGraph().entry();
    }

    ProgramContext program() {
        return program;
    }

    Map<String, JavaClass> classes() {
        return program.classes();
    }

    LinkedMethod method() {
        return method;
    }

    ConstantPool constantPool() {
        return method.owner().constantPool();
    }

    FreshValues values() {
        return values;
    }

    IrBlockWriter code() {
        return code;
    }

    ValueTracking facts() {
        return facts;
    }

    OperandStack stack() {
        return stack;
    }

    LocalSlots locals() {
        return locals;
    }

    ExceptionRouting exceptions() {
        return exceptions;
    }

    List<ArrayDeclaration> arrays() {
        return arrays;
    }

    void emit(IrInstruction instruction) {
        code.emit(instruction);
    }

    /** Emits the implicit monitor acquisition for an instance method declared {@code synchronized}. */
    void enterSynchronized() {
        if (!method.method().isSynchronized()) {
            return;
        }
        Value monitor = values.int32();
        emit(new IrInstruction.LoadLocal(monitor, 0));
        emit(new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.MONITOR_ENTER, Optional.of(monitor),
                List.of(), List.of()));
        synchronizedMonitor = Optional.of(monitor);
    }

    /** Emits the implicit monitor release before a normal or locally handled exceptional return. */
    void exitSynchronized() {
        synchronizedMonitor.ifPresent(monitor -> emit(new IrInstruction.IntrinsicCall(Optional.empty(),
                Intrinsic.MONITOR_EXIT, Optional.of(monitor), List.of(), List.of())));
    }

    /** Adds the implicit monitor release to the shared exceptional-unwind return block, when one exists. */
    List<IrBasicBlock> finishBlocks() {
        List<IrBasicBlock> blocks = code.blocks();
        if (synchronizedMonitor.isEmpty()) {
            return blocks;
        }
        IrInstruction release = new IrInstruction.IntrinsicCall(Optional.empty(), Intrinsic.MONITOR_EXIT,
                synchronizedMonitor, List.of(), List.of());
        return blocks.stream().map(block -> block.start() == PROPAGATE_BLOCK
                ? new IrBasicBlock(block.start(), append(block.instructions(), release), block.terminator())
                : block).toList();
    }

    private List<IrInstruction> append(List<IrInstruction> instructions, IrInstruction extra) {
        List<IrInstruction> result = new ArrayList<>(instructions.size() + 1);
        result.addAll(instructions);
        result.add(extra);
        return List.copyOf(result);
    }

    /** The bytecode block being lowered. */
    BasicBlock currentBlock() {
        return currentBlock;
    }

    void enterBlock(BasicBlock block) {
        currentBlock = Objects.requireNonNull(block);
    }

    /** {@code "Owner.method(descriptor) at bytecode offset N"}, the prefix of a compile error about {@code site}. */
    String at(Instruction site) {
        return method.method().reference().displayName() + " at bytecode offset " + site.offset();
    }
}

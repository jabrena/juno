package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ExceptionHandler;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.InvokeDynamicRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static io.github.jabrena.juno.bytecode.Opcodes.CHECKCAST;
import static io.github.jabrena.juno.bytecode.Opcodes.GETFIELD;
import static io.github.jabrena.juno.bytecode.Opcodes.GETSTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEDYNAMIC;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEINTERFACE;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESPECIAL;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEVIRTUAL;
import static io.github.jabrena.juno.bytecode.Opcodes.MULTIANEWARRAY;
import static io.github.jabrena.juno.bytecode.Opcodes.PUTFIELD;
import static io.github.jabrena.juno.bytecode.Opcodes.PUTSTATIC;

/**
 * Each block's operand-stack depth on entry, found by walking one method's control flow graph from its entry block
 * (depth 0) and every handler (depth 1: just the caught exception) and adding up each block's net effect. The JVM
 * verifier guarantees every predecessor of a block agrees on that depth, so the first visit of a block is final.
 */
final class StackDepths {
    private static final Map<Integer, Integer> FIXED_DELTAS = Map.ofEntries(
            Map.entry(0, 0), Map.entry(1, 1), Map.entry(2, 1), Map.entry(3, 1), Map.entry(4, 1), Map.entry(5, 1),
            Map.entry(6, 1), Map.entry(7, 1),
            Map.entry(8, 1), Map.entry(9, 2), Map.entry(10, 2), Map.entry(11, 1), Map.entry(12, 1), Map.entry(13, 1),
            Map.entry(14, 2), Map.entry(15, 2),
            Map.entry(16, 1), Map.entry(17, 1), Map.entry(18, 1), Map.entry(19, 1), Map.entry(20, 2), Map.entry(21, 1),
            Map.entry(22, 2), Map.entry(23, 1),
            Map.entry(24, 2), Map.entry(25, 1), Map.entry(26, 1), Map.entry(27, 1), Map.entry(28, 1), Map.entry(29, 1),
            Map.entry(30, 2), Map.entry(31, 2),
            Map.entry(32, 2), Map.entry(33, 2), Map.entry(34, 1), Map.entry(35, 1), Map.entry(36, 1), Map.entry(37, 1),
            Map.entry(38, 2), Map.entry(39, 2),
            Map.entry(40, 2), Map.entry(41, 2), Map.entry(42, 1), Map.entry(43, 1), Map.entry(44, 1), Map.entry(45, 1),
            Map.entry(46, -1), Map.entry(47, 0),
            Map.entry(48, -1), Map.entry(49, 0), Map.entry(50, -1), Map.entry(51, -1), Map.entry(52, -1),
            Map.entry(53, -1), Map.entry(54, -1), Map.entry(55, -2),
            Map.entry(56, -1), Map.entry(57, -2), Map.entry(58, -1), Map.entry(59, -1), Map.entry(60, -1),
            Map.entry(61, -1), Map.entry(62, -1), Map.entry(63, -2),
            Map.entry(64, -2), Map.entry(65, -2), Map.entry(66, -2), Map.entry(67, -1), Map.entry(68, -1),
            Map.entry(69, -1), Map.entry(70, -1), Map.entry(71, -2),
            Map.entry(72, -2), Map.entry(73, -2), Map.entry(74, -2), Map.entry(75, -1), Map.entry(76, -1),
            Map.entry(77, -1), Map.entry(78, -1), Map.entry(79, -3),
            Map.entry(80, -4), Map.entry(81, -3), Map.entry(82, -4), Map.entry(83, -3), Map.entry(84, -3),
            Map.entry(85, -3), Map.entry(86, -3), Map.entry(87, -1),
            Map.entry(88, -2), Map.entry(89, 1), Map.entry(96, -1), Map.entry(97, -2), Map.entry(98, -1),
            Map.entry(99, -2), Map.entry(100, -1), Map.entry(101, -2),
            Map.entry(102, -1), Map.entry(103, -2), Map.entry(104, -1), Map.entry(105, -2), Map.entry(106, -1),
            Map.entry(107, -2), Map.entry(108, -1), Map.entry(109, -2),
            Map.entry(110, -1), Map.entry(111, -2), Map.entry(112, -1), Map.entry(113, -2), Map.entry(114, -1),
            Map.entry(115, -2), Map.entry(116, 0), Map.entry(117, 0),
            Map.entry(118, 0), Map.entry(119, 0), Map.entry(120, -1), Map.entry(121, -1), Map.entry(122, -1),
            Map.entry(123, -1), Map.entry(124, -1), Map.entry(125, -1),
            Map.entry(126, -1), Map.entry(127, -2), Map.entry(128, -1), Map.entry(129, -2), Map.entry(130, -1),
            Map.entry(131, -2), Map.entry(132, 0), Map.entry(133, 1),
            Map.entry(134, 0), Map.entry(135, 1), Map.entry(136, -1), Map.entry(137, -1), Map.entry(138, 0),
            Map.entry(139, 0), Map.entry(140, 1), Map.entry(141, 1),
            Map.entry(142, -1), Map.entry(143, 0), Map.entry(144, -1), Map.entry(145, 0), Map.entry(146, 0),
            Map.entry(147, 0), Map.entry(148, -3), Map.entry(149, -1),
            Map.entry(150, -1), Map.entry(151, -3), Map.entry(152, -3), Map.entry(153, -1), Map.entry(154, -1),
            Map.entry(155, -1), Map.entry(156, -1), Map.entry(157, -1),
            Map.entry(158, -1), Map.entry(159, -2), Map.entry(160, -2), Map.entry(161, -2), Map.entry(162, -2),
            Map.entry(163, -2), Map.entry(164, -2), Map.entry(165, -2),
            Map.entry(166, -2), Map.entry(167, 0), Map.entry(170, -1), Map.entry(171, -1), Map.entry(172, -1),
            Map.entry(173, -2), Map.entry(174, -1), Map.entry(175, -2),
            Map.entry(176, -1), Map.entry(177, 0), Map.entry(187, 1), Map.entry(188, 0), Map.entry(189, 0),
            Map.entry(190, 0), Map.entry(191, -1), Map.entry(194, -1), Map.entry(195, -1), Map.entry(198, -1),
            Map.entry(199, -1));

    private final LinkedMethod method;

    StackDepths(LinkedMethod method) {
        this.method = method;
    }

    /** Entry depth by block start offset, for every block reachable from the entry or a handler. */
    Map<Integer, Integer> entryDepths() {
        ControlFlowGraph cfg = method.controlFlowGraph();
        Map<Integer, Integer> entryDepth = new HashMap<>();
        Deque<Integer> work = new ArrayDeque<>();
        int entryStart = cfg.entry().start();
        entryDepth.put(entryStart, 0);
        work.add(entryStart);
        // A handler always starts with just the caught exception on the stack, even when no explicit
        // throw reaches it (its range may hold only calls, whose exceptions Juno doesn't propagate).
        for (ExceptionHandler handler : method.method().exceptionHandlers()) {
            if (entryDepth.putIfAbsent(handler.handlerPc(), 1) == null) {
                work.add(handler.handlerPc());
            }
        }
        while (!work.isEmpty()) {
            int blockStart = work.removeFirst();
            BasicBlock block = cfg.blockAt(blockStart).orElseThrow();
            int depth = Objects.requireNonNull(entryDepth.get(blockStart))
                    + block.instructions().stream().mapToInt(this::delta).sum();
            if (block.terminator() instanceof Terminator.Throw) {
                continue;
            }
            for (int successor : successors(block.terminator())) {
                Integer existing = entryDepth.get(successor);
                if (existing == null) {
                    entryDepth.put(successor, depth);
                    work.add(successor);
                } else if (existing != depth) {
                    throw new CompileException(method.method().reference().displayName()
                            + ": inconsistent operand-stack depth entering block at offset " + successor
                            + " (" + existing + " from an earlier predecessor, " + depth
                            + " from block at offset " + blockStart + ")");
                }
            }
        }
        return entryDepth;
    }

    /** Net stack slots {@code instruction} pushes (positive) or pops (negative). */
    private int delta(Instruction instruction) {
        int opcode = instruction.opcode();
        Integer fixed = FIXED_DELTAS.get(opcode);
        if (fixed != null) {
            return fixed;
        }
        return switch (opcode) {
            case GETSTATIC, PUTSTATIC -> {
                int slots = fieldSlots(instruction);
                yield opcode == GETSTATIC ? slots : -slots;
            }
            case GETFIELD, PUTFIELD -> {
                int slots = fieldSlots(instruction);
                yield opcode == GETFIELD ? slots - 1 : -slots - 1;
            }
            case CHECKCAST -> 0;
            case MULTIANEWARRAY -> 1 - instruction.operandB();
            case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE -> {
                MethodRef called = method.owner().constantPool().methodRef(instruction.operandA());
                int receiver = opcode == INVOKESTATIC ? 0 : 1;
                yield callDelta(Descriptor.parse(called.descriptor())) - receiver;
            }
            case INVOKEDYNAMIC -> {
                InvokeDynamicRef dynamic = method.owner().constantPool().invokeDynamic(instruction.operandA());
                yield callDelta(Descriptor.parse(dynamic.descriptor()));
            }
            default -> throw new CompileException("Juno IR lowering does not support opcode " + opcode);
        };
    }

    private int fieldSlots(Instruction instruction) {
        FieldRef field = method.owner().constantPool().fieldRef(instruction.operandA());
        return Descriptor.jvmSlots(field.descriptor());
    }

    private int callDelta(Descriptor descriptor) {
        int consumed = descriptor.parameters().stream().mapToInt(Descriptor::jvmSlots).sum();
        return Descriptor.jvmSlots(descriptor.returnType()) - consumed;
    }

    private List<Integer> successors(Terminator terminator) {
        return switch (terminator) {
            case Terminator.Jump jump -> List.of(jump.target());
            case Terminator.Branch branch -> List.of(branch.trueTarget(), branch.falseTarget());
            case Terminator.Fallthrough fallthrough -> List.of(fallthrough.target());
            case Terminator.Return ignored -> List.of();
            case Terminator.Throw thrown -> thrown.handlers();
            case Terminator.Switch switched ->
                    Stream.concat(switched.targets().stream(), Stream.of(switched.defaultTarget())).toList();
        };
    }
}

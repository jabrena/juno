package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.classfile.InvokeDynamicRef;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LinkedMethod;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Compile-time-constant lookahead and the per-opcode operand-stack effect
 * table, split out of {@link BytecodeToIr}.
 */
final class ConstantAndStackSupport {
    private ConstantAndStackSupport() {
    }

    static final Map<Integer, Integer> SIMPLE_STACK_DELTA = Map.ofEntries(
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

    static @Nullable Integer constantPushValue(Instruction instruction, LinkedMethod linked) {
        return switch (instruction.opcode()) {
            case 2 -> -1;
            case 3, 4, 5, 6, 7, 8 -> instruction.opcode() - 3;
            case 16, 17 -> instruction.operandA();
            case 18, 19 -> linked.owner().constantPool().integer(instruction.operandA());
            default -> null;
        };
    }

    static ConstPop popKnownConstant(List<IrInstruction> instructions, int stackBase, int depth,
            LinkedMethod linked, Instruction site, ValueTracking tracking) {
        int newDepth = depth - 1;
        int slot = stackBase + newDepth;
        if (instructions.size() >= 2
                && instructions.get(instructions.size() - 1) instanceof IrInstruction.StoreLocal store
                && store.local() == slot
                && instructions.get(instructions.size() - 2) instanceof IrInstruction.Const constant
                && constant.target().equals(store.value())) {
            instructions.remove(instructions.size() - 1);
            instructions.remove(instructions.size() - 1);
            tracking.clearStackSlot(slot);
            return new ConstPop(constant.value(), newDepth);
        }
        throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                + site.offset() + ": array length must be a compile-time constant");
    }

    static int stackDelta(LinkedMethod linked, Instruction instruction) {
        int opcode = instruction.opcode();
        Integer simple = SIMPLE_STACK_DELTA.get(opcode);
        if (simple != null) {
            return simple;
        }
        return switch (opcode) {
            case 178, 179 -> {
                FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
                int slots = Descriptor.jvmSlots(field.descriptor());
                yield opcode == 178 ? slots : -slots;
            }
            case 180, 181 -> {
                FieldRef field = linked.owner().constantPool().fieldRef(instruction.operandA());
                int slots = Descriptor.jvmSlots(field.descriptor());
                yield opcode == 180 ? slots - 1 : -slots - 1;
            }
            case 192 -> 0;
            case 197 -> 1 - instruction.operandB();
            case 182, 183, 184, 185 -> {
                MethodRef called = linked.owner().constantPool().methodRef(instruction.operandA());
                Descriptor descriptor = Descriptor.parse(called.descriptor());
                int consumed = descriptor.parameters().stream().mapToInt(Descriptor::jvmSlots).sum()
                        + (opcode == 182 || opcode == 183 || opcode == 185 ? 1 : 0);
                int produced = Descriptor.jvmSlots(descriptor.returnType());
                yield produced - consumed;
            }
            case 186 -> {
                InvokeDynamicRef dynamic = linked.owner().constantPool().invokeDynamic(instruction.operandA());
                Descriptor descriptor = Descriptor.parse(dynamic.descriptor());
                int consumed = descriptor.parameters().stream().mapToInt(Descriptor::jvmSlots).sum();
                yield Descriptor.jvmSlots(descriptor.returnType()) - consumed;
            }
            default -> throw new CompileException("Juno IR lowering does not support opcode " + opcode);
        };
    }
}

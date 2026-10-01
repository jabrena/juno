package io.github.jabrena.juno.bytecode;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.JavaMethod;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/** Decodes the intentionally small JVM bytecode subset supported by Juno v0.1. */
public final class BytecodeDecoder {
    // Opcodes grouped by encoding shape (operand count/width), not by what they do: every opcode in
    // a group decodes identically, so classifying by group collapses what would otherwise be a huge
    // number of switch case labels (one per opcode) into a single set-membership check.
    private static final BitSet ONE_BYTE_NO_OPERAND = bitSetOf(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 26, 27, 28, 29,
            30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
            50, 51, 52, 53, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74,
            75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 96, 97, 98, 99, 100,
            101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120,
            121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 133, 134, 135, 136, 137, 138, 139, 140, 141,
            142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 172, 173, 174, 175, 176, 177, 190, 191);
    private static final BitSet UNSIGNED_BYTE_OPERAND = bitSetOf(
            18, 21, 22, 23, 24, 25, 54, 55, 56, 57, 58, 188);
    private static final BitSet SHORT_OPERAND = bitSetOf(
            17, 19, 20, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 178, 179,
            180, 181, 182, 183, 184, 187, 189, 198, 199);
    // The SHORT_OPERAND subset whose operand is an unsigned constant-pool/branch index rather than a
    // signed value.
    private static final BitSet UNSIGNED_SHORT_OPERAND = bitSetOf(
            19, 20, 178, 179, 180, 181, 182, 183, 184, 187, 189);

    private static final Map<Integer, String> OPCODE_NAMES = Map.ofEntries(
            Map.entry(0, "nop"), Map.entry(1, "aconst_null"), Map.entry(2, "iconst_m1"), Map.entry(3, "iconst_0"),
            Map.entry(4, "iconst_1"), Map.entry(5, "iconst_2"), Map.entry(6, "iconst_3"), Map.entry(7, "iconst_4"),
            Map.entry(8, "iconst_5"), Map.entry(9, "lconst_0"), Map.entry(10, "lconst_1"), Map.entry(11, "fconst_0"),
            Map.entry(12, "fconst_1"), Map.entry(13, "fconst_2"), Map.entry(14, "dconst_0"), Map.entry(15, "dconst_1"),
            Map.entry(16, "bipush"), Map.entry(17, "sipush"), Map.entry(18, "ldc"), Map.entry(19, "ldc_w"),
            Map.entry(20, "ldc2_w"), Map.entry(21, "iload"), Map.entry(22, "lload"), Map.entry(23, "fload"),
            Map.entry(24, "dload"), Map.entry(25, "aload"), Map.entry(26, "iload_0"), Map.entry(27, "iload_1"),
            Map.entry(28, "iload_2"), Map.entry(29, "iload_3"), Map.entry(30, "lload_0"), Map.entry(31, "lload_1"),
            Map.entry(32, "lload_2"), Map.entry(33, "lload_3"), Map.entry(34, "fload_0"), Map.entry(35, "fload_1"),
            Map.entry(36, "fload_2"), Map.entry(37, "fload_3"), Map.entry(38, "dload_0"), Map.entry(39, "dload_1"),
            Map.entry(40, "dload_2"), Map.entry(41, "dload_3"), Map.entry(42, "aload_0"), Map.entry(43, "aload_1"),
            Map.entry(44, "aload_2"), Map.entry(45, "aload_3"), Map.entry(46, "iaload"), Map.entry(47, "laload"),
            Map.entry(48, "faload"), Map.entry(49, "daload"), Map.entry(50, "aaload"), Map.entry(51, "baload"),
            Map.entry(52, "caload"), Map.entry(53, "saload"), Map.entry(54, "istore"), Map.entry(55, "lstore"),
            Map.entry(56, "fstore"), Map.entry(57, "dstore"), Map.entry(58, "astore"), Map.entry(59, "istore_0"),
            Map.entry(60, "istore_1"), Map.entry(61, "istore_2"), Map.entry(62, "istore_3"), Map.entry(63, "lstore_0"),
            Map.entry(64, "lstore_1"), Map.entry(65, "lstore_2"), Map.entry(66, "lstore_3"), Map.entry(67, "fstore_0"),
            Map.entry(68, "fstore_1"), Map.entry(69, "fstore_2"), Map.entry(70, "fstore_3"), Map.entry(71, "dstore_0"),
            Map.entry(72, "dstore_1"), Map.entry(73, "dstore_2"), Map.entry(74, "dstore_3"), Map.entry(75, "astore_0"),
            Map.entry(76, "astore_1"), Map.entry(77, "astore_2"), Map.entry(78, "astore_3"), Map.entry(79, "iastore"),
            Map.entry(80, "lastore"), Map.entry(81, "fastore"), Map.entry(82, "dastore"), Map.entry(83, "aastore"),
            Map.entry(84, "bastore"), Map.entry(85, "castore"), Map.entry(86, "sastore"), Map.entry(87, "pop"),
            Map.entry(88, "pop2"), Map.entry(89, "dup"), Map.entry(96, "iadd"), Map.entry(97, "ladd"),
            Map.entry(98, "fadd"), Map.entry(99, "dadd"), Map.entry(100, "isub"), Map.entry(101, "lsub"),
            Map.entry(102, "fsub"), Map.entry(103, "dsub"), Map.entry(104, "imul"), Map.entry(105, "lmul"),
            Map.entry(106, "fmul"), Map.entry(107, "dmul"), Map.entry(108, "idiv"), Map.entry(109, "ldiv"),
            Map.entry(110, "fdiv"), Map.entry(111, "ddiv"), Map.entry(112, "irem"), Map.entry(113, "lrem"),
            Map.entry(114, "frem"), Map.entry(115, "drem"), Map.entry(116, "ineg"), Map.entry(117, "lneg"),
            Map.entry(118, "fneg"), Map.entry(119, "dneg"), Map.entry(120, "ishl"), Map.entry(121, "lshl"),
            Map.entry(122, "ishr"), Map.entry(123, "lshr"), Map.entry(124, "iushr"), Map.entry(125, "lushr"),
            Map.entry(126, "iand"), Map.entry(127, "land"), Map.entry(128, "ior"), Map.entry(129, "lor"),
            Map.entry(130, "ixor"), Map.entry(131, "lxor"), Map.entry(132, "iinc"), Map.entry(133, "i2l"),
            Map.entry(134, "i2f"), Map.entry(135, "i2d"), Map.entry(136, "l2i"), Map.entry(137, "l2f"),
            Map.entry(138, "l2d"), Map.entry(139, "f2i"), Map.entry(140, "f2l"), Map.entry(141, "f2d"),
            Map.entry(142, "d2i"), Map.entry(143, "d2l"), Map.entry(144, "d2f"), Map.entry(145, "i2b"),
            Map.entry(146, "i2c"), Map.entry(147, "i2s"), Map.entry(148, "lcmp"), Map.entry(149, "fcmpl"),
            Map.entry(150, "fcmpg"), Map.entry(151, "dcmpl"), Map.entry(152, "dcmpg"), Map.entry(153, "ifeq"),
            Map.entry(154, "ifne"), Map.entry(155, "iflt"), Map.entry(156, "ifge"), Map.entry(157, "ifgt"),
            Map.entry(158, "ifle"), Map.entry(159, "if_icmpeq"), Map.entry(160, "if_icmpne"), Map.entry(161, "if_icmplt"),
            Map.entry(162, "if_icmpge"), Map.entry(163, "if_icmpgt"), Map.entry(164, "if_icmple"), Map.entry(165, "if_acmpeq"),
            Map.entry(166, "if_acmpne"), Map.entry(167, "goto"), Map.entry(170, "tableswitch"), Map.entry(171, "lookupswitch"),
            Map.entry(172, "ireturn"), Map.entry(173, "lreturn"), Map.entry(174, "freturn"), Map.entry(175, "dreturn"),
            Map.entry(176, "areturn"), Map.entry(177, "return"), Map.entry(178, "getstatic"), Map.entry(179, "putstatic"),
            Map.entry(180, "getfield"), Map.entry(181, "putfield"), Map.entry(182, "invokevirtual"), Map.entry(183, "invokespecial"),
            Map.entry(184, "invokestatic"), Map.entry(185, "invokeinterface"), Map.entry(187, "new"),
            Map.entry(188, "newarray"), Map.entry(189, "anewarray"),
            Map.entry(190, "arraylength"), Map.entry(191, "athrow"), Map.entry(196, "wide"), Map.entry(197, "multianewarray"),
            Map.entry(198, "ifnull"), Map.entry(199, "ifnonnull"));

    public List<Instruction> decode(JavaMethod method) {
        if (method.code() == null) {
            throw error(method, 0, "method has no bytecode");
        }
        byte[] code = method.code();
        List<Instruction> instructions = new ArrayList<>();
        for (int offset = 0; offset < code.length; ) {
            int opcode = unsigned(code[offset]);
            DecodedInstruction decoded = decodeOne(code, offset, opcode, method);
            instructions.add(new Instruction(offset, decoded.opcode(), decoded.operandA(), decoded.operandB(),
                    decoded.switchKeys(), decoded.switchOffsets()));
            offset += decoded.length();
        }
        return List.copyOf(instructions);
    }

    private DecodedInstruction decodeOne(byte[] code, int offset, int opcode, JavaMethod method) {
        if (ONE_BYTE_NO_OPERAND.get(opcode)) {
            return DecodedInstruction.of(opcode, 1);
        }
        if (opcode == 16) {
            require(code, offset, 2, method);
            return DecodedInstruction.of(opcode, 2, code[offset + 1], 0);
        }
        if (UNSIGNED_BYTE_OPERAND.get(opcode)) {
            require(code, offset, 2, method);
            return DecodedInstruction.of(opcode, 2, unsigned(code[offset + 1]), 0);
        }
        if (SHORT_OPERAND.get(opcode)) {
            require(code, offset, 3, method);
            int operandA = UNSIGNED_SHORT_OPERAND.get(opcode)
                    ? unsignedShort(code, offset + 1)
                    : signedShort(code, offset + 1);
            return DecodedInstruction.of(opcode, 3, operandA, 0);
        }
        if (opcode == 132) {
            require(code, offset, 3, method);
            return DecodedInstruction.of(opcode, 3, unsigned(code[offset + 1]), code[offset + 2]);
        }
        if (opcode == 185) {
            require(code, offset, 5, method);
            if (code[offset + 4] != 0) {
                throw error(method, offset, "invokeinterface reserved byte must be zero");
            }
            int count = unsigned(code[offset + 3]);
            if (count == 0) {
                throw error(method, offset, "invokeinterface argument count must be non-zero");
            }
            return DecodedInstruction.of(opcode, 5, unsignedShort(code, offset + 1), count);
        }
        if (opcode == 170 || opcode == 171) {
            return SwitchInstructionDecoder.decode(code, offset, opcode, method, this);
        }
        if (opcode == 196) {
            return decodeWide(code, offset, method);
        }
        if (opcode == 197) {
            require(code, offset, 4, method);
            return DecodedInstruction.of(opcode, 4, unsignedShort(code, offset + 1), unsigned(code[offset + 3]));
        }
        throw error(method, offset, "unsupported opcode 0x" + String.format("%02x", opcode) + " (" + opcodeName(opcode) + ")");
    }

    // wide: the next instruction takes a 16-bit local index (and, for iinc, a 16-bit increment); it
    // decodes as that instruction, the same as its narrow form.
    private DecodedInstruction decodeWide(byte[] code, int offset, JavaMethod method) {
        require(code, offset, 2, method);
        int widened = unsigned(code[offset + 1]);
        if (widened == 132) {
            require(code, offset, 6, method);
            return DecodedInstruction.of(widened, 6, unsignedShort(code, offset + 2), signedShort(code, offset + 4));
        }
        if ((widened >= 21 && widened <= 25) || (widened >= 54 && widened <= 58)) {
            require(code, offset, 4, method);
            return DecodedInstruction.of(widened, 4, unsignedShort(code, offset + 2), 0);
        }
        throw error(method, offset, "unsupported wide opcode 0x" + String.format("%02x", widened)
                + " (" + opcodeName(widened) + ")");
    }

    public static String opcodeName(int opcode) {
        return OPCODE_NAMES.getOrDefault(opcode, "unknown");
    }

    void require(byte[] code, int offset, int length, JavaMethod method) {
        if (offset + length > code.length) {
            throw error(method, offset, "truncated instruction");
        }
    }

    CompileException error(JavaMethod method, int offset, String detail) {
        return new CompileException(method.reference().displayName() + " at bytecode offset " + offset + ": " + detail);
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private int unsignedShort(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) << 8 | unsigned(bytes[offset + 1]);
    }

    private int signedShort(byte[] bytes, int offset) {
        return (short) unsignedShort(bytes, offset);
    }

    static int signedInt(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) << 24 | Byte.toUnsignedInt(bytes[offset + 1]) << 16
                | Byte.toUnsignedInt(bytes[offset + 2]) << 8 | Byte.toUnsignedInt(bytes[offset + 3]);
    }

    private static BitSet bitSetOf(int... codes) {
        BitSet set = new BitSet();
        for (int code : codes) {
            set.set(code);
        }
        return set;
    }
}

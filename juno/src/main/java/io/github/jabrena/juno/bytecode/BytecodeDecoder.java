package io.github.jabrena.juno.bytecode;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.JavaMethod;

import java.util.ArrayList;
import java.util.List;

/** Decodes the intentionally small JVM bytecode subset supported by Juno v0.1. */
public final class BytecodeDecoder {
    public List<Instruction> decode(JavaMethod method) {
        if (method.code() == null) {
            throw error(method, 0, "method has no bytecode");
        }
        byte[] code = method.code();
        List<Instruction> instructions = new ArrayList<>();
        for (int offset = 0; offset < code.length; ) {
            int opcode = unsigned(code[offset]);
            int length;
            int operandA = 0;
            int operandB = 0;
            switch (opcode) {
                case 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45,
                        46, 47, 48, 49, 51, 52, 53, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72,
                        73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 84, 85, 86, 87, 88, 89, 96, 97, 98, 99, 100, 101,
                        102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117,
                        118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 133, 134,
                        135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150,
                        151, 152, 172, 173, 174, 175, 176, 177,
                        190 -> length = 1;
                case 16 -> {
                    require(code, offset, 2, method);
                    operandA = code[offset + 1];
                    length = 2;
                }
                case 18, 21, 22, 23, 24, 25, 54, 55, 56, 57, 58, 188 -> {
                    require(code, offset, 2, method);
                    operandA = unsigned(code[offset + 1]);
                    length = 2;
                }
                case 17, 19, 20, 153, 154, 155, 156, 157, 158,
                        159, 160, 161, 162, 163, 164, 165, 166, 167, 178, 179, 180, 181, 182, 183, 184, 187 -> {
                    require(code, offset, 3, method);
                    operandA = signedShort(code, offset + 1);
                    if (opcode == 19 || opcode == 20 || opcode == 178 || opcode == 179 || opcode == 180 || opcode == 181
                            || opcode == 182 || opcode == 183 || opcode == 184 || opcode == 187) {
                        operandA = unsignedShort(code, offset + 1);
                    }
                    length = 3;
                }
                case 132 -> {
                    require(code, offset, 3, method);
                    operandA = unsigned(code[offset + 1]);
                    operandB = code[offset + 2];
                    length = 3;
                }
                default -> throw error(method, offset,
                        "unsupported opcode 0x" + String.format("%02x", opcode) + " (" + opcodeName(opcode) + ")");
            }
            instructions.add(new Instruction(offset, opcode, operandA, operandB));
            offset += length;
        }
        return List.copyOf(instructions);
    }

    public static String opcodeName(int opcode) {
        return switch (opcode) {
            case 0 -> "nop";
            case 2 -> "iconst_m1";
            case 3, 4, 5, 6, 7, 8 -> "iconst_" + (opcode - 3);
            case 9, 10 -> "lconst_" + (opcode - 9);
            case 11, 12, 13 -> "fconst_" + (opcode - 11);
            case 14, 15 -> "dconst_" + (opcode - 14);
            case 16 -> "bipush";
            case 17 -> "sipush";
            case 18 -> "ldc";
            case 19 -> "ldc_w";
            case 20 -> "ldc2_w";
            case 21 -> "iload";
            case 22 -> "lload";
            case 23 -> "fload";
            case 24 -> "dload";
            case 26, 27, 28, 29 -> "iload_" + (opcode - 26);
            case 30, 31, 32, 33 -> "lload_" + (opcode - 30);
            case 34, 35, 36, 37 -> "fload_" + (opcode - 34);
            case 38, 39, 40, 41 -> "dload_" + (opcode - 38);
            case 25 -> "aload";
            case 42, 43, 44, 45 -> "aload_" + (opcode - 42);
            case 46 -> "iaload";
            case 47 -> "laload";
            case 48 -> "faload";
            case 49 -> "daload";
            case 51 -> "baload";
            case 52 -> "caload";
            case 53 -> "saload";
            case 54 -> "istore";
            case 55 -> "lstore";
            case 56 -> "fstore";
            case 57 -> "dstore";
            case 59, 60, 61, 62 -> "istore_" + (opcode - 59);
            case 63, 64, 65, 66 -> "lstore_" + (opcode - 63);
            case 67, 68, 69, 70 -> "fstore_" + (opcode - 67);
            case 71, 72, 73, 74 -> "dstore_" + (opcode - 71);
            case 58 -> "astore";
            case 75, 76, 77, 78 -> "astore_" + (opcode - 75);
            case 79 -> "iastore";
            case 80 -> "lastore";
            case 81 -> "fastore";
            case 82 -> "dastore";
            case 84 -> "bastore";
            case 85 -> "castore";
            case 86 -> "sastore";
            case 87 -> "pop";
            case 88 -> "pop2";
            case 89 -> "dup";
            case 96 -> "iadd";
            case 97 -> "ladd";
            case 98 -> "fadd";
            case 99 -> "dadd";
            case 100 -> "isub";
            case 101 -> "lsub";
            case 102 -> "fsub";
            case 103 -> "dsub";
            case 104 -> "imul";
            case 105 -> "lmul";
            case 106 -> "fmul";
            case 107 -> "dmul";
            case 108 -> "idiv";
            case 109 -> "ldiv";
            case 110 -> "fdiv";
            case 111 -> "ddiv";
            case 112 -> "irem";
            case 113 -> "lrem";
            case 114 -> "frem";
            case 115 -> "drem";
            case 116 -> "ineg";
            case 117 -> "lneg";
            case 118 -> "fneg";
            case 119 -> "dneg";
            case 120 -> "ishl";
            case 121 -> "lshl";
            case 122 -> "ishr";
            case 123 -> "lshr";
            case 124 -> "iushr";
            case 125 -> "lushr";
            case 126 -> "iand";
            case 127 -> "land";
            case 128 -> "ior";
            case 129 -> "lor";
            case 130 -> "ixor";
            case 131 -> "lxor";
            case 132 -> "iinc";
            case 133 -> "i2l";
            case 134 -> "i2f";
            case 135 -> "i2d";
            case 136 -> "l2i";
            case 137 -> "l2f";
            case 138 -> "l2d";
            case 139 -> "f2i";
            case 140 -> "f2l";
            case 141 -> "f2d";
            case 142 -> "d2i";
            case 143 -> "d2l";
            case 144 -> "d2f";
            case 145 -> "i2b";
            case 146 -> "i2c";
            case 147 -> "i2s";
            case 148 -> "lcmp";
            case 149 -> "fcmpl";
            case 150 -> "fcmpg";
            case 151 -> "dcmpl";
            case 152 -> "dcmpg";
            case 153 -> "ifeq";
            case 154 -> "ifne";
            case 155 -> "iflt";
            case 156 -> "ifge";
            case 157 -> "ifgt";
            case 158 -> "ifle";
            case 159 -> "if_icmpeq";
            case 160 -> "if_icmpne";
            case 161 -> "if_icmplt";
            case 162 -> "if_icmpge";
            case 163 -> "if_icmpgt";
            case 164 -> "if_icmple";
            case 165 -> "if_acmpeq";
            case 166 -> "if_acmpne";
            case 167 -> "goto";
            case 172 -> "ireturn";
            case 173 -> "lreturn";
            case 174 -> "freturn";
            case 175 -> "dreturn";
            case 176 -> "areturn";
            case 177 -> "return";
            case 178 -> "getstatic";
            case 179 -> "putstatic";
            case 180 -> "getfield";
            case 181 -> "putfield";
            case 182 -> "invokevirtual";
            case 183 -> "invokespecial";
            case 184 -> "invokestatic";
            case 187 -> "new";
            case 188 -> "newarray";
            case 190 -> "arraylength";
            default -> "unknown";
        };
    }

    private void require(byte[] code, int offset, int length, JavaMethod method) {
        if (offset + length > code.length) {
            throw error(method, offset, "truncated instruction");
        }
    }

    private CompileException error(JavaMethod method, int offset, String detail) {
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
}

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
                case 0, 2, 3, 4, 5, 6, 7, 8,
                        26, 27, 28, 29, 42, 43, 44, 45,
                        46, 59, 60, 61, 62, 75, 76, 77, 78,
                        79, 87, 89, 96, 100, 104, 108, 112, 116,
                        120, 122, 124, 126, 128, 130,
                        145, 146, 147, 172, 177, 190 -> length = 1;
                case 16 -> {
                    require(code, offset, 2, method);
                    operandA = code[offset + 1];
                    length = 2;
                }
                case 18, 21, 25, 54, 58, 188 -> {
                    require(code, offset, 2, method);
                    operandA = unsigned(code[offset + 1]);
                    length = 2;
                }
                case 17, 19, 153, 154, 155, 156, 157, 158,
                        159, 160, 161, 162, 163, 164, 167, 182, 184 -> {
                    require(code, offset, 3, method);
                    operandA = signedShort(code, offset + 1);
                    if (opcode == 19 || opcode == 182 || opcode == 184) {
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
            case 16 -> "bipush";
            case 17 -> "sipush";
            case 18 -> "ldc";
            case 19 -> "ldc_w";
            case 21 -> "iload";
            case 26, 27, 28, 29 -> "iload_" + (opcode - 26);
            case 25 -> "aload";
            case 42, 43, 44, 45 -> "aload_" + (opcode - 42);
            case 46 -> "iaload";
            case 54 -> "istore";
            case 59, 60, 61, 62 -> "istore_" + (opcode - 59);
            case 58 -> "astore";
            case 75, 76, 77, 78 -> "astore_" + (opcode - 75);
            case 79 -> "iastore";
            case 87 -> "pop";
            case 89 -> "dup";
            case 96 -> "iadd";
            case 100 -> "isub";
            case 104 -> "imul";
            case 108 -> "idiv";
            case 112 -> "irem";
            case 116 -> "ineg";
            case 120 -> "ishl";
            case 122 -> "ishr";
            case 124 -> "iushr";
            case 126 -> "iand";
            case 128 -> "ior";
            case 130 -> "ixor";
            case 132 -> "iinc";
            case 145 -> "i2b";
            case 146 -> "i2c";
            case 147 -> "i2s";
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
            case 167 -> "goto";
            case 172 -> "ireturn";
            case 177 -> "return";
            case 178 -> "getstatic";
            case 179 -> "putstatic";
            case 182 -> "invokevirtual";
            case 184 -> "invokestatic";
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

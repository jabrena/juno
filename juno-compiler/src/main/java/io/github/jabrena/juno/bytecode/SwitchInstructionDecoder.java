package io.github.jabrena.juno.bytecode;

import io.github.jabrena.juno.classfile.JavaMethod;

import java.util.ArrayList;
import java.util.List;

/** Decodes {@code tableswitch} (opcode 170) and {@code lookupswitch} (opcode 171), split out of {@link BytecodeDecoder}. */
final class SwitchInstructionDecoder {
    private SwitchInstructionDecoder() {
    }

    static DecodedInstruction decode(byte[] code, int offset, int opcode, JavaMethod method, BytecodeDecoder support) {
        int cursor = offset + 1;
        while ((cursor & 3) != 0) {
            cursor++;
        }
        support.require(code, offset, cursor - offset + (opcode == 170 ? 12 : 8), method);
        int operandA = BytecodeDecoder.signedInt(code, cursor);
        cursor += 4;

        List<Integer> keys = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        cursor = opcode == 170
                ? decodeTable(code, offset, cursor, method, support, keys, offsets)
                : decodeLookup(code, offset, cursor, method, support, keys, offsets);

        return DecodedInstruction.ofSwitch(opcode, cursor - offset, operandA, List.copyOf(keys), List.copyOf(offsets));
    }

    private static int decodeTable(byte[] code, int offset, int cursor, JavaMethod method, BytecodeDecoder support,
                                   List<Integer> keys, List<Integer> offsets) {
        int low = BytecodeDecoder.signedInt(code, cursor);
        int high = BytecodeDecoder.signedInt(code, cursor + 4);
        cursor += 8;
        if (high < low || (long) high - low > 65535) {
            throw support.error(method, offset, "invalid tableswitch range");
        }
        support.require(code, offset, cursor - offset + (high - low + 1) * 4, method);
        for (int key = low; key <= high; key++) {
            keys.add(key);
            offsets.add(BytecodeDecoder.signedInt(code, cursor));
            cursor += 4;
        }
        return cursor;
    }

    private static int decodeLookup(byte[] code, int offset, int cursor, JavaMethod method, BytecodeDecoder support,
                                    List<Integer> keys, List<Integer> offsets) {
        int pairs = BytecodeDecoder.signedInt(code, cursor);
        cursor += 4;
        if (pairs < 0 || pairs > 65535) {
            throw support.error(method, offset, "invalid lookupswitch pair count");
        }
        support.require(code, offset, cursor - offset + pairs * 8, method);
        for (int index = 0; index < pairs; index++) {
            keys.add(BytecodeDecoder.signedInt(code, cursor));
            offsets.add(BytecodeDecoder.signedInt(code, cursor + 4));
            cursor += 8;
        }
        return cursor;
    }
}

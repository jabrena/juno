package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;

/**
 * Rejects an AI Thinker ESP32-CAM program that uses both the OV2640 camera and {@code MCU.getI2c()}. Both sit on
 * GPIO26 and GPIO27: the camera's SCCB bus and the I2C bus that a breakout shares with it. Whether the installed
 * esp32-camera driver can share an already-started {@code Wire} bus is not yet verified on hardware, so the compiler
 * refuses the combination rather than emitting code that may fail on the board.
 */
final class Esp32CamBusConflicts {
    private static final String MCU = "io/github/jabrena/juno/hardware/MCU";
    private static final int INVOKEVIRTUAL = 182;
    private static final int INVOKESTATIC = 184;

    void validate(Board board, List<LinkedMethod> methods) {
        if (board != Board.AI_THINKER_ESP32_CAM) {
            return;
        }
        LinkedMethod camera = null;
        LinkedMethod i2c = null;
        for (LinkedMethod method : methods) {
            for (Instruction instruction : method.instructions()) {
                int opcode = instruction.opcode();
                if (opcode < INVOKEVIRTUAL || opcode > INVOKESTATIC) {
                    continue;
                }
                MethodRef called = method.owner().constantPool().methodRef(instruction.operandA());
                if (!called.owner().equals(MCU)) {
                    continue;
                }
                if (called.name().equals("getCamera") && camera == null) {
                    camera = method;
                } else if (called.name().equals("getI2c") && i2c == null) {
                    i2c = method;
                }
            }
        }
        if (camera != null && i2c != null) {
            throw new CompileException("MCU.getCamera() and MCU.getI2c() cannot be used in one program on "
                    + board.displayName() + ": the camera's SCCB bus and the I2C bus share GPIO26 and GPIO27, and "
                    + "sharing them is not yet verified. Camera use is in " + camera.method().reference().displayName()
                    + "; I2C use is in " + i2c.method().reference().displayName());
        }
    }
}

package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;

/**
 * Rejects a program that calls both {@code Camera.capture(byte[])} and {@code Camera.captureGrayscale}. The camera is
 * initialized once, in one pixel format: a JPEG program and a grayscale program cannot share the sensor without a
 * deinit and a fresh init at run time, which this compiler does not emit.
 */
final class CameraModeConflicts {
    private static final String CAMERA = "io/github/jabrena/juno/hardware/device/camera/Camera";
    private static final String NATIVE_CAMERA = "io/github/jabrena/juno/hardware/internal/NativeCamera";
    private static final String JPEG_CAPTURE = "capture";
    private static final String GRAYSCALE_CAPTURE = "captureGrayscale";
    private static final int FIRST_INVOKE = 182;
    private static final int LAST_INVOKE = 185;

    void validate(List<LinkedMethod> methods) {
        LinkedMethod jpeg = null;
        LinkedMethod grayscale = null;
        for (LinkedMethod method : methods) {
            for (Instruction instruction : method.instructions()) {
                int opcode = instruction.opcode();
                if (opcode < FIRST_INVOKE || opcode > LAST_INVOKE) {
                    continue;
                }
                MethodRef called = method.owner().constantPool().methodRef(instruction.operandA());
                if (!called.owner().equals(CAMERA) && !called.owner().equals(NATIVE_CAMERA)) {
                    continue;
                }
                if (called.name().equals(JPEG_CAPTURE) && jpeg == null) {
                    jpeg = method;
                } else if (called.name().equals(GRAYSCALE_CAPTURE) && grayscale == null) {
                    grayscale = method;
                }
            }
        }
        if (jpeg != null && grayscale != null) {
            throw new CompileException("Camera.capture(byte[]) and Camera.captureGrayscale(...) cannot be used in one "
                    + "program: the camera starts once in one pixel format, JPEG or grayscale. JPEG capture is in "
                    + jpeg.method().reference().displayName() + "; grayscale capture is in "
                    + grayscale.method().reference().displayName());
        }
    }
}

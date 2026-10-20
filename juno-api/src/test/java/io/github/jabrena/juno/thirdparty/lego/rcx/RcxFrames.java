package io.github.jabrena.juno.thirdparty.lego.rcx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;

import java.util.ArrayList;
import java.util.List;

/** Builds the bytes an RCX brick sends back over IR, for scripting {@link io.github.jabrena.juno.hardware.device.ir.Infrared}. */
public final class RcxFrames {
    private RcxFrames() {
    }

    /**
     * One frame: the header, each value followed by its complement, the checksum pair, then a timeout (-1) for the
     * idle line that ends it, which is how the receiver knows the frame is over.
     */
    public static List<Integer> frame(int... values) {
        List<Integer> bytes = new ArrayList<>(List.of(0x55, 0xFF, 0x00));
        int sum = 0;
        for (int value : values) {
            bytes.add(value);
            bytes.add(~value & 0xFF);
            sum += value;
        }
        bytes.add(sum & 0xFF);
        bytes.add(~sum & 0xFF);
        bytes.add(Infrared.TIMEOUT);
        return bytes;
    }

    /** The brick's acknowledgement of {@code command}: its opcode is the command's complement, followed by arguments. */
    public static List<Integer> replyTo(int command, int... arguments) {
        int[] values = new int[arguments.length + 1];
        values[0] = ~command & 0xF7;
        System.arraycopy(arguments, 0, values, 1, arguments.length);
        return frame(values);
    }
}

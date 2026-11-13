package io.github.jabrena.juno.remote.lego.rcx;

/** An output of an RCX brick, printed on the brick as A, B or C: the motor plugs in there. */
public enum RcxMotorPort {
    /** Output A. */
    A(0, RcxBrick.OUTPUT_A),
    /** Output B. */
    B(1, RcxBrick.OUTPUT_B),
    /** Output C. */
    C(2, RcxBrick.OUTPUT_C);

    private final int index;
    private final int output;

    RcxMotorPort(int index, int output) {
        this.index = index;
        this.output = output;
    }

    /** The position of this output in the brick's motor table. */
    int index() {
        return index;
    }

    /** The output's bit in the brick's protocol messages. */
    int output() {
        return output;
    }
}

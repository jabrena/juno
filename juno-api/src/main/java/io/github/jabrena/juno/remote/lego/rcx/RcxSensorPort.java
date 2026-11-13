package io.github.jabrena.juno.remote.lego.rcx;

/** A sensor input of an RCX brick, printed on the brick as 1, 2 or 3: a touch, light or rotation sensor plugs in there. */
public enum RcxSensorPort {
    /** Sensor input 1. */
    INPUT_1(RcxBrick.INPUT_1),
    /** Sensor input 2. */
    INPUT_2(RcxBrick.INPUT_2),
    /** Sensor input 3. */
    INPUT_3(RcxBrick.INPUT_3);

    private final int input;

    RcxSensorPort(int input) {
        this.input = input;
    }

    /** The input's number in the brick's protocol messages, from 0 for input 1. */
    int input() {
        return input;
    }
}

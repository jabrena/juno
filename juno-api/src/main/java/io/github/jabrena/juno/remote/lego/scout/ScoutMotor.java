package io.github.jabrena.juno.remote.lego.scout;

import io.github.jabrena.juno.remote.lego.rcx.RcxBrick;

/** One Scout motor output, controlled with the Scout-compatible global output commands. Create it through {@link RemoteScout}. */
public final class ScoutMotor {
    /** Highest power level accepted by {@link #setPower(int)} and {@link #drive(int)}. */
    public static final int MAX_POWER = 7;

    private final RcxBrick brick;
    private final int output;

    ScoutMotor(RcxBrick brick, int output) {
        this.brick = brick;
        this.output = output;
    }

    /** Runs this output forwards at its last set power level. */
    public void forward() {
        brick.setGlobalMotorDirection(output, true);
        brick.globalMotorOn(output);
    }

    /** Runs this output backwards at its last set power level. */
    public void backward() {
        brick.setGlobalMotorDirection(output, false);
        brick.globalMotorOn(output);
    }

    /** Stops this output with the motor braking. */
    public void stop() {
        brick.globalMotorOff(output);
    }

    /** Stops this output with the motor free to coast. */
    public void flt() {
        brick.globalMotorFloat(output);
    }

    /** Sets power to {@code 0..MAX_POWER}; it takes effect while the output is on. */
    public void setPower(int level) {
        brick.setMaxMotorPower(output, level);
    }

    /** Runs at {@code -MAX_POWER..MAX_POWER}; sign selects direction and zero brakes. */
    public void drive(int speed) {
        if (speed == 0) {
            stop();
            return;
        }
        setPower(speed > 0 ? speed : -speed);
        if (speed > 0) {
            forward();
        } else {
            backward();
        }
    }
}

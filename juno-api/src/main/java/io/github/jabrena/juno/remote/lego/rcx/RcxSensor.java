package io.github.jabrena.juno.remote.lego.rcx;

/**
 * One of an RCX brick's three sensor inputs, as LeJOS's {@code SensorPort}: get it from {@link RemoteRcx#getSensor1()},
 * {@link RemoteRcx#getSensor2()} or {@link RemoteRcx#getSensor3()} and hand it to the sensor plugged into it, an
 * {@link RcxTouchSensor}, {@link RcxLightSensor}, {@link RcxTemperatureSensor} or {@link RcxRotationSensor}, created
 * once, at startup. Every read is a request and a reply, about 100 ms, and needs the IR receiver module wired.
 */
public final class RcxSensor {
    private final RcxBrick brick;
    private final int input;

    RcxSensor(RcxBrick brick, int input) {
        this.brick = brick;
        this.input = input;
    }

    /** The input's raw value in the mode its sensor set, or {@code -1} if the brick did not answer. */
    public int readValue() {
        return brick.readSensor(input);
    }

    /** Sets the input's value back to zero, e.g. a rotation sensor's count. */
    public void clear() {
        brick.clearSensor(input);
    }

    boolean setTouch() {
        return brick.setTouchSensor(input);
    }

    boolean setLight() {
        return brick.setLightSensor(input);
    }

    boolean setRotation() {
        return brick.setRotationSensor(input);
    }

    boolean setTemperature() {
        return brick.setTemperatureSensor(input);
    }

    int rotationSteps() {
        return brick.rotationSteps(input);
    }

    int rotationDegrees() {
        return brick.rotationDegrees(input);
    }

    int temperatureTenths() {
        return brick.temperatureTenths(input);
    }
}

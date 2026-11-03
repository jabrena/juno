package io.github.jabrena.juno.remote.lego.pf;

/**
 * A LEGO Power Functions infrared remote (8885), which drives the motors, lights and servos plugged
 * into an IR Receiver (8884) set to one of its four channels. The commands are plain Java on top of
 * a {@link PFLink}, which frames them and sends them through an infrared emitter (HX-53 or Grove).
 *
 * <p>Each receiver has two outputs, {@link PFPort#RED} and {@link PFPort#BLUE}, and the receiver's
 * channel switch must match the {@link PFChannel} argument. Sending is blocking. Works on
 * both boards. The pulse timings follow the published protocol and have not been verified on a
 * receiver.
 *
 * <p>Create one {@link PFLink} at startup, call its {@code begin} once, and create the remote with it.
 */
public final class RemotePF {
    /** The fastest speed in either direction for {@link #setSpeed}; the PWM has seven steps. */
    public static final int MAX_SPEED = 7;

    private static final int PWM_FLOAT = 0x0;
    private static final int PWM_BRAKE = 0x8;

    private final PFLink link;

    /** A remote that sends its commands through {@code link}. */
    public RemotePF(PFLink link) {
        this.link = link;
    }

    /**
     * Runs {@code port} of the receiver on {@code channel} at {@code speed}, {@code -7..7}: positive
     * for one direction, negative for the other, {@code 0} lets it coast. Out-of-range values are clamped.
     */
    public void setSpeed(PFChannel channel, PFPort port, int speed) {
        link.sendSingle(channel.value(), port.value(), pwm(speed));
    }

    /** Actively brakes {@code port}, unlike {@code setSpeed(channel, port, 0)}, which lets it coast. */
    public void brake(PFChannel channel, PFPort port) {
        link.sendSingle(channel.value(), port.value(), PWM_BRAKE);
    }

    /**
     * Sets both outputs of the receiver on {@code channel} in one message, each {@code -7..7} as in
     * {@link #setSpeed}. It is a single command, so use it to start two motors together.
     */
    public void setSpeeds(PFChannel channel, int redSpeed, int blueSpeed) {
        link.sendCombined(channel.value(), pwm(blueSpeed), pwm(redSpeed));
    }

    /** Lets both outputs of the receiver on {@code channel} coast. */
    public void stop(PFChannel channel) {
        link.sendCombined(channel.value(), PWM_FLOAT, PWM_FLOAT);
    }

    /** Maps a speed to the protocol's PWM nibble: 1..7 forward, 15..9 backward, 0 float. */
    private static int pwm(int speed) {
        int limited = speed > MAX_SPEED ? MAX_SPEED : speed < -MAX_SPEED ? -MAX_SPEED : speed;
        return limited >= 0 ? limited : 16 + limited;
    }
}

package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.robotics.Encoder;
import io.github.jabrena.juno.robotics.RegulatedMotor;

/**
 * One side of an {@link RcxRegulatedPair}, as the {@link RegulatedMotor} contract: speed and acceleration are the
 * pair's, a rotation of this side starts a movement in which the other side stays where it is, and the tachometer is
 * this side's own. Get it from {@link RcxRegulatedPair#left()} or {@link RcxRegulatedPair#right()}.
 *
 * <p>Only regulated movements exist on the brick, so the open-loop calls ({@link #forward}, {@link #backward},
 * {@link #flt}, {@link #setPower}, {@link #getPower}) throw {@link UnsupportedOperationException}; use {@link #rotate}.
 */
public final class RcxRegulatedMotor implements RegulatedMotor, Encoder {
    private final RcxRegulatedPair pair;
    private final int side;

    RcxRegulatedMotor(RcxRegulatedPair pair, int side) {
        this.pair = pair;
        this.side = side;
    }

    /** Sets the target speed of the pair, in degrees per second; it applies to both sides. */
    @Override
    public void setSpeed(int degreesPerSecond) {
        pair.setSpeed(degreesPerSecond);
    }

    /** Sets the acceleration of the pair, in degrees per second squared; it applies to both sides. */
    @Override
    public void setAcceleration(int degreesPerSecondSquared) {
        pair.setAcceleration(degreesPerSecondSquared);
    }

    @Override
    public int speed() {
        return pair.speed();
    }

    @Override
    public void rotate(int degrees) {
        rotate(degrees, false);
    }

    @Override
    public void rotate(int degrees, boolean immediateReturn) {
        rotateThisSide(degrees, immediateReturn);
    }

    @Override
    public void rotateTo(int limitAngle) {
        rotateTo(limitAngle, false);
    }

    @Override
    public void rotateTo(int limitAngle, boolean immediateReturn) {
        rotateThisSide(limitAngle - pair.tachoCount(side), immediateReturn);
    }

    @Override
    public int tachoCount() {
        return pair.tachoCount(side);
    }

    /** The tachometer in encoder-shaft degrees, as the {@link Encoder} contract reads it; refreshes the status first. */
    @Override
    public int getTachoCount() {
        return pair.tachoCount(side);
    }

    /** Sets this side's tachometer zero to where it is now; rejected while the pair moves. */
    @Override
    public void resetTachoCount() {
        if (pair.isMoving()) {
            throw new IllegalStateException("the tachometer cannot be reset while the pair is moving");
        }
        pair.resetTachoCount(side);
    }

    /** Rotates this side by {@code degrees} while the other stays where it is: the other side's target is its position. */
    private void rotateThisSide(int degrees, boolean immediateReturn) {
        if (side == RcxRegulatedPair.LEFT) {
            pair.rotate(degrees, 0, immediateReturn);
        } else {
            pair.rotate(0, degrees, immediateReturn);
        }
    }

    @Override
    public boolean isMoving() {
        return pair.isMoving();
    }

    @Override
    public boolean isStalled() {
        return pair.isStalled();
    }

    @Override
    public void waitComplete() {
        pair.waitComplete();
    }

    /** Stops both sides of the pair, the movement being shared. */
    @Override
    public void stop() {
        pair.stop();
    }

    /** Not supported: the brick only runs finite regulated movements; use {@link #rotate}. */
    @Override
    public void forward() {
        throw unsupported();
    }

    /** Not supported: the brick only runs finite regulated movements; use {@link #rotate}. */
    @Override
    public void backward() {
        throw unsupported();
    }

    /** Not supported: the brick only runs finite regulated movements; use {@link #stop}. */
    @Override
    public void flt() {
        throw unsupported();
    }

    /** Not supported: the brick regulates speed itself, through the pair's profile. */
    @Override
    public void setPower(int power) {
        throw unsupported();
    }

    /** Not supported: the brick regulates speed itself, through the pair's profile. */
    @Override
    public int getPower() {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("a regulated RCX output takes rotate(), not open-loop power");
    }
}

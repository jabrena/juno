package io.github.jabrena.juno.remote.lego.poweredup;

/**
 * Two motors of one Powered Up hub paired into a virtual port, so one call drives both in sync, as for a two-motor
 * vehicle. Get one from {@link RemotePoweredUpHub#linkMotors}; the first motor is the one on the first port given there.
 *
 * <p>A pair belongs to the connection that created it. Once the hub disconnects, or a reconnect starts a new
 * connection, every call on the old pair throws {@link IllegalStateException}: link the motors again after connecting.
 * Create a pair once, at startup, not inside the main loop.
 */
public final class LinkedMotors {
    private final RemotePoweredUpHub hub;
    private final int virtualPort;
    private final int connection;
    private boolean unlinked;

    LinkedMotors(RemotePoweredUpHub hub, int virtualPort, int connection) {
        this.hub = hub;
        this.virtualPort = virtualPort;
        this.connection = connection;
    }

    /**
     * Drives the first motor at {@code first} and the second at {@code second}, each {@code -100..100} (clamped),
     * negative for backwards, {@code 0} to coast. Mount mirrored motors with opposite signs.
     *
     * @throws IllegalStateException if the hub has disconnected since the pair was made
     */
    public void setPower(int first, int second) {
        requireLive();
        hub.setLinkedMotorPower(virtualPort, first, second);
    }

    /**
     * Actively brakes both motors.
     *
     * @throws IllegalStateException if the hub has disconnected since the pair was made
     */
    public void brake() {
        requireLive();
        hub.brakeLinkedMotors(virtualPort);
    }

    /**
     * Takes the pair apart, so the two motors are driven separately again. The pair cannot be used afterwards.
     *
     * @throws IllegalStateException if the hub has disconnected since the pair was made
     */
    public void unlink() {
        requireLive();
        hub.unlinkMotors(virtualPort);
        unlinked = true;
    }
    private void requireLive() {
        if (unlinked) {
            throw new IllegalStateException("the motors were unlinked");
        }
        hub.requireConnection(connection);
    }
}

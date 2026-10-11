package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.net.Udp;

/** The local arena and CPU opponent used by UNO Q Solo mode, independent of display and touch input. */
final class SoloDuel {
    static final int PLAYER_SWUNG = 1;
    static final int CPU_SWUNG = 2;

    private static final int SWING_MILLIS = 700;

    final Match match = new Match();
    final Body player = new Body();
    final Body cpu = new Body();
    final Brain playerBrain;
    final Brain cpuBrain;
    final int playerBehaviour;
    final int cpuBehaviour;

    private final byte[] state = new byte[GladiatorProtocol.PACKET_SIZE];
    private int playerStrikes;
    private int cpuStrikes;
    private int playerSwungAt = -SWING_MILLIS;
    private int cpuSwungAt = -SWING_MILLIS;
    private int round;

    /** Both gladiators join at {@code now}, the clock {@link #step} is later called with. */
    SoloDuel(int playerBehaviour, int opponentBehaviour, int now) {
        this.playerBehaviour = playerBehaviour;
        cpuBehaviour = opponentBehaviour;
        playerBrain = new Brain(playerBehaviour);
        cpuBrain = new Brain(cpuBehaviour);
        Coliseum.build();
        int[] local = new int[Udp.ENDPOINT_SIZE];
        match.join(1, playerBehaviour, local, now);
        match.join(2, cpuBehaviour, local, now);
        round = match.round;
        reset(now);
    }

    /** Advances the authoritative match by one display frame and returns swing-animation flags. */
    int step(int now) {
        match.tick(now);
        if (match.round != round) {
            round = match.round;
            reset(now);
        }
        int events = 0;
        if (match.phase == Match.FIGHT) {
            sight(player, 0);
            sight(cpu, 1);
            playerBrain.see(match.sees(0), match.x[1], match.y[1], now);
            playerBrain.drive(player, now);
            if (playerBrain.strike && now - playerSwungAt >= SWING_MILLIS) {
                playerSwungAt = now;
                playerStrikes = (playerStrikes + 1) & 0xFF;
                events = PLAYER_SWUNG;
            }
            cpuBrain.see(match.sees(1), match.x[0], match.y[0], now);
            cpuBrain.drive(cpu, now);
            if (cpuBrain.strike && now - cpuSwungAt >= SWING_MILLIS) {
                cpuSwungAt = now;
                cpuStrikes = (cpuStrikes + 1) & 0xFF;
                events = events | CPU_SWUNG;
            }
        }
        int oldPlayerHealth = match.health[0];
        int oldCpuHealth = match.health[1];
        state(0, player, playerBehaviour, playerStrikes, playerBrain.mode, false, now);
        state(1, cpu, cpuBehaviour, cpuStrikes, cpuBrain.mode, false, now);
        if (match.health[0] < oldPlayerHealth) {
            playerBrain.hurt(now);
        }
        if (match.health[1] < oldCpuHealth) {
            cpuBrain.hurt(now);
        }
        return events;
    }

    void writeWorld(byte[] packet, int now) {
        match.writeWorld(packet, 0, now);
    }

    private void reset(int now) {
        player.spawn(0);
        cpu.spawn(1);
        playerBrain.reset(0, now);
        cpuBrain.reset(1, now);
    }

    private void sight(Body body, int slot) {
        int opponent = 1 - slot;
        body.opponentInSight = match.sees(slot);
        body.opponentX = match.x[opponent];
        body.opponentY = match.y[opponent];
    }

    private void state(int slot, Body body, int behaviour, int strikes, int mode, boolean human, int now) {
        GladiatorProtocol.begin(state, GladiatorProtocol.STATE, slot, slot + 1);
        state[GladiatorProtocol.STATE_ROUND] = (byte) match.round;
        state[GladiatorProtocol.STATE_BEHAVIOUR] = (byte) behaviour;
        GladiatorProtocol.writeShort(state, GladiatorProtocol.X, Math.round(body.x));
        GladiatorProtocol.writeShort(state, GladiatorProtocol.Y, Math.round(body.y));
        GladiatorProtocol.writeShort(state, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(body.angle));
        state[GladiatorProtocol.STRIKES] = (byte) strikes;
        state[GladiatorProtocol.MODE] = (byte) mode;
        state[GladiatorProtocol.HUMAN] = (byte) (human ? 1 : 0);
        match.state(slot, state, now);
    }
}

package io.github.jabrena.juno.games.gladiator.screen;

import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.games.gladiator.arena.Arms;
import io.github.jabrena.juno.games.gladiator.arena.Body;
import io.github.jabrena.juno.games.gladiator.arena.Coliseum;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.arena.Javelins;
import io.github.jabrena.juno.games.gladiator.arena.Lion;
import io.github.jabrena.juno.games.gladiator.arena.Match;
import io.github.jabrena.juno.games.gladiator.arena.Side;
import io.github.jabrena.juno.games.gladiator.gladiator.Brain;
import io.github.jabrena.juno.games.gladiator.gladiator.Senses;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;

/**
 * The local arena and CPU opponent used by UNO Q Solo mode, independent of display and touch input: a new
 * {@link Coliseum}, the authoritative {@link Match} on it, and both gladiators' bodies and brains.
 */
public final class SoloDuel {
    public static final int PLAYER_SWUNG = 1;
    public static final int CPU_SWUNG = 2;

    private final Coliseum coliseum = new Coliseum();
    private final Match match;
    private final Body player;
    private final Body cpu;
    private final Brain playerBrain;
    private final Brain cpuBrain;
    private final byte[] state = new byte[GladiatorProtocol.PACKET_SIZE];
    private int playerStrikes;
    private int cpuStrikes;
    private int playerThrows;
    private int cpuThrows;
    private int playerNets;
    private int cpuNets;
    private int playerSwungAt;
    private int cpuSwungAt;
    private int round;

    /** Both gladiators join at {@code now}, the clock {@link #step} is later called with. */
    public SoloDuel(int playerBehaviour, int opponentBehaviour, int now) {
        this(playerBehaviour, GladiatorClass.MURMILLO, opponentBehaviour, GladiatorClass.MURMILLO, now);
    }

    /** Both gladiators, each with its fighting style and class, join at {@code now}, in a newly built coliseum. */
    public SoloDuel(int playerBehaviour, int playerKind, int opponentBehaviour, int opponentKind, int now) {
        coliseum.build();
        match = new Match(coliseum);
        player = new Body(coliseum);
        cpu = new Body(coliseum);
        playerBrain = new Brain(playerBehaviour, playerKind);
        cpuBrain = new Brain(opponentBehaviour, opponentKind);
        playerSwungAt = now - Arms.swingMillis(playerKind);
        cpuSwungAt = now - Arms.swingMillis(opponentKind);
        int[] local = new int[Udp.ENDPOINT_SIZE];
        match.join(1, playerBehaviour, local, now);
        match.join(2, opponentBehaviour, local, now);
        round = match.round();
        reset(now);
    }

    public Coliseum coliseum() {
        return coliseum;
    }

    public Match match() {
        return match;
    }

    public Body player() {
        return player;
    }

    public Body cpu() {
        return cpu;
    }

    public Brain playerBrain() {
        return playerBrain;
    }

    public Brain cpuBrain() {
        return cpuBrain;
    }

    /** Advances the authoritative match by one display frame and returns swing-animation flags. */
    public int step(int now) {
        match.tick(now);
        if (match.round() != round) {
            round = match.round();
            reset(now);
        }
        int events = 0;
        if (match.phase() == Match.FIGHT) {
            sight(player, 0);
            sight(cpu, 1);
            if (think(playerBrain, player, 0, now)) {
                events = PLAYER_SWUNG;
            }
            if (think(cpuBrain, cpu, 1, now)) {
                events = events | CPU_SWUNG;
            }
        }
        int oldPlayerHealth = match.side(0).health();
        int oldCpuHealth = match.side(1).health();
        state(0, player, playerStrikes, playerThrows, playerNets, playerBrain, now);
        state(1, cpu, cpuStrikes, cpuThrows, cpuNets, cpuBrain, now);
        if (match.side(0).health() < oldPlayerHealth) {
            playerBrain.hurt(now);
        }
        if (match.side(1).health() < oldCpuHealth) {
            cpuBrain.hurt(now);
        }
        return events;
    }

    /** One gladiator's move this frame, and its counters; whether it swung its weapon. */
    private boolean think(Brain brain, Body body, int slot, int now) {
        Side other = match.side(1 - slot);
        arm(brain, slot, now);
        brain.see(match.sees(slot), other.x(), other.y(), now);
        brain.drive(body, now);
        Senses senses = brain.senses();
        boolean swung = brain.strike()
                && now - (slot == 0 ? playerSwungAt : cpuSwungAt) >= Arms.swingMillis(brain.kind());
        if (slot == 0) {
            playerThrows = senses.throwJavelin() ? (playerThrows + 1) & 0xFF : playerThrows;
            playerNets = senses.castNet() ? (playerNets + 1) & 0xFF : playerNets;
            playerSwungAt = swung ? now : playerSwungAt;
            playerStrikes = swung ? (playerStrikes + 1) & 0xFF : playerStrikes;
        } else {
            cpuThrows = senses.throwJavelin() ? (cpuThrows + 1) & 0xFF : cpuThrows;
            cpuNets = senses.castNet() ? (cpuNets + 1) & 0xFF : cpuNets;
            cpuSwungAt = swung ? now : cpuSwungAt;
            cpuStrikes = swung ? (cpuStrikes + 1) & 0xFF : cpuStrikes;
        }
        return swung;
    }

    public void writeWorld(byte[] packet, int now) {
        match.writeWorld(packet, 0, now);
    }

    private void reset(int now) {
        player.spawn(0);
        cpu.spawn(1);
        playerBrain.reset(0, now);
        cpuBrain.reset(1, now);
    }

    /** What the gladiator in {@code slot} knows of javelins, nets and the lion: the arena tells it all of that. */
    private void arm(Brain brain, int slot, int now) {
        Senses senses = brain.senses();
        Javelins javelins = match.javelins();
        Lion lion = match.lion();
        senses.arm(javelins.held(slot) >= 0, match.side(slot).rooted(now), match.side(slot).netReady());
        senses.lion(lion.active(), lion.x(), lion.y());
        for (int j = 0; j < Javelins.COUNT; j++) {
            senses.javelin(j, javelins.state(j) == Javelins.GROUND, javelins.x(j), javelins.y(j));
        }
    }

    private void sight(Body body, int slot) {
        Side opponent = match.side(1 - slot);
        body.seeOpponent(match.sees(slot), opponent.x(), opponent.y());
    }

    private void state(int slot, Body body, int strikes, int throwsMade, int nets, Brain brain, int now) {
        GladiatorProtocol.begin(state, GladiatorProtocol.STATE, slot, slot + 1);
        state[GladiatorProtocol.STATE_ROUND] = (byte) match.round();
        state[GladiatorProtocol.STATE_BEHAVIOUR] = (byte) brain.behaviour();
        state[GladiatorProtocol.STATE_KIND] = (byte) brain.kind();
        state[GladiatorProtocol.NETS] = (byte) nets;
        GladiatorProtocol.writeShort(state, GladiatorProtocol.X, Math.round(body.x()));
        GladiatorProtocol.writeShort(state, GladiatorProtocol.Y, Math.round(body.y()));
        GladiatorProtocol.writeShort(state, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(body.angle()));
        state[GladiatorProtocol.STRIKES] = (byte) strikes;
        state[GladiatorProtocol.MODE] = (byte) brain.mode();
        state[GladiatorProtocol.THROWS] = (byte) throwsMade;
        match.state(slot, state, now);
    }
}

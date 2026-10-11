package io.github.jabrena.juno.games.gladiator.arena;

import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;

/**
 * The map packet: the {@link Coliseum} the arena built, sent once to each gladiator so its board can draw and steer
 * in the same arena. The layout is the protocol's, in {@link GladiatorProtocol}.
 */
public final class ColiseumMap {
    private ColiseumMap() {
    }

    /** Writes the {@code coliseum} into {@code packet} for the gladiator in {@code slot}. */
    public static void write(byte[] packet, int slot, int nodeId, Coliseum coliseum) {
        GladiatorProtocol.begin(packet, GladiatorProtocol.MAP, slot, nodeId);
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.RADIUS_X, coliseum.radiusX());
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.RADIUS_Y, coliseum.radiusY());
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.WALL_HEIGHT, coliseum.wallHeight());
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.PILLAR_RADIUS, coliseum.pillarRadius());
        GladiatorProtocol.writeShort(packet, GladiatorProtocol.PILLAR_HEIGHT, coliseum.pillarHeight());
        packet[GladiatorProtocol.PILLAR_COUNT] = (byte) coliseum.pillars();
        for (int p = 0; p < coliseum.pillars(); p++) {
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.PILLARS + 4 * p, coliseum.pillarX(p));
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.PILLARS + 4 * p + 2, coliseum.pillarY(p));
        }
    }

    /** Loads the {@code coliseum} from a map packet; false, leaving it unchanged, when the packet is malformed. */
    public static boolean read(byte[] packet, Coliseum coliseum) {
        int count = GladiatorProtocol.unsigned(packet, GladiatorProtocol.PILLAR_COUNT);
        int radiusX = GladiatorProtocol.readShort(packet, GladiatorProtocol.RADIUS_X);
        int radiusY = GladiatorProtocol.readShort(packet, GladiatorProtocol.RADIUS_Y);
        if (count > Coliseum.MAX_PILLARS || radiusX <= Coliseum.BODY * 2 || radiusY <= Coliseum.BODY * 2) {
            return false;
        }
        coliseum.define(radiusX, radiusY,
                GladiatorProtocol.readShort(packet, GladiatorProtocol.WALL_HEIGHT),
                GladiatorProtocol.readShort(packet, GladiatorProtocol.PILLAR_RADIUS),
                GladiatorProtocol.readShort(packet, GladiatorProtocol.PILLAR_HEIGHT));
        for (int p = 0; p < count; p++) {
            coliseum.addPillar(GladiatorProtocol.readShort(packet, GladiatorProtocol.PILLARS + 4 * p),
                    GladiatorProtocol.readShort(packet, GladiatorProtocol.PILLARS + 4 * p + 2));
        }
        return true;
    }
}

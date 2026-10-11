package io.github.jabrena.juno.games.gladiator.arena;

import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;

/**
 * The {@link GladiatorProtocol#WORLD} packet the arena sends each gladiator ten times a second: the round, both healths,
 * the smoke and javelins it may see, the lion, and the opponent only while the recipient's eyes see it.
 */
final class WorldPacket {
    private WorldPacket() {
    }

    static void write(Match match, byte[] packet, int slot, int now) {
        Side own = match.side(slot);
        Side other = match.side(1 - slot);
        GladiatorProtocol.begin(packet, GladiatorProtocol.WORLD, slot, own.node());
        packet[GladiatorProtocol.PHASE] = (byte) match.phase();
        packet[GladiatorProtocol.ROUND] = (byte) match.round();
        packet[GladiatorProtocol.WINNER] = (byte) match.winner();
        packet[GladiatorProtocol.HEALTH] = (byte) own.health();
        packet[GladiatorProtocol.WINS] = (byte) own.wins();
        packet[GladiatorProtocol.HITS] = (byte) own.hits();
        packet[GladiatorProtocol.SURPRISED] = (byte) (own.surprised() ? 1 : 0);
        packet[GladiatorProtocol.COUNTDOWN] = (byte) match.countdown(now);
        packet[GladiatorProtocol.SMOKE] = (byte) match.coliseum().braziers().mask();
        match.javelins().write(packet, slot);
        packet[GladiatorProtocol.FLAGS] = (byte) ((own.rooted(now) ? GladiatorProtocol.ROOTED : 0)
                | (own.netReady() ? GladiatorProtocol.NET_READY : 0)
                | (other.rooted(now) ? GladiatorProtocol.OPPONENT_ROOTED : 0));
        packet[GladiatorProtocol.OPPONENT_CLASS] = (byte) other.kind();
        match.lion().write(packet);
        if (!other.present()) {
            return;
        }
        packet[GladiatorProtocol.OPPONENT] = 1;
        packet[GladiatorProtocol.OPPONENT_BEHAVIOUR] = (byte) other.behaviour();
        packet[GladiatorProtocol.OPPONENT_HEALTH] = (byte) other.health();
        packet[GladiatorProtocol.OPPONENT_WINS] = (byte) other.wins();
        if (match.sees(slot)) {
            packet[GladiatorProtocol.VISIBLE] = 1;
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.OPPONENT_X, other.x());
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.OPPONENT_Y, other.y());
            GladiatorProtocol.writeShort(packet, GladiatorProtocol.OPPONENT_ANGLE, other.angle());
            packet[GladiatorProtocol.OPPONENT_STRIKES] = (byte) other.strikes();
        }
    }
}

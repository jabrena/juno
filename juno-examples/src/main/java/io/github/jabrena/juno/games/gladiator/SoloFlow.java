package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** A complete local CPU-versus-CPU match using the selected player profile and a random opponent on an UNO Q. */
final class SoloFlow {
    private static final int FRAME_MILLIS = 40;
    private static final int SWING_FRAMES = 6;
    private static final int HURT_FRAMES = 4;
    private static final int THRUST_FRAMES = 6;

    private SoloFlow() {
    }

    /** Runs one local bout against a randomly generated CPU, then shows its victor. */
    static void run(FighterProfile profile) {
        FighterProfile opponent = FighterProfile.randomOpponent(profile);
        SoloPresentation.showOpponent(profile, opponent);
        SoloDuel duel = new SoloDuel(profile.behaviour, opponent.behaviour, Clock.millis());
        byte[] world = new byte[GladiatorProtocol.PACKET_SIZE];
        int swing = 0;
        int hurt = 0;
        int thrust = 0;
        boolean touching = true;
        int next = Clock.millis();
        Report.clear();
        Hud.bar();
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            int now = Clock.millis();
            if (TftTouchShield.readTouch()) {
                int x = TftTouchShield.touchX();
                int y = TftTouchShield.touchY();
                boolean tap = !touching;
                touching = true;
                if (tap && Hud.onMenu(x, y)) {
                    return;
                }
            } else {
                touching = false;
            }
            int duelEvents = duel.step(now);
            if ((duelEvents & SoloDuel.PLAYER_SWUNG) != 0) {
                swing = SWING_FRAMES;
            }
            if ((duelEvents & SoloDuel.CPU_SWUNG) != 0) {
                thrust = THRUST_FRAMES;
            }
            duel.writeWorld(world, now);
            int reportEvents = Report.read(world);
            if ((reportEvents & Report.STRUCK) != 0) {
                hurt = Report.surprised ? 2 * HURT_FRAMES : HURT_FRAMES;
            }
            if ((reportEvents & Report.OPPONENT_SWUNG) != 0) {
                thrust = THRUST_FRAMES;
            }
            swing = Math.max(0, swing - 1);
            hurt = Math.max(0, hurt - 1);
            thrust = Math.max(0, thrust - 1);
            View.build(ViewList.lines, duel.player, Report.visible, Report.opponentX, Report.opponentY,
                    Report.opponentAngle, Report.opponentKind, thrust > 0, swing, hurt, profile.kind);
            ViewList.present(ViewList.lines, ViewList.changes);
            Hud.ownProfile(profile.name, profile.displayName(), profile.color(), Report.health, Report.wins);
            Hud.opponentProfile(opponent.name, opponent.displayName(), opponent.color(),
                    Report.opponentHealth, Report.opponentWins);
            Hud.fighterStatus(0, false, duel.playerBrain.mode, hurt > 0);
            if (duel.match.phase == Match.OVER) {
                SoloPresentation.showResult(duel.match.winner == 0, profile, opponent);
                return;
            }
        }
    }

}

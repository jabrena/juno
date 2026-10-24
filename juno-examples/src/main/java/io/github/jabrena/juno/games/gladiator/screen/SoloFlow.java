package io.github.jabrena.juno.games.gladiator.screen;

import io.github.jabrena.juno.games.gladiator.arena.Match;
import io.github.jabrena.juno.games.gladiator.gladiator.Report;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;
import io.github.jabrena.juno.games.gladiator.view.ClassWireframe;
import io.github.jabrena.juno.games.gladiator.view.Hud;
import io.github.jabrena.juno.games.gladiator.view.View;
import io.github.jabrena.juno.games.gladiator.view.ViewList;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/** A complete local CPU-versus-CPU match using the selected player profile and a random opponent on an UNO Q. */
public final class SoloFlow {
    private static final Clock CLOCK = ArduinoUno.getClock();
    private static final int FRAME_MILLIS = 40;
    private static final int SWING_FRAMES = 6;
    private static final int HURT_FRAMES = 4;
    private static final int THRUST_FRAMES = 6;

    private SoloFlow() {
    }

    /** Runs one local bout against a randomly generated CPU, drawn into {@code list}, then shows its victor. */
    public static void run(ViewList list, ClassWireframe wireframe, FighterProfile profile) {
        FighterProfile opponent = FighterProfile.randomOpponent(profile);
        SoloPresentation.showOpponent(list, wireframe, profile, opponent);
        SoloDuel duel = new SoloDuel(profile.behaviour(), profile.kind(), opponent.behaviour(), opponent.kind(),
                CLOCK.millis());
        Report report = new Report(duel.coliseum());
        View view = new View(list, duel.coliseum(), report);
        Hud hud = new Hud(list);
        byte[] world = new byte[GladiatorProtocol.PACKET_SIZE];
        int swing = 0;
        int hurt = 0;
        int thrust = 0;
        boolean touching = true;
        int next = CLOCK.millis();
        int fpsSince = next;
        int frames = 0;
        hud.bar();
        while (true) {
            while (CLOCK.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            int now = CLOCK.millis();
            frames = frames + 1;
            if (now - fpsSince >= 1000) {
                hud.fps(frames * 1000 / (now - fpsSince));
                fpsSince = now;
                frames = 0;
            }
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
            int reportEvents = report.read(world);
            if ((reportEvents & Report.STRUCK) != 0) {
                hurt = report.surprised() ? 2 * HURT_FRAMES : HURT_FRAMES;
            }
            if ((reportEvents & Report.OPPONENT_SWUNG) != 0) {
                thrust = THRUST_FRAMES;
            }
            swing = Math.max(0, swing - 1);
            hurt = Math.max(0, hurt - 1);
            thrust = Math.max(0, thrust - 1);
            view.build(duel.player(), report.visible(), report.opponentX(), report.opponentY(),
                    report.opponentAngle(), report.opponentKind(), thrust > 0, swing, hurt, profile.kind());
            list.present();
            hud.ownProfile(profile.name(), profile.displayName(), profile.color(), report.health(), report.wins());
            hud.opponentProfile(opponent.name(), opponent.displayName(), opponent.color(), report.opponentHealth(),
                    report.opponentWins());
            hud.fighterStatus(report, 0, false, duel.playerBrain().mode(), hurt > 0);
            if (duel.match().phase() == Match.OVER) {
                SoloPresentation.showResult(duel.match().winner() == 0, profile, opponent);
                return;
            }
        }
    }

}

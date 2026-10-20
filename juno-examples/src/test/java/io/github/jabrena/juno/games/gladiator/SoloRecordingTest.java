package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.arena.Match;
import io.github.jabrena.juno.games.gladiator.gladiator.Report;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;
import io.github.jabrena.juno.games.gladiator.screen.FighterProfile;
import io.github.jabrena.juno.games.gladiator.screen.SoloDuel;
import io.github.jabrena.juno.games.gladiator.screen.SoloPresentation;
import io.github.jabrena.juno.games.gladiator.view.ClassWireframe;
import io.github.jabrena.juno.games.gladiator.view.Hud;
import io.github.jabrena.juno.games.gladiator.view.View;
import io.github.jabrena.juno.games.gladiator.view.ViewList;
import io.github.jabrena.juno.hardware.gpio.EmulatedShield;
import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Writes the frames of a whole Solo bout on demand, for documentation media: the player's screen exactly as
 * {@link SoloFlow} draws it, from the countdown until the victory or defeat shows. ffmpeg turns the frames into a
 * video.
 *
 * <p>{@code ./mvnw -pl juno-examples test -Dtest=SoloRecordingTest -Djuno.recording.solo=true
 * [-Djuno.recording.seed=7] [-Djuno.recording.kind=2]}, the kind being the player's {@link Opening} class.
 */
class SoloRecordingTest {
    private static final int FRAME_MILLIS = 40;
    private static final int SWING_FRAMES = 6;
    private static final int HURT_FRAMES = 4;
    private static final int THRUST_FRAMES = 6;
    private static final int REVEAL_FRAMES = 75;
    private static final int RESULT_FRAMES = 75;
    private static final int TITLE_FRAMES = 125;
    private static final int MAX_FRAMES = 6000;

    @Test
    @EnabledIfSystemProperty(named = "juno.recording.solo", matches = "true")
    void recordsASoloBout() throws Exception {
        Path output = Path.of(System.getProperty("basedir", "."), "target", "recordings", "solo");
        Files.createDirectories(output);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(Integer.getInteger("juno.recording.seed", 7));

        int kind = Integer.getInteger("juno.recording.kind", GladiatorClass.MURMILLO);
        FighterProfile profile = new FighterProfile(FighterProfile.FLAMMA, kind);
        FighterProfile opponent = FighterProfile.randomOpponent(profile);
        ViewList list = new ViewList();
        ClassWireframe wireframe = new ClassWireframe(list);
        SoloDuel duel = new SoloDuel(profile.behaviour(), profile.kind(), opponent.behaviour(), opponent.kind(), 1000);
        Report report = new Report(duel.coliseum());
        View view = new View(list, duel.coliseum(), report);
        Hud hud = new Hud(list);
        byte[] world = new byte[GladiatorProtocol.PACKET_SIZE];
        int written = 0;
        SoloPresentation.drawVersus(list, profile, opponent);
        for (int frame = 0; frame < REVEAL_FRAMES; frame++) {
            wireframe.buildVersus(profile.kind(), opponent.kind(), frame * FRAME_MILLIS * 0.0025f);
            list.present();
            write(output, written++);
        }
        int swing = 0;
        int hurt = 0;
        int thrust = 0;
        int resultFrames = 0;
        hud.bar();

        for (int frame = 0; frame < MAX_FRAMES && resultFrames < RESULT_FRAMES; frame++) {
            int now = 1000 + frame * FRAME_MILLIS;
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
            write(output, written++);
            if (duel.match().phase() == Match.OVER) {
                resultFrames++;
            }
        }
        SoloPresentation.drawResult(duel.match().winner() == 0, profile, opponent);
        for (int frame = 0; frame < TITLE_FRAMES; frame++) {
            write(output, written++);
        }
        System.out.println("SOLO-RECORDING " + (duel.match().winner() == 0 ? "VICTORY" : "DEFEAT") + " "
                + profile.displayName() + " vs " + opponent.displayName());
    }

    private static void write(Path output, int frame) throws Exception {
        ImageIO.write(capture(), "png", output.resolve("frame-%05d.png".formatted(frame)).toFile());
    }

    private static BufferedImage capture() {
        int width = TftTouchShield.width();
        int height = TftTouchShield.height();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = EmulatedShield.FRAMEBUFFER[y * EmulatedShield.STRIDE + x];
                int red = (color >> 11) & 31;
                int green = (color >> 5) & 63;
                int blue = color & 31;
                image.setRGB(x, y, (red * 255 / 31) << 16 | (green * 255 / 63) << 8 | (blue * 255 / 31));
            }
        }
        return image;
    }
}

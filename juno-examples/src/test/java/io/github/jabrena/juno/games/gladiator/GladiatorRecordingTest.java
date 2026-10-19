package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.hardware.io.gpio.EmulatedShield;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.util.Random;
import io.github.jabrena.juno.games.gladiator.arena.Body;
import io.github.jabrena.juno.games.gladiator.arena.Coliseum;
import io.github.jabrena.juno.games.gladiator.arena.Match;
import io.github.jabrena.juno.games.gladiator.arena.Side;
import io.github.jabrena.juno.games.gladiator.gladiator.Brain;
import io.github.jabrena.juno.games.gladiator.gladiator.Report;
import io.github.jabrena.juno.games.gladiator.net.GladiatorProtocol;
import io.github.jabrena.juno.games.gladiator.view.ArenaView;
import io.github.jabrena.juno.games.gladiator.view.Hud;
import io.github.jabrena.juno.games.gladiator.view.View;
import io.github.jabrena.juno.games.gladiator.view.ViewList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Writes the frames of a whole Gladiator match on demand, for documentation media: the arena's screen and both
 * gladiators' first-person screens side by side, as the three boards would show them. One AGGRESSIVE and one AMBUSH
 * gladiator play through the real {@link Match}, {@link Brain}, {@link Report}, {@link ArenaView} and {@link View};
 * the network is replaced by direct calls. ffmpeg turns the frames into a video.
 *
 * <p>{@code ./mvnw -pl juno-examples test -Dtest=GladiatorRecordingTest -Djuno.recording.gladiator=true}
 */
class GladiatorRecordingTest {
    private static final int FRAME_MILLIS = 40;
    private static final int CAPTURE_MILLIS = 80;
    private static final int WORLD_MILLIS = 120;
    private static final int[] SOURCE = {192, 168, 1, 20, GladiatorProtocol.PORT};

    private final Coliseum coliseum = new Coliseum();
    private final Match match = new Match(coliseum);
    private final Body[] bodies = {new Body(coliseum), new Body(coliseum)};
    private final Brain[] brains = {new Brain(Brain.AGGRESSIVE), new Brain(Brain.AMBUSH)};
    private final Report[] reports = {new Report(coliseum), new Report(coliseum)};
    private final ViewList list = new ViewList();
    private final View[] views = {new View(list, coliseum, reports[0]), new View(list, coliseum, reports[1])};
    private final Hud hud = new Hud(list);
    private final ArenaView arenaView = new ArenaView(coliseum);
    private final int[] strikes = new int[2];
    private final int[] swungAt = {-10_000, -10_000};
    private final int[] swing = new int[2];
    private final int[] hurt = new int[2];
    private final int[] thrust = new int[2];
    private final byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];

    @Test
    @EnabledIfSystemProperty(named = "juno.recording.gladiator", matches = "true")
    void recordsAMatch() throws Exception {
        int duration = Integer.getInteger("juno.recording.duration", 60_000);
        Path output = Path.of(System.getProperty("basedir", "."), "target", "recordings", "gladiator");
        Files.createDirectories(output);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Random.seed(7);
        coliseum.build();
        match.join(11, Brain.AGGRESSIVE, SOURCE, 0);
        match.join(22, Brain.AMBUSH, SOURCE, 0);

        int frame = 0;
        for (int now = 0; now < duration; now += FRAME_MILLIS) {
            step(now);
            if (now % CAPTURE_MILLIS == 0) {
                BufferedImage image = new BufferedImage(3 * ViewList.WIDTH, ViewList.HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.drawImage(arena(now), 0, 0, null);
                graphics.drawImage(fighter(0), ViewList.WIDTH, 0, null);
                graphics.drawImage(fighter(1), 2 * ViewList.WIDTH, 0, null);
                graphics.setColor(Color.DARK_GRAY);
                graphics.drawLine(ViewList.WIDTH, 0, ViewList.WIDTH, ViewList.HEIGHT);
                graphics.drawLine(2 * ViewList.WIDTH, 0, 2 * ViewList.WIDTH, ViewList.HEIGHT);
                graphics.dispose();
                ImageIO.write(image, "png", output.resolve("frame-%05d.png".formatted(frame++)).toFile());
            }
        }
    }

    /** One 40 ms frame of the arena and both boards. */
    private void step(int now) {
        match.tick(now);
        if (now % WORLD_MILLIS == 0) {
            for (int slot = 0; slot < 2; slot++) {
                match.writeWorld(packet, slot, now);
                react(slot, reports[slot].read(packet), now);
            }
        }
        for (int slot = 0; slot < 2; slot++) {
            swing[slot] = Math.max(0, swing[slot] - 1);
            hurt[slot] = Math.max(0, hurt[slot] - 1);
            thrust[slot] = Math.max(0, thrust[slot] - 1);
            boolean swung = false;
            Report report = reports[slot];
            if (match.phase() == Match.FIGHT) {
                bodies[slot].seeOpponent(report.visible(), report.opponentX(), report.opponentY());
                brains[slot].see(report.visible(), report.opponentX(), report.opponentY(), now);
                brains[slot].drive(bodies[slot], now);
                if (brains[slot].strike() && now - swungAt[slot] >= 700) {
                    swungAt[slot] = now;
                    strikes[slot] = (strikes[slot] + 1) & 0xFF;
                    swing[slot] = 6;
                    swung = true;
                }
            }
            if (swung || now % WORLD_MILLIS == 0) {
                sendState(slot, now);
            }
        }
    }

    private void sendState(int slot, int now) {
        byte[] state = new byte[GladiatorProtocol.PACKET_SIZE];
        GladiatorProtocol.begin(state, GladiatorProtocol.STATE, slot, match.side(slot).node());
        state[GladiatorProtocol.STATE_ROUND] = (byte) match.round();
        state[GladiatorProtocol.STATE_BEHAVIOUR] = (byte) brains[slot].behaviour();
        GladiatorProtocol.writeShort(state, GladiatorProtocol.X, Math.round(bodies[slot].x()));
        GladiatorProtocol.writeShort(state, GladiatorProtocol.Y, Math.round(bodies[slot].y()));
        GladiatorProtocol.writeShort(state, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(bodies[slot].angle()));
        state[GladiatorProtocol.STRIKES] = (byte) strikes[slot];
        state[GladiatorProtocol.MODE] = (byte) brains[slot].mode();
        match.state(slot, state, now);
    }

    /** What the gladiator's board does with what its {@code WORLD} packet said. */
    private void react(int slot, int events, int now) {
        if ((events & Report.NEW_ROUND) != 0) {
            bodies[slot].spawn(slot);
            brains[slot].reset(slot, now);
            swing[slot] = 0;
            hurt[slot] = 0;
        }
        if ((events & Report.STRUCK) != 0) {
            hurt[slot] = reports[slot].surprised() ? 8 : 4;
            brains[slot].hurt(now);
        }
        if ((events & Report.OPPONENT_SWUNG) != 0) {
            thrust[slot] = 6;
        }
    }

    private BufferedImage arena(int now) {
        arenaView.drawAll();
        arenaView.update(match, now);
        return capture();
    }

    private BufferedImage fighter(int slot) {
        Report report = reports[slot];
        Side own = match.side(slot);
        Side other = match.side(1 - slot);
        hud.bar();
        if (report.phase() == Match.WAITING) {
            hud.status(0, "WAITING", TftTouchShield.GRAY);
        } else {
            list.clearView();
            views[slot].build(bodies[slot], report.visible(), report.opponentX(), report.opponentY(),
                    report.opponentAngle(), other.behaviour(), thrust[slot] > 0, swing[slot], hurt[slot]);
            list.present();
            hud.fighterStatus(report, slot, false, brains[slot].mode(), hurt[slot] > 0);
        }
        hud.own(brains[slot].behaviour(), own.health(), own.wins());
        hud.opponent(other.present(), other.behaviour(), other.health(), other.wins());
        hud.pilot(false);
        return capture();
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

package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.tft.TftTouchShield;
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
 * gladiator play through the real {@link Match}, {@link Brain}, {@link ArenaView} and {@link View}; the network is
 * replaced by direct calls. ffmpeg turns the frames into a video.
 *
 * <p>{@code ./mvnw -pl juno-examples test -Dtest=GladiatorRecordingTest -Djuno.recording.gladiator=true}
 */
class GladiatorRecordingTest {
    private static final int FRAME_MILLIS = 40;
    private static final int CAPTURE_MILLIS = 80;
    private static final int[] SOURCE = {192, 168, 1, 20, Gladiator.PORT};

    private final Match match = new Match();
    private final Body[] bodies = {new Body(), new Body()};
    private final Brain[] brains = {new Brain(Brain.AGGRESSIVE), new Brain(Brain.AMBUSH)};
    private final int[] strikes = new int[2];
    private final int[] swungAt = {-10_000, -10_000};
    private final int[] swing = new int[2];
    private final int[] hurt = new int[2];
    private final int[] thrust = new int[2];
    private final int[] rounds = {-1, -1};
    private final int[] hits = {-1, -1};
    private final int[] seenStrikes = {-1, -1};
    private final boolean[] visible = new boolean[2];
    private final float[] opponentX = new float[2];
    private final float[] opponentY = new float[2];
    private final float[] opponentAngle = new float[2];
    private final byte[] packet = new byte[GladiatorProtocol.PACKET_SIZE];

    @Test
    @EnabledIfSystemProperty(named = "juno.recording.gladiator", matches = "true")
    void recordsAMatch() throws Exception {
        int duration = Integer.getInteger("juno.recording.duration", 60_000);
        Path output = Path.of(System.getProperty("basedir", "."), "target", "recordings", "gladiator");
        Files.createDirectories(output);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        Coliseum.build();
        Random.seed(7);
        match.join(11, Brain.AGGRESSIVE, SOURCE, 0);
        match.join(22, Brain.AMBUSH, SOURCE, 0);

        int frame = 0;
        for (int now = 0; now < duration; now += FRAME_MILLIS) {
            step(now);
            if (now % CAPTURE_MILLIS == 0) {
                BufferedImage image = new BufferedImage(3 * ViewList.WIDTH, ViewList.HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.drawImage(arena(now), 0, 0, null);
                graphics.drawImage(fighter(0, now), ViewList.WIDTH, 0, null);
                graphics.drawImage(fighter(1, now), 2 * ViewList.WIDTH, 0, null);
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
        if (now % 120 == 0) {
            for (int slot = 0; slot < 2; slot++) {
                match.writeWorld(packet, slot, now);
                world(slot, now);
            }
        }
        for (int slot = 0; slot < 2; slot++) {
            swing[slot] = Math.max(0, swing[slot] - 1);
            hurt[slot] = Math.max(0, hurt[slot] - 1);
            thrust[slot] = Math.max(0, thrust[slot] - 1);
            boolean swung = false;
            if (match.phase == Match.FIGHT) {
                Body body = bodies[slot];
                body.opponentInSight = visible[slot];
                body.opponentX = opponentX[slot];
                body.opponentY = opponentY[slot];
                brains[slot].see(visible[slot], opponentX[slot], opponentY[slot], now);
                brains[slot].drive(body, now);
                if (brains[slot].strike && now - swungAt[slot] >= 700) {
                    swungAt[slot] = now;
                    strikes[slot] = (strikes[slot] + 1) & 0xFF;
                    swing[slot] = 6;
                    swung = true;
                }
            }
            if (swung || now % 120 == 0) {
                byte[] state = new byte[GladiatorProtocol.PACKET_SIZE];
                GladiatorProtocol.begin(state, GladiatorProtocol.STATE, slot, match.node[slot]);
                state[GladiatorProtocol.STATE_ROUND] = (byte) match.round;
                state[GladiatorProtocol.STATE_BEHAVIOUR] = (byte) brains[slot].behaviour;
                GladiatorProtocol.writeShort(state, GladiatorProtocol.X, Math.round(bodies[slot].x));
                GladiatorProtocol.writeShort(state, GladiatorProtocol.Y, Math.round(bodies[slot].y));
                GladiatorProtocol.writeShort(state, GladiatorProtocol.ANGLE, GladiatorProtocol.toWire(bodies[slot].angle));
                state[GladiatorProtocol.STRIKES] = (byte) strikes[slot];
                state[GladiatorProtocol.MODE] = (byte) brains[slot].mode;
                match.state(slot, state, now);
            }
        }
    }

    /** What the gladiator's board does with a {@code WORLD} packet. */
    private void world(int slot, int now) {
        int round = GladiatorProtocol.unsigned(packet, GladiatorProtocol.ROUND);
        if (round != rounds[slot]) {
            rounds[slot] = round;
            bodies[slot].spawn(slot);
            brains[slot].reset(slot, now);
            seenStrikes[slot] = -1;
            swing[slot] = 0;
            hurt[slot] = 0;
        }
        int hit = GladiatorProtocol.unsigned(packet, GladiatorProtocol.HITS);
        if (hits[slot] >= 0 && hit != hits[slot]) {
            boolean unseen = packet[GladiatorProtocol.SURPRISED] != 0;
            hurt[slot] = unseen ? 8 : 4;
            brains[slot].hurt(now);
        }
        hits[slot] = hit;
        visible[slot] = packet[GladiatorProtocol.VISIBLE] != 0;
        if (visible[slot]) {
            opponentX[slot] = GladiatorProtocol.readShort(packet, GladiatorProtocol.OPPONENT_X);
            opponentY[slot] = GladiatorProtocol.readShort(packet, GladiatorProtocol.OPPONENT_Y);
            opponentAngle[slot] = GladiatorProtocol.fromWire(
                    GladiatorProtocol.readWord(packet, GladiatorProtocol.OPPONENT_ANGLE));
            int swung = GladiatorProtocol.unsigned(packet, GladiatorProtocol.OPPONENT_STRIKES);
            if (seenStrikes[slot] >= 0 && swung != seenStrikes[slot]) {
                thrust[slot] = 6;
            }
            seenStrikes[slot] = swung;
        }
    }

    private BufferedImage arena(int now) {
        ArenaView.drawAll();
        ArenaView.update(match, now);
        return capture();
    }

    private BufferedImage fighter(int slot, int now) {
        int other = 1 - slot;
        Hud.bar();
        Report.phase = match.phase;
        Report.round = match.round;
        Report.winner = match.winner;
        Report.countdown = match.countdown(now);
        Report.health = match.health[slot];
        Report.wins = match.wins[slot];
        Report.surprised = match.surprised[slot];
        Report.opponentPresent = match.present[other];
        Report.opponentKind = match.behaviour[other];
        Report.opponentHealth = match.health[other];
        Report.opponentWins = match.wins[other];
        Report.visible = visible[slot];
        if (match.phase == Match.WAITING) {
            Hud.status(0, "WAITING", TftTouchShield.GRAY);
        } else {
            ViewList.clearView();
            View.build(ViewList.lines, bodies[slot], visible[slot], opponentX[slot], opponentY[slot],
                    opponentAngle[slot], match.behaviour[other], thrust[slot] > 0, swing[slot], hurt[slot]);
            ViewList.present(ViewList.lines, ViewList.changes);
            Hud.fighterStatus(slot, false, brains[slot].mode, hurt[slot] > 0);
        }
        Hud.own(brains[slot].behaviour, match.health[slot], match.wins[slot]);
        Hud.opponent(match.present[other], match.behaviour[other], match.health[other], match.wins[other]);
        Hud.pilot(false);
        return capture();
    }

    private static BufferedImage capture() {
        int width = TftTouchShield.width();
        int height = TftTouchShield.height();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = Gpio.FRAMEBUFFER[y * Gpio.STRIDE + x];
                int red = (color >> 11) & 31;
                int green = (color >> 5) & 63;
                int blue = color & 31;
                image.setRGB(x, y, (red * 255 / 31) << 16 | (green * 255 / 63) << 8 | (blue * 255 / 31));
            }
        }
        return image;
    }
}

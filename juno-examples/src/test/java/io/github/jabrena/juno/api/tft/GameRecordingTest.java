package io.github.jabrena.juno.api.tft;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Writes emulator frames on demand; ffmpeg turns these generated test artifacts into documentation media. */
class GameRecordingTest {
    @Test
    @EnabledIfSystemProperty(named = "juno.recording.game", matches = ".+")
    void recordsGameFrames() throws Exception {
        String game = System.getProperty("juno.recording.game");
        String script = System.getProperty("juno.recording.script", "end:10000");
        int duration = Integer.getInteger("juno.recording.duration", 10_000);
        int interval = Integer.getInteger("juno.recording.interval", 100);
        Path output = Path.of(System.getProperty("basedir", "."), "target", "recordings", game);
        Files.createDirectories(output);
        List<BufferedImage> frames = TftEmulator.record(
                "io.github.jabrena.juno." + game, duration, interval, taps(script));
        for (int index = 0; index < frames.size(); index++) {
            ImageIO.write(frames.get(index), "png", output.resolve("frame-%05d.png".formatted(index)).toFile());
        }
    }

    private static List<TftEmulator.Tap> taps(String script) {
        List<TftEmulator.Tap> taps = new ArrayList<>();
        for (String step : script.trim().split("\\s+")) {
            String[] parts = step.split(":");
            if (!parts[0].equals("end")) {
                String[] point = parts[1].split(",");
                taps.add(new TftEmulator.Tap(
                        Integer.parseInt(parts[0]), Integer.parseInt(point[0]), Integer.parseInt(point[1])));
            }
        }
        return taps;
    }
}

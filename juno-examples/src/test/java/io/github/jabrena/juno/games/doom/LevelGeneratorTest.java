package io.github.jabrena.juno.games.doom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;

/**
 * Generates {@code Level.java} from a local DOOM WAD on demand:
 * {@code ./mvnw -f juno-examples/pom.xml test -Dtest=LevelGeneratorTest -Djuno.doom.wad=/path/DOOM1.WAD}.
 * By default it overwrites the game's {@code Level.java}; {@code -Djuno.doom.output=...} writes elsewhere.
 * The result holds id Software's data and must not be committed.
 */
class LevelGeneratorTest {
    @Test
    @EnabledIfSystemProperty(named = "juno.doom.wad", matches = ".+")
    void generatesTheLevelTables() throws Exception {
        Path base = Path.of(System.getProperty("basedir", "."));
        Path output = Path.of(System.getProperty("juno.doom.output",
                base.resolve("src/main/java/io/github/jabrena/juno/games/doom/Level.java").toString()));
        LevelGenerator.generate(Path.of(System.getProperty("juno.doom.wad")),
                System.getProperty("juno.doom.map", "E1M1"), output);
    }
}

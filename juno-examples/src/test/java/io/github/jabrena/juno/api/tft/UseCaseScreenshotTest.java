package io.github.jabrena.juno.api.tft;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Screenshot coverage for multi-board examples under {@code usecases}, kept separate from standalone games. */
class UseCaseScreenshotTest {
    private static final String MINER2 = "usecases.miner2.Miner2";
    private static final String MINER2_ID = "123e4567e89b12d3a456426614174000";

    static Stream<Arguments> useCases() {
        return Stream.of(
                Arguments.of("usecases.miner.MinerGame", "miner", "end:60000"),
                Arguments.of("usecases.miner2.Miner2", "miner2-cover-logo", "end:1500"),
                Arguments.of("usecases.miner2.Miner2", "miner2-cover-board", "end:4500"),
                Arguments.of("usecases.miner2.Miner2", "miner2-cover-title", "end:7500"),
                Arguments.of("usecases.miner2.Miner2", "miner2-chain", "7500:120,225 end:60000"),
                Arguments.of("usecases.miner2.Miner2", "miner2-peers", "7500:120,225 60000:90,300 end:61000"),
                Arguments.of("usecases.miner2.Miner2", "miner2-net", "7500:120,225 60000:200,300 end:62500"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("useCases")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void matchesItsScreenshot(String useCase, String screenshot, String script) throws Exception {
        String previousIdentity = null;
        if (useCase.equals(MINER2)) {
            previousIdentity = System.getProperty("juno.emulator.miner2Identity");
            System.setProperty("juno.emulator.miner2Identity", MINER2_ID);
        }
        try {
            GameScreenshotTest.assertMatches("usecases", useCase, screenshot, script);
        } finally {
            if (useCase.equals(MINER2)) {
                if (previousIdentity == null) {
                    System.clearProperty("juno.emulator.miner2Identity");
                } else {
                    System.setProperty("juno.emulator.miner2Identity", previousIdentity);
                }
            }
        }
    }
}

package io.github.jabrena.juno.api.tft;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Screenshot coverage for multi-board examples under {@code usecases}, kept separate from standalone games. */
class UseCaseScreenshotTest {
    static Stream<Arguments> useCases() {
        return Stream.of(
                Arguments.of("usecases.ledger.LedgerGame", "ledger-menu", "end:3000"),
                Arguments.of("usecases.ledger.LedgerGame", "ledger-leader", "3000:120,120 end:6000"),
                Arguments.of("usecases.ledger.LedgerGame", "ledger-worker", "3000:120,210 end:6000"),
                Arguments.of("usecases.ledger.LedgerGame", "ledger-menu-again",
                        "3000:120,120 8000:120,297 end:10000"),
                Arguments.of("usecases.miner.MinerGame", "miner", "end:60000"),
                Arguments.of("usecases.miner2.Miner2", "miner2-cover-logo", "end:1500"),
                Arguments.of("usecases.miner2.Miner2", "miner2-cover-board", "end:4500"),
                Arguments.of("usecases.miner2.Miner2", "miner2-cover-title", "end:7500"),
                Arguments.of("usecases.miner2.Miner2", "miner2-chain", "7500:120,225 end:60000"),
                Arguments.of("usecases.miner2.Miner2", "miner2-peers", "7500:120,225 60000:120,300 end:61000"),
                Arguments.of("usecases.miner2.Miner2", "miner2-net", "7500:120,225 60000:200,300 end:62500"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("useCases")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void matchesItsScreenshot(String useCase, String screenshot, String script) throws Exception {
        GameScreenshotTest.assertMatches("usecases", useCase, screenshot, script);
    }
}

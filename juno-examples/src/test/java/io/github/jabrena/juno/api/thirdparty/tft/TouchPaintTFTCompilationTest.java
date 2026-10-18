package io.github.jabrena.juno.api.thirdparty.tft;

import io.github.jabrena.juno.CompilationRequest;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TouchPaintTFTCompilationTest {
    private static final String MAIN = "io.github.jabrena.juno.api.thirdparty.tft.TouchPaintTFT";

    @Test
    void touchLoggingDoesNotBuildABoundedRuntimeString() throws Exception {
        CompilationResult result = compileForUnoWifiRev2();

        assertThat(result.assembly()).doesNotContain("juno_string_concat");
    }

    private static CompilationResult compileForUnoWifiRev2() throws URISyntaxException {
        Path examples = Path.of("target/classes");
        Path api = Path.of(Watchdog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new JunoCompiler().compile(new CompilationRequest(List.of(examples, api), MAIN, false,
                Optional.of(Board.UNO_R3_WIFI_REV2.id())));
    }
}

package io.github.jabrena.juno.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CI Java compile matrix")
class CiBoardMatrixTest {

    private static final Pattern JOB_HEADER = Pattern.compile("(?m)^  [a-z0-9-]+:\\s*$");
    private static final Pattern BOARD_ID = Pattern.compile("(?m)^\\s*board_id:\\s*(\\S+)\\s*$");

    @Test
    @DisplayName("The arduino-java matrix lists exactly the ids of Board.values()")
    void shouldListEveryBoardInTheJavaCompileMatrix() throws IOException {
        // Given
        String workflow = Files.readString(findWorkflow());
        int start = workflow.indexOf("\n  arduino-java:");
        assertThat(start).as("arduino-java job in maven.yaml").isGreaterThanOrEqualTo(0);
        Matcher nextJob = JOB_HEADER.matcher(workflow);
        int headerEnd = start + "\n  arduino-java:".length();
        int end = nextJob.find(headerEnd) ? nextJob.start() : workflow.length();
        String job = workflow.substring(start, end);

        // When
        List<String> matrixBoards = new java.util.ArrayList<>();
        Matcher ids = BOARD_ID.matcher(job);
        while (ids.find()) {
            matrixBoards.add(ids.group(1));
        }

        // Then: a board added to Board without a matrix entry, or a matrix entry naming no board, fails here.
        List<String> enumBoards = Stream.of(Board.values()).map(Board::id).toList();
        assertThat(matrixBoards).containsExactlyInAnyOrderElementsOf(enumBoards);
    }

    /** The workflow sits at the repository root; Maven runs tests from the module directory below it. */
    private static Path findWorkflow() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(".github/workflows/maven.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IOException("Could not find .github/workflows/maven.yaml above " + Path.of("").toAbsolutePath());
    }
}

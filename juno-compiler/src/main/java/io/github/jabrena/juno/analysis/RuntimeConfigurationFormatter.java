package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.ResolvedRuntimeConfig;
import io.github.jabrena.juno.ResolvedRuntimeConfig.Source;
import io.github.jabrena.juno.board.BoardMemoryProfile;

import java.util.List;

/**
 * Renders the memory configuration a build used as human-readable lines: the arena and carrier stack with where each
 * came from, the board's maximums, and the PSRAM the board carries. Shared by the CLI and the Maven plugin, and printed
 * on every build, with or without findings.
 */
public final class RuntimeConfigurationFormatter {

    public List<String> format(ResolvedRuntimeConfig config) {
        BoardMemoryProfile profile = config.board().memoryProfile();
        String name = config.board().displayName();
        String arenaSource = config.arenaSource() == Source.OVERRIDE ? "juno.Xmx override" : "board default";
        String stackSource = config.stackSource() == Source.OVERRIDE ? "juno.Xss override" : "board default";
        String carriers = config.carrierCount() == 1 ? "1 carrier" : config.carrierCount() + " carriers";
        String psram = profile.psramBytes().isPresent()
                ? "PSRAM: " + (profile.psramBytes().getAsInt() / (1024 * 1024)) + " MiB, not used by the arena"
                : "PSRAM: none recorded";
        return List.of(
                "Runtime configuration (" + name + "):",
                "  Arena: " + config.arenaBytes() + " bytes (" + arenaSource + "; max " + profile.maxArenaBytes()
                        + " of " + profile.internalSramBytes() + " bytes internal SRAM)",
                "  Carrier stack: " + config.threadStackBytes() + " bytes (" + stackSource + "; max "
                        + profile.maxStackBytes() + ") x " + carriers,
                "  " + psram);
    }
}

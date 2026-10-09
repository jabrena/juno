package io.github.jabrena.juno.analysis;

import java.util.ArrayList;
import java.util.List;

/** Renders {@link ConfigSuggestion}s as human-readable lines, shared by the CLI and the Maven plugin. */
public final class ConfigSuggestionFormatter {
    private ConfigSuggestionFormatter() {
    }

    /** One line per suggestion, or none when the configuration already suits the program. */
    public static List<String> format(List<ConfigSuggestion> suggestions) {
        List<String> lines = new ArrayList<>();
        if (suggestions.isEmpty()) {
            return lines;
        }
        lines.add("Runtime configuration suggestions:");
        for (ConfigSuggestion suggestion : suggestions) {
            lines.add("  [" + suggestion.code() + "] " + suggestion.parameter() + " " + suggestion.current()
                    + " -> " + suggestion.suggested() + ": " + suggestion.message());
        }
        return lines;
    }
}

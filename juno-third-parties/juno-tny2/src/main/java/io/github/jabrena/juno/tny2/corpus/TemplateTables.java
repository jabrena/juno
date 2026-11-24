package io.github.jabrena.juno.tny2.corpus;

import io.github.jabrena.juno.tny2.Tny2FormatException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facts written as tables and expanded through question templates, the way TINY 2.1's {@code gen2.py} turns a few
 * hundred facts into thousands of wordings. A template line {@code relation => template | template} uses {@code {s}}
 * for the subject; a table row {@code relation<TAB>subject|alias<TAB>answer} becomes one {@link Fact} whose questions
 * are every template of its relation with every name of its subject.
 */
public final class TemplateTables {
    private static final String ARROW = "=>";
    private static final String ALTERNATIVE = "\\|";
    private static final String COMMENT = "#";
    private static final String SUBJECT = "{s}";
    private static final String TAB = "\t";

    private final Map<String, List<String>> templates;

    /** Parses the template lines of {@code source}. */
    public TemplateTables(String source, String text) {
        Map<String, List<String>> parsed = new LinkedHashMap<>();
        List<String> lines = text.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith(COMMENT)) {
                continue;
            }
            String[] sides = line.split(ARROW, -1);
            List<String> forms = sides.length == 2 ? alternatives(sides[1]) : List.of();
            if (sides.length != 2 || sides[0].isBlank() || forms.isEmpty()
                    || !forms.stream().allMatch(form -> form.contains(SUBJECT))) {
                throw new Tny2FormatException("%s:%d: expected 'relation => template {s} | template {s}'"
                        .formatted(source, i + 1));
            }
            parsed.put(sides[0].strip(), forms);
        }
        templates = Map.copyOf(parsed);
    }

    /** Expands the rows of one table into facts; every relation it uses must have templates. */
    public List<Fact> expand(String source, String text) {
        List<Fact> facts = new ArrayList<>();
        List<String> lines = text.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith(COMMENT)) {
                continue;
            }
            String[] cells = line.split(TAB, -1);
            List<String> forms = cells.length == 3 ? templates.get(cells[0].strip()) : null;
            if (forms == null) {
                throw new Tny2FormatException(("%s:%d: expected 'relation<TAB>subject|alias<TAB>answer' with a "
                        + "relation that has templates").formatted(source, i + 1));
            }
            List<String> questions = new ArrayList<>();
            for (String subject : alternatives(cells[1])) {
                for (String form : forms) {
                    questions.add(form.replace(SUBJECT, subject));
                }
            }
            try {
                facts.add(new Fact(questions, List.of(cells[2].strip())));
            } catch (IllegalArgumentException | Tny2FormatException invalid) {
                throw new Tny2FormatException("%s:%d: %s".formatted(source, i + 1, invalid.getMessage()));
            }
        }
        return List.copyOf(facts);
    }

    private static List<String> alternatives(String side) {
        return Arrays.stream(side.split(ALTERNATIVE)).map(String::strip).filter(text -> !text.isEmpty()).toList();
    }
}

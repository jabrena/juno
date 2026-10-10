package io.github.jabrena.juno.backend;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes redundant fixed-frame memory traffic from generated assembly. */
final class AssemblyPeepholeOptimizer {
    private static final Pattern STACK_LOAD = Pattern.compile("\\s*ldr (r(?:1[0-2]|[0-9])), \\[sp, #(\\d+)]");
    private static final Pattern STACK_STORE = Pattern.compile("\\s*str (r(?:1[0-2]|[0-9])), \\[sp, #(\\d+)]");

    private AssemblyPeepholeOptimizer() {
    }

    /**
     * Tracks only exact {@code [sp, #offset]} words and only across stores, blank lines and comments.
     * Every other instruction, label or directive is a barrier. This deliberately small window
     * captures Juno's common store/load round trips without making assumptions about flags, calls,
     * control-flow joins, indirect memory aliases or scratch-register lifetimes.
     */
    static String optimize(String assembly) {
        StringBuilder optimized = new StringBuilder(assembly.length());
        Map<Integer, String> residentWords = new HashMap<>();
        String[] lines = assembly.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            Matcher load = STACK_LOAD.matcher(line);
            Matcher store = STACK_STORE.matcher(line);
            if (load.matches()) {
                String destination = load.group(1);
                int offset = Integer.parseInt(load.group(2));
                String resident = residentWords.get(offset);
                if (resident == null) {
                    forgetRegister(residentWords, destination);
                    appendLine(optimized, line, index, lines.length);
                    residentWords.put(offset, destination);
                } else if (!resident.equals(destination)) {
                    forgetRegister(residentWords, destination);
                    appendLine(optimized, "    mov " + destination + ", " + resident, index, lines.length);
                    residentWords.put(offset, resident);
                }
                continue;
            }
            if (store.matches()) {
                String source = store.group(1);
                int offset = Integer.parseInt(store.group(2));
                if (!source.equals(residentWords.get(offset))) {
                    appendLine(optimized, line, index, lines.length);
                }
                residentWords.put(offset, source);
                continue;
            }

            appendLine(optimized, line, index, lines.length);
            String stripped = line.strip();
            if (!stripped.isEmpty() && !stripped.startsWith("@")) {
                residentWords.clear();
            }
        }
        return optimized.toString();
    }

    private static void forgetRegister(Map<Integer, String> residentWords, String register) {
        for (Iterator<Map.Entry<Integer, String>> iterator = residentWords.entrySet().iterator(); iterator.hasNext();) {
            if (iterator.next().getValue().equals(register)) {
                iterator.remove();
            }
        }
    }

    private static void appendLine(StringBuilder output, String line, int index, int lineCount) {
        output.append(line);
        if (index < lineCount - 1) {
            output.append('\n');
        }
    }
}

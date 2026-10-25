///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Text filters for {@code mailbox.sh}, reading stdin and writing stdout.
 *
 * <ul>
 *   <li>{@code summary} picks a reply's {@code TL;DR:} line, else its first paragraph.
 *   <li>{@code tweet LIMIT} drops markdown and non-ASCII characters, joins the text onto one line and cuts it to
 *       LIMIT characters, because the UNO Q mailbox hangs on non-ASCII bodies.
 * </ul>
 */
public final class MailboxText {

    private static final Pattern TLDR =
            Pattern.compile("^\\W*TL;DR:?\\W*(.+)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern MARKDOWN_MARKS = Pattern.compile("[`*_#>|]");
    private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String ELLIPSIS = "...";

    private final String text;

    MailboxText(String text) {
        this.text = text;
    }

    String summary() {
        String trimmed = text.strip();
        var match = TLDR.matcher(trimmed);
        return match.find() ? match.group(1) : PARAGRAPH_BREAK.split(trimmed, 2)[0];
    }

    String tweet(int limit) {
        String plain = MARKDOWN_LINK.matcher(text).replaceAll("$1");
        plain = MARKDOWN_MARKS.matcher(plain).replaceAll("");
        plain = Normalizer.normalize(plain, Normalizer.Form.NFKD);
        plain = NON_ASCII.matcher(plain).replaceAll("");
        plain = WHITESPACE.matcher(plain).replaceAll(" ").strip();
        if (plain.length() <= limit) {
            return plain;
        }
        return plain.substring(0, limit - ELLIPSIS.length()).stripTrailing() + ELLIPSIS;
    }

    public static void main(String[] args) throws IOException {
        var input = new MailboxText(new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
        String output =
                switch (args.length == 0 ? "" : args[0]) {
                    case "summary" -> input.summary();
                    case "tweet" -> input.tweet(Integer.parseInt(args[1]));
                    default -> {
                        System.err.println("usage: MailboxText.java summary | tweet LIMIT");
                        System.exit(2);
                        yield "";
                    }
                };
        System.out.println(output);
    }
}

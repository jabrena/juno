package io.github.jabrena.juno.maven;

import java.util.List;
import java.util.Optional;

/**
 * One RFC 5424 syslog message as {@code juno:monitor} shows it: the severity, the sender's host and app names, and the
 * free-form message. The facility, process id, message id, timestamp and structured data are parsed past but not kept,
 * since a board sends them as nil values.
 */
record SyslogMessage(int severity, String hostName, String appName, String message) {
    private static final List<String> SEVERITY_NAMES = List.of(
            "EMERGENCY", "ALERT", "CRITICAL", "ERROR", "WARNING", "NOTICE", "INFO", "DEBUG");
    private static final int MAX_PRIORITY = 191;
    private static final int SEVERITIES = 8;
    private static final String NIL = "-";

    /** The severity's name, from {@code EMERGENCY} (0) to {@code DEBUG} (7). */
    String severityName() {
        return SEVERITY_NAMES.get(severity);
    }

    /**
     * Parses {@code <PRI>1 TIMESTAMP HOSTNAME APP-NAME PROCID MSGID STRUCTURED-DATA [MSG]}; empty when the frame does
     * not follow RFC 5424, so the caller can show it raw instead of dropping it.
     */
    static Optional<SyslogMessage> parse(String frame) {
        return new Cursor(frame).message();
    }

    /** Reads one frame left to right; each step returns empty as soon as the frame stops matching the grammar. */
    private static final class Cursor {
        private final String frame;
        private int position;

        Cursor(String frame) {
            this.frame = frame;
        }

        Optional<SyslogMessage> message() {
            Optional<Integer> priority = priority();
            if (priority.isEmpty() || !consume("1 ")) {
                return Optional.empty();
            }
            Optional<String> timestamp = field();
            Optional<String> hostName = field();
            Optional<String> appName = field();
            Optional<String> processId = field();
            Optional<String> messageId = field();
            if (timestamp.isEmpty() || hostName.isEmpty() || appName.isEmpty() || processId.isEmpty()
                    || messageId.isEmpty() || !structuredData()) {
                return Optional.empty();
            }
            String text = "";
            if (position < frame.length()) {
                if (frame.charAt(position) != ' ') {
                    return Optional.empty();
                }
                text = frame.substring(position + 1);
            }
            return Optional.of(new SyslogMessage(priority.get() % SEVERITIES, hostName.get(), appName.get(),
                    withoutByteOrderMark(text)));
        }

        private Optional<Integer> priority() {
            if (!consume("<")) {
                return Optional.empty();
            }
            int start = position;
            while (position < frame.length() && position - start < 3 && Character.isDigit(frame.charAt(position))) {
                position++;
            }
            if (position == start || !consume(">")) {
                return Optional.empty();
            }
            int value = Integer.parseInt(frame.substring(start, position - 1));
            return value <= MAX_PRIORITY ? Optional.of(value) : Optional.empty();
        }

        /** A space-terminated header field: one or more non-space characters followed by a single space. */
        private Optional<String> field() {
            int end = frame.indexOf(' ', position);
            if (end <= position) {
                return Optional.empty();
            }
            String value = frame.substring(position, end);
            position = end + 1;
            return Optional.of(value);
        }

        /** The nil value or one or more {@code [id param="value"...]} elements, whose values may escape {@code ]}. */
        private boolean structuredData() {
            if (consume(NIL)) {
                return true;
            }
            boolean any = false;
            while (position < frame.length() && frame.charAt(position) == '[') {
                if (!element()) {
                    return false;
                }
                any = true;
            }
            return any;
        }

        private boolean element() {
            boolean quoted = false;
            for (position++; position < frame.length(); position++) {
                char character = frame.charAt(position);
                if (quoted && character == '\\') {
                    position++;
                } else if (character == '"') {
                    quoted = !quoted;
                } else if (character == ']' && !quoted) {
                    position++;
                    return true;
                }
            }
            return false;
        }

        private boolean consume(String expected) {
            if (frame.startsWith(expected, position)) {
                position += expected.length();
                return true;
            }
            return false;
        }

        private static String withoutByteOrderMark(String text) {
            return text.startsWith("﻿") ? text.substring(1) : text;
        }
    }
}

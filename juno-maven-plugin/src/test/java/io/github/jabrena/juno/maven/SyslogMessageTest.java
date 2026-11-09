package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RFC 5424 syslog message")
class SyslogMessageTest {
    @Test
    @DisplayName("Parses the frame a board sends")
    void shouldParseTheFrameABoardSends() {
        // When
        SyslogMessage message = SyslogMessage.parse("<134>1 - nano-rcx drive - - - t=200 left=3 right=-3").orElseThrow();

        // Then
        assertThat(message).isEqualTo(new SyslogMessage(6, "nano-rcx", "drive", "t=200 left=3 right=-3"));
        assertThat(message.severityName()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("Parses a full frame with a timestamp, structured data and a byte order mark")
    void shouldParseAFullFrame() {
        // Given
        String frame = "<165>1 2003-10-11T22:14:15.003Z mymachine evntslog - ID47 "
                + "[exampleSDID@32473 iut=\"3\" eventSource=\"App\\]lication\"][other@1 a=\"b\"] ﻿An application event";

        // When
        SyslogMessage message = SyslogMessage.parse(frame).orElseThrow();

        // Then
        assertThat(message).isEqualTo(new SyslogMessage(5, "mymachine", "evntslog", "An application event"));
        assertThat(message.severityName()).isEqualTo("NOTICE");
    }

    @Test
    @DisplayName("Accepts a frame without a message")
    void shouldAcceptAFrameWithoutAMessage() {
        assertThat(SyslogMessage.parse("<131>1 - host app - - -"))
                .contains(new SyslogMessage(3, "host", "app", ""));
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {
        "plain text",
        "<134> - host app - - - no version",
        "<192>1 - host app - - - priority too high",
        "<1345>1 - host app - - - priority too long",
        "<134>2 - host app - - - unknown version",
        "<134>1 - host app - - x structured data missing",
        "<134>1 - host app - - [unterminated message",
        "<134>1 - host  app - - - empty field",
        "<134>1 - host app - - -no space before message"
    })
    void shouldRejectAFrameThatIsNotRfc5424(String frame) {
        assertThat(SyslogMessage.parse(frame)).isEmpty();
    }
}

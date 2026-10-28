package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxPollingTest {
    @Test
    void keepsTheFiveMinuteDefaultWhenNoSettingExists() {
        MailboxPolling polling = new MailboxPolling(new EmulatedSdCard());

        polling.begin();

        assertThat(polling.minutes()).isEqualTo(MailboxPolling.DEFAULT_MINUTES);
    }

    @Test
    void persistsAValidIntervalAcrossRestart() {
        EmulatedSdCard card = new EmulatedSdCard();
        card.insert();
        MailboxPolling polling = new MailboxPolling(card);

        assertThat(polling.change("15".toCharArray(), 2)).isTrue();
        MailboxPolling afterRestart = new MailboxPolling(card);
        afterRestart.begin();

        assertThat(afterRestart.minutes()).isEqualTo(15);
    }

    @Test
    void rejectsValuesOutsideOneToSixty() {
        EmulatedSdCard card = new EmulatedSdCard();
        card.insert();
        MailboxPolling polling = new MailboxPolling(card);

        assertThat(polling.change("0".toCharArray(), 1)).isFalse();
        assertThat(polling.change("61".toCharArray(), 2)).isFalse();
        assertThat(polling.minutes()).isEqualTo(MailboxPolling.DEFAULT_MINUTES);
    }
}

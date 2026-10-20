package io.github.jabrena.juno.api.remote.lego.rcx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RcxDownloadProgressTest {
    @Test
    void lostRepliesNeverSkipBytesAndRecoveryIsBounded() {
        RcxDownloadProgress progress = new RcxDownloadProgress(2249);
        assertThat(progress.restart()).isTrue();
        assertThat(progress.accept(0)).isTrue();
        assertThat(progress.offset()).isEqualTo(64);
        assertThat(progress.accept(-1)).isTrue();
        assertThat(progress.accept(-1)).isTrue();
        assertThat(progress.accept(-1)).isFalse();
        assertThat(progress.offset()).isEqualTo(64);
        assertThat(progress.index()).isEqualTo(2);
        assertThat(progress.restart()).isTrue();
        assertThat(progress.offset()).isZero();
        assertThat(progress.index()).isEqualTo(1);
        assertThat(progress.size()).isEqualTo(32);
        for (int attempt = 3; attempt <= 5; attempt++) {
            assertThat(progress.restart()).isTrue();
            assertThat(progress.size()).isEqualTo(16);
        }
        assertThat(progress.restart()).isFalse();
    }

    @Test
    void acknowledgedBlockResetsRetryBudgetAndFinalBlockUsesZero() {
        RcxDownloadProgress progress = new RcxDownloadProgress(129);
        progress.restart();
        assertThat(progress.accept(-1)).isTrue();
        assertThat(progress.accept(-1)).isTrue();
        assertThat(progress.accept(0)).isTrue();
        assertThat(progress.accept(-1)).isTrue();
        assertThat(progress.accept(-1)).isTrue();
        assertThat(progress.accept(0)).isTrue();
        assertThat(progress.offset()).isEqualTo(128);
        assertThat(progress.size()).isEqualTo(1);
        assertThat(progress.index()).isZero();
        assertThat(progress.complete()).isFalse();
        assertThat(progress.accept(0)).isTrue();
        assertThat(progress.complete()).isTrue();
        assertThat(progress.offset()).isEqualTo(129);
    }

    @Test
    void finalBlockAndProtocolErrorsRequireRestartWithoutAdvancing() {
        RcxDownloadProgress progress = new RcxDownloadProgress(64);
        progress.restart();
        assertThat(progress.index()).isZero();
        assertThat(progress.accept(-1)).isFalse();
        assertThat(progress.offset()).isZero();
        assertThat(progress.complete()).isFalse();
        progress.restart();
        assertThat(progress.accept(3)).isFalse();
        assertThat(progress.offset()).isZero();
        progress.restart();
        assertThat(progress.accept(6)).isFalse();
        assertThat(progress.offset()).isZero();
    }
}

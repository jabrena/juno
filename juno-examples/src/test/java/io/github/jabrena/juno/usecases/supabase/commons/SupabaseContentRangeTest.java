package io.github.jabrena.juno.usecases.supabase.commons;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseContentRangeTest {

    @Test
    void readsTheCaseInsensitiveExactTotalFromContentRange() {
        SupabaseResponse response = response("Date: today\r\ncontent-range: 0-39/105\r\n");

        assertThat(SupabaseContentRange.total(response)).isEqualTo(105);
    }

    @Test
    void reportsUnknownForAnAbsentOrEstimatedTotal() {
        assertThat(SupabaseContentRange.total(response("Date: today\r\n"))).isNegative();
        assertThat(SupabaseContentRange.total(response("Content-Range: 0-39/*\r\n"))).isNegative();
    }

    private static SupabaseResponse response(String text) {
        byte[] headers = text.getBytes(StandardCharsets.US_ASCII);
        int[] status = {200, headers.length};
        return new SupabaseResponse(new byte[1], 1, headers, headers.length, status);
    }
}

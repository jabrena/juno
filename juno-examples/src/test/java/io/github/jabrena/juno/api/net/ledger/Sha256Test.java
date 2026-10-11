package io.github.jabrena.juno.api.net.ledger;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256Test {
    @Test
    void matchesTheFipsTestVectors() {
        assertThat(digest(new byte[0], 0))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(digest("abc".getBytes(), 3))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void matchesTheJdkForEveryLengthAcrossThePaddingBoundaries() throws NoSuchAlgorithmException {
        byte[] message = new byte[200];
        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) (i * 37 + 11);
        }
        for (int length = 0; length <= message.length; length++) {
            MessageDigest jdk = MessageDigest.getInstance("SHA-256");
            jdk.update(message, 0, length);

            assertThat(digest(message, length)).as("length %d", length)
                    .isEqualTo(HexFormat.of().formatHex(jdk.digest()));
        }
    }

    private static String digest(byte[] message, int length) {
        byte[] digest = new byte[Sha256.DIGEST_SIZE];
        Sha256.digest(message, length, digest, new byte[Sha256.BLOCK_SIZE], new int[Sha256.SCHEDULE_SIZE],
                new int[Sha256.STATE_SIZE]);
        return HexFormat.of().formatHex(digest);
    }

    @Test
    void finishingFromAMidstateMatchesHashingTheWholeHeader() {
        byte[] header = new byte[100];
        for (int i = 0; i < header.length; i++) {
            header[i] = (byte) (i * 13 + 5);
        }
        byte[] block = new byte[Sha256.BLOCK_SIZE];
        int[] schedule = new int[Sha256.SCHEDULE_SIZE];
        int[] state = new int[Sha256.STATE_SIZE];
        byte[] whole = new byte[Sha256.DIGEST_SIZE];
        byte[] resumed = new byte[Sha256.DIGEST_SIZE];

        Sha256.digest(header, 10, 80, whole, block, schedule, state);
        Sha256.initialize(state);
        Sha256.absorb(header, 10, block, schedule, state);
        Sha256.finish(header, 74, 16, 80, resumed, block, schedule, state);

        assertThat(resumed).isEqualTo(whole);
        byte[] copy = new byte[80];
        System.arraycopy(header, 10, copy, 0, 80);
        assertThat(digest(copy, 80)).isEqualTo(HexFormat.of().formatHex(whole));
    }
}

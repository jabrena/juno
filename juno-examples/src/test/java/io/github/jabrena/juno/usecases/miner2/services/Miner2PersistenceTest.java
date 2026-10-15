package io.github.jabrena.juno.usecases.miner2.services;

import io.github.jabrena.juno.api.io.SdCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Miner2PersistenceTest {
    @AfterEach
    void removeCard() {
        SdCard.reset();
    }

    @Test
    void reusesTheUuidPreviouslySavedOnTheCard() {
        SdCard.present = true;
        SdCard.appendText("MINER2.ID", "123e4567-e89b-12d3-a456-426614174000\n");

        Miner2Persistence persistence = new Miner2Persistence();

        assertThat(persistence.loadOrCreateIdentity())
                .isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(persistence.isReady()).isTrue();
    }

    @Test
    void createsAndSavesAUuidWhenTheCardHasNoIdentity() {
        SdCard.present = true;

        UUID created = new Miner2Persistence().loadOrCreateIdentity();
        UUID loaded = new Miner2Persistence().loadOrCreateIdentity();

        assertThat(created.version()).isEqualTo(4);
        assertThat(loaded).isEqualTo(created);
    }

    @Test
    void replacesAnInvalidSavedIdentity() {
        SdCard.present = true;
        SdCard.appendText("MINER2.ID", "not-a-uuid\n");

        UUID replacement = new Miner2Persistence().loadOrCreateIdentity();

        assertThat(new Miner2Persistence().loadOrCreateIdentity()).isEqualTo(replacement);
    }

    @Test
    void stillCreatesAnIdentityWithoutAnSdCard() {
        Miner2Persistence persistence = new Miner2Persistence();

        assertThat(persistence.loadOrCreateIdentity().version()).isEqualTo(4);
        assertThat(persistence.isReady()).isFalse();
    }
}

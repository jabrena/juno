package io.github.jabrena.juno.usecases.miner.services;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Miner2PersistenceTest {
    private final EmulatedSdCard sdCard = new EmulatedSdCard();

    @Test
    void reusesTheUuidPreviouslySavedOnTheCard() {
        sdCard.insert();
        sdCard.appendText("MINER2.ID", "123e4567-e89b-12d3-a456-426614174000\n");

        Miner2Persistence persistence = new Miner2Persistence(sdCard);

        assertThat(persistence.loadOrCreateIdentity())
                .isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(persistence.isReady()).isTrue();
    }

    @Test
    void createsAndSavesAUuidWhenTheCardHasNoIdentity() {
        sdCard.insert();

        UUID created = new Miner2Persistence(sdCard).loadOrCreateIdentity();
        UUID loaded = new Miner2Persistence(sdCard).loadOrCreateIdentity();

        assertThat(created.version()).isEqualTo(4);
        assertThat(loaded).isEqualTo(created);
    }

    @Test
    void replacesAnInvalidSavedIdentity() {
        sdCard.insert();
        sdCard.appendText("MINER2.ID", "not-a-uuid\n");

        UUID replacement = new Miner2Persistence(sdCard).loadOrCreateIdentity();

        assertThat(new Miner2Persistence(sdCard).loadOrCreateIdentity()).isEqualTo(replacement);
    }

    @Test
    void stillCreatesAnIdentityWithoutAnSdCard() {
        Miner2Persistence persistence = new Miner2Persistence(sdCard);

        assertThat(persistence.loadOrCreateIdentity().version()).isEqualTo(4);
        assertThat(persistence.isReady()).isFalse();
    }

    @Test
    void keepsTheColorSavedBesideTheIdentity() {
        sdCard.insert();
        Miner2Persistence persistence = new Miner2Persistence(sdCard);
        persistence.loadOrCreateIdentity();
        assertThat(persistence.loadColor(8)).as("nothing saved yet").isEqualTo(-1);

        persistence.saveColor(5);
        persistence.saveColor(6);

        Miner2Persistence restarted = new Miner2Persistence(sdCard);
        restarted.loadOrCreateIdentity();
        assertThat(restarted.loadColor(8)).isEqualTo(6);
        assertThat(restarted.loadColor(6)).as("not a color below the limit").isEqualTo(-1);
    }

    @Test
    void hasNoColorWithoutAnSdCard() {
        Miner2Persistence persistence = new Miner2Persistence(sdCard);
        persistence.loadOrCreateIdentity();
        persistence.saveColor(3);

        assertThat(persistence.loadColor(8)).isEqualTo(-1);
    }
}

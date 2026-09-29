package io.github.jabrena.juno.games.pokemon;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PokemonProtocolTest {
    @Test
    void writesAndRecognizesABattlePacket() {
        byte[] packet = new byte[PokemonProtocol.PACKET_SIZE];

        PokemonProtocol.write(packet, PokemonProtocol.ATTACK, PokemonProtocol.PIKACHU, 257, 27);

        assertThat(PokemonProtocol.valid(packet, packet.length)).isTrue();
        assertThat(PokemonProtocol.unsigned(packet[PokemonProtocol.TYPE])).isEqualTo(PokemonProtocol.ATTACK);
        assertThat(PokemonProtocol.unsigned(packet[PokemonProtocol.POKEMON])).isEqualTo(PokemonProtocol.PIKACHU);
        assertThat(PokemonProtocol.unsigned(packet[PokemonProtocol.SEQUENCE])).isEqualTo(1);
        assertThat(PokemonProtocol.unsigned(packet[PokemonProtocol.VALUE])).isEqualTo(27);
    }

    @Test
    void rejectsWrongLengthMagicVersionAndService() {
        byte[] packet = new byte[PokemonProtocol.PACKET_SIZE];
        PokemonProtocol.write(packet, PokemonProtocol.DISCOVER, PokemonProtocol.SQUIRTLE, 0, 0);

        assertThat(PokemonProtocol.valid(packet, packet.length - 1)).isFalse();
        packet[0] = 'X';
        assertThat(PokemonProtocol.valid(packet, packet.length)).isFalse();
        PokemonProtocol.write(packet, PokemonProtocol.DISCOVER, PokemonProtocol.SQUIRTLE, 0, 0);
        packet[4] = 2;
        assertThat(PokemonProtocol.valid(packet, packet.length)).isFalse();
        PokemonProtocol.write(packet, PokemonProtocol.DISCOVER, PokemonProtocol.SQUIRTLE, 0, 0);
        packet[5] = 2;
        assertThat(PokemonProtocol.valid(packet, packet.length)).isFalse();
    }

    @Test
    void damageAlwaysMakesProgressAtTheDefenseBoundary() {
        assertThat(PokemonProtocol.damage(20, 10)).isEqualTo(10);
        assertThat(PokemonProtocol.damage(10, 10)).isEqualTo(1);
        assertThat(PokemonProtocol.damage(1, 100)).isEqualTo(1);
    }
}

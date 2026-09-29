package io.github.jabrena.juno.games.pokemon;

/** Fixed-size wire protocol and battle rules shared by both boards. */
final class PokemonProtocol {
    static final int PIKACHU = 1;
    static final int CHARMANDER = 2;
    static final int SQUIRTLE = 3;

    static final int DISCOVER = 1;
    static final int HERE = 2;
    static final int START = 3;
    static final int ATTACK = 4;
    static final int FAINTED = 5;

    static final int PACKET_SIZE = 10;
    static final int TYPE = 6;
    static final int POKEMON = 7;
    static final int SEQUENCE = 8;
    static final int VALUE = 9;

    private static final int VERSION = 1;
    private static final int BATTLE_SERVICE = 1;

    private PokemonProtocol() {
    }

    static void write(byte[] packet, int type, int pokemon, int sequence, int value) {
        packet[0] = 'J';
        packet[1] = 'U';
        packet[2] = 'N';
        packet[3] = 'O';
        packet[4] = VERSION;
        packet[5] = BATTLE_SERVICE;
        packet[TYPE] = (byte) type;
        packet[POKEMON] = (byte) pokemon;
        packet[SEQUENCE] = (byte) sequence;
        packet[VALUE] = (byte) value;
    }

    static boolean valid(byte[] packet, int length) {
        return length == PACKET_SIZE
                && packet[0] == 'J' && packet[1] == 'U' && packet[2] == 'N' && packet[3] == 'O'
                && unsigned(packet[4]) == VERSION && unsigned(packet[5]) == BATTLE_SERVICE;
    }

    static int unsigned(byte value) {
        return value & 0xff;
    }

    static String name(int pokemon) {
        if (pokemon == PIKACHU) {
            return "PIKACHU";
        }
        if (pokemon == CHARMANDER) {
            return "CHARMANDER";
        }
        return "SQUIRTLE";
    }

    static int hitPoints(int pokemon) {
        if (pokemon == PIKACHU) {
            return 92;
        }
        if (pokemon == CHARMANDER) {
            return 100;
        }
        return 108;
    }

    static int attack(int pokemon) {
        if (pokemon == PIKACHU) {
            return 27;
        }
        if (pokemon == CHARMANDER) {
            return 25;
        }
        return 22;
    }

    static int defense(int pokemon) {
        if (pokemon == PIKACHU) {
            return 12;
        }
        if (pokemon == CHARMANDER) {
            return 15;
        }
        return 18;
    }

    static int damage(int attack, int defense) {
        int damage = attack - defense;
        return damage < 1 ? 1 : damage;
    }
}

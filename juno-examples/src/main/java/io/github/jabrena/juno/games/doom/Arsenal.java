package io.github.jabrena.juno.games.doom;

/**
 * How the CPU weighs the weapons it carries: the damage per frame each can be expected to deal at a monster's distance,
 * so {@link Combat} raises the one that hits hardest there.
 */
final class Arsenal {
    private Arsenal() {
    }

    /**
     * The damage per frame {@code weapon} can be expected to deal to a monster {@code distance} away, for the CPU to
     * pick the hardest-hitting weapon it can use; 0 when it is not carried, has no ammo, or must not be used there:
     * the fist and the chainsaw out of reach, a rocket or the BFG's ball so close its blast would hurt the marine.
     */
    static float damageRate(int weapon, float distance) {
        if (!Weapon.usable(weapon)) {
            return 0f;
        }
        // The share of pellets that hit narrows with distance: a monster ~40 units wide against the spread.
        float width = (float) Math.atan(20f / Math.max(distance, 1f));
        return switch (weapon) {
            case Weapon.FIST -> distance < Weapon.MELEE ? 1.1f : 0.05f;
            case Weapon.CHAINSAW -> distance < 2 * Weapon.MELEE ? 7f : 0.05f;
            case Weapon.SHOTGUN -> 70f / Weapon.CYCLE[Weapon.SHOTGUN] * Math.min(1f, width / 0.1f);
            case Weapon.CHAINGUN -> 10f / Weapon.CYCLE[Weapon.CHAINGUN] * Math.min(1f, width / 0.05f);
            case Weapon.LAUNCHER -> distance < 2 * Weapon.SPLASH ? 0f : 90f / Weapon.CYCLE[Weapon.LAUNCHER];
            case Weapon.PLASMA -> 22.5f / Weapon.CYCLE[Weapon.PLASMA];
            case Weapon.BFG -> distance < 2 * Weapon.SPLASH ? 0f : 600f / Weapon.CYCLE[Weapon.BFG];
            default -> 10f / Weapon.CYCLE[Weapon.PISTOL];
        };
    }

    /** The carried weapon that deals the most damage to a monster {@code distance} away. */
    static int best(float distance) {
        int best = Weapon.FIST;
        float bestRate = -1f;
        for (int weapon = 0; weapon < Weapon.WEAPONS; weapon++) {
            float rate = damageRate(weapon, distance);
            if (rate > bestRate) {
                best = weapon;
                bestRate = rate;
            }
        }
        return best;
    }
}

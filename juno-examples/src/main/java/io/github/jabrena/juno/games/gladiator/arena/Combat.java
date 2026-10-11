package io.github.jabrena.juno.games.gladiator.arena;


/** The two ways a gladiator hurts or disables another in the arena: a melee swing and a RETIARIUS's net. */
public final class Combat {
    private Combat() {
    }

    /** A melee swing by {@code attacker}: it lands when the victim is in reach, in front and in clear sight. */
    static void strike(Match match, int attacker, int now) {
        Side striker = match.side(attacker);
        Side victim = match.side(1 - attacker);
        if (!victim.present()) {
            return;
        }
        float dx = victim.x() - striker.x();
        float dy = victim.y() - striker.y();
        int reach = Arms.reach(striker.kind());
        if (dx * dx + dy * dy > reach * reach) {
            return;
        }
        float off = Eyes.wrap((float) Math.atan2(dy, dx) - striker.facing());
        if (Math.abs(off) > Match.STRIKE_ARC || !match.coliseum().clearSight(striker.x(), striker.y(), victim.x(), victim.y())) {
            return;
        }
        match.wound(attacker, Arms.damage(striker.kind()), true, now);
    }

    /** A RETIARIUS casts its one net of the round; an opponent in front, near and in sight is rooted. */
    static void castNet(Match match, int slot, int now) {
        Side caster = match.side(slot);
        if (!caster.netReady()) {
            return;
        }
        caster.useNet();
        Side victim = match.side(1 - slot);
        float dx = victim.x() - caster.x();
        float dy = victim.y() - caster.y();
        float off = Eyes.wrap((float) Math.atan2(dy, dx) - caster.facing());
        if (victim.present() && dx * dx + dy * dy <= (Arms.NET_MAX + 30) * (Arms.NET_MAX + 30)
                && Math.abs(off) <= Arms.NET_AIM && match.coliseum().clearSight(caster.x(), caster.y(), victim.x(), victim.y())) {
            victim.root(now + Arms.netMillis(victim.kind()));
        }
    }
}

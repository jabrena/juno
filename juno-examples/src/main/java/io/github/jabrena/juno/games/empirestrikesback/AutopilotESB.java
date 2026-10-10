package io.github.jabrena.juno.games.empirestrikesback;

import io.github.jabrena.juno.api.Random;

import static io.github.jabrena.juno.games.empirestrikesback.Entities.ENTITIES;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_AUX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SR;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_STRIDE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SX;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_SY;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_TYPE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_X;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Y;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.E_Z;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.FIREBALL_REACH;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ASTEROID;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ATAT;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_ATST;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_FIREBALL;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_PROBE;
import static io.github.jabrena.juno.games.empirestrikesback.Entities.T_TIE;

/**
 * The CPU at the controls, flying like a person rather than a machine: it lets targets come within
 * {@value #CPU_RANGE} before engaging the nearest (a probe droid, walker, TIE fighter, fireball, or an
 * asteroid within 900), sweeps the crosshair towards it at {@value #CPU_AIM_SPEED} pixels a frame,
 * and fires, at most every {@value #CPU_FIRE_FRAMES} frames, once the crosshair is on its aim point.
 * About {@value #CPU_LATE_PERCENT}% of the time it is slow to notice a new target (anything but a
 * fireball) and only reacts once it is within 600 to 1100. About {@value #CPU_MISS_PERCENT}% of its
 * shots at anything but a fireball are aimed just past the target and miss. The crosshair also steers
 * the craft, so when a fireball or asteroid on a collision course comes within {@value #CPU_EVADE_Z},
 * it dodges first (see {@link #dodge}).
 */
final class AutopilotESB {
    private static final int CPU_AIM_SPEED = 20;
    private static final int CPU_FIRE_FRAMES = 8;
    private static final int CPU_RANGE = 2000;
    private static final int CPU_MISS_PERCENT = 10;
    /** How often the CPU is slow to notice a new target, and lets it come much closer first. */
    private static final int CPU_LATE_PERCENT = 30;
    /** How near a fireball or asteroid on a collision course must be before the CPU dodges it. */
    private static final int CPU_EVADE_Z = 700;

    private static int aimOffsetX;
    private static int aimOffsetY;
    /** The target the CPU last picked, and how near it must come before the CPU reacts. */
    private static int cpuTarget = -1;
    private static int cpuReaction;

    private AutopilotESB() {
    }

    static void fly(int[] ents) {
        int target = nearestTarget(ents);
        if (target >= 0 && target != cpuTarget) {
            notice(ents, target);
        }
        if (target >= 0 && ents[target * E_STRIDE + E_Z] >= cpuReaction) {
            target = -1;
        }
        boolean trigger = Session.frame % CPU_FIRE_FRAMES == 0;
        if (trigger) {
            chooseAim(ents, target);
        }
        int aimX = Camera.CENTER_X;
        int aimY = Camera.CENTER_Y;
        if (target >= 0) {
            aimX = ents[target * E_STRIDE + E_SX] + aimOffsetX;
            aimY = ents[target * E_STRIDE + E_SY] + aimOffsetY;
        }
        // Dodge first: the crosshair steers the craft, so swing it away from whatever is about to hit.
        if (underThreat(ents)) {
            // A dodge is a flick of the finger to wherever steers clear of every close threat.
            dodge(ents);
            return;
        }
        if (target < 0) {
            return;
        }
        aimX = Camera.clamp(aimX, 8, DisplayList.WIDTH - 9);
        aimY = Camera.clamp(aimY, DisplayList.HEADER + 8, DisplayList.HEIGHT - 9);
        Controls.crossX = Controls.crossX + Camera.clamp(aimX - Controls.crossX, -CPU_AIM_SPEED, CPU_AIM_SPEED);
        Controls.crossY = Controls.crossY + Camera.clamp(aimY - Controls.crossY, -CPU_AIM_SPEED, CPU_AIM_SPEED);
        if (trigger && Math.abs(aimX - Controls.crossX) + Math.abs(aimY - Controls.crossY) <= 4) {
            Combat.fire(ents);
        }
    }

    /** The nearest target within {@value #CPU_RANGE}, or -1. */
    private static int nearestTarget(int[] ents) {
        int target = -1;
        int nearest = CPU_RANGE;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            boolean wanted = type == T_PROBE || type == T_ATAT || type == T_ATST || type == T_TIE
                    || type == T_FIREBALL || (type == T_ASTEROID && ents[b + E_Z] < 900);
            if (wanted && ents[b + E_SR] > 0 && ents[b + E_Z] < nearest) {
                target = slot;
                nearest = ents[b + E_Z];
            }
        }
        return target;
    }

    /** A new target: now and then the CPU is slow to notice it, as a person would be. */
    private static void notice(int[] ents, int target) {
        cpuTarget = target;
        boolean late = ents[target * E_STRIDE + E_TYPE] != T_FIREBALL && Random.nextInt(100) < CPU_LATE_PERCENT;
        cpuReaction = late ? Random.nextInt(600, 1100) : CPU_RANGE;
    }

    /** Each shot is aimed true or, now and then, just past the edge of the target. */
    private static void chooseAim(int[] ents, int target) {
        aimOffsetX = 0;
        aimOffsetY = 0;
        if (target >= 0 && ents[target * E_STRIDE + E_TYPE] != T_FIREBALL && Random.nextInt(100) < CPU_MISS_PERCENT) {
            int off = ents[target * E_STRIDE + E_SR] + Random.nextInt(6, 20);
            aimOffsetX = Random.nextInt(2) == 0 ? -off : off;
            aimOffsetY = Random.nextInt(-off, off + 1);
        }
    }

    /** Whether a fireball or asteroid within {@value #CPU_EVADE_Z} is on a collision course. */
    private static boolean underThreat(int[] ents) {
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            int reach = type == T_FIREBALL ? FIREBALL_REACH : ents[b + E_AUX];
            if ((type == T_FIREBALL || type == T_ASTEROID) && ents[b + E_Z] < CPU_EVADE_Z
                    && Math.abs(ents[b + E_X] - Camera.camX) < reach + 60
                    && Math.abs(ents[b + E_Y] - Camera.camY) < reach + 60) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries a grid of crosshair positions and flicks the crosshair to the one that steers the craft
     * furthest from every fireball and asteroid within {@value #CPU_EVADE_Z}.
     */
    private static void dodge(int[] ents) {
        int bestX = Controls.crossX;
        int bestY = Controls.crossY;
        int bestScore = Integer.MIN_VALUE;
        for (int column = 0; column < 5; column++) {
            for (int row = 0; row < 3; row++) {
                int x = 8 + column * (DisplayList.WIDTH - 17) / 4;
                int y = DisplayList.HEADER + 8 + row * (DisplayList.HEIGHT - DisplayList.HEADER - 17) / 2;
                int clearance = clearance(ents, Camera.headingX(x), Camera.headingY(y));
                // Prefer staying close to where the crosshair already is, all else equal.
                int score = clearance * 4 - Math.abs(x - Controls.crossX) - Math.abs(y - Controls.crossY);
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        Controls.crossX = bestX;
        Controls.crossY = bestY;
    }

    /** How far a craft heading for (headX, headY) passes from the closest fireball or asteroid. */
    private static int clearance(int[] ents, int headX, int headY) {
        int clearance = Integer.MAX_VALUE;
        for (int slot = 0; slot < ENTITIES; slot++) {
            int b = slot * E_STRIDE;
            int type = ents[b + E_TYPE];
            if ((type != T_FIREBALL && type != T_ASTEROID) || ents[b + E_Z] >= CPU_EVADE_Z) {
                continue;
            }
            int reach = type == T_FIREBALL ? FIREBALL_REACH : ents[b + E_AUX];
            int gap = Math.max(Math.abs(ents[b + E_X] - headX), Math.abs(ents[b + E_Y] - headY)) - reach;
            clearance = Math.min(clearance, gap);
        }
        return clearance;
    }
}

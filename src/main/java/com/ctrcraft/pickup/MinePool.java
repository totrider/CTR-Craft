package com.ctrcraft.pickup;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.ctrcraft.track.RaceManager;

import net.minecraft.server.level.ServerLevel;

/**
 * The placed weapons (TNT and Nitro crates, beakers) that are lying about, as the original's mine pool keeps them (RB_MinePool.c): ten at a time, and when an eleventh is
 * placed the oldest one bursts (RB_GenericMine_ThTick, "destroyed from MinePool overflow"); one that is hit leaves the pool. The original has one pool for a race. Here:
 * <ul>
 * <li><b>in a race</b>, one pool for the race, whoever placed the weapon; all of it is cleared when the race ends;</li>
 * <li><b>in free roam</b>, one pool for each player (ten of their own), and a weapon that nobody has touched goes away after {@link #FREE_ROAM_LIFETIME_TICKS}.</li>
 * </ul>
 */
public final class MinePool {
    private MinePool() {}

    /** RB_MinePool_Capacity: 10 in a race. */
    public static final int CAPACITY = 10;
    /** A free-roam weapon nobody touches goes away after five minutes (the original's has no time limit: its mines end with the race). */
    public static final int FREE_ROAM_LIFETIME_TICKS = 6000;

    /** A weapon in the pool. */
    public interface Mine {
        /** The pool is full: this is the oldest one and bursts (a harmless blow-up with its sound). */
        void overflow(ServerLevel level);

        /** The race is over (or the pool is cleared): gone, quietly. */
        void vanish(ServerLevel level);

        boolean isGone();
    }

    private static final Map<String, Deque<Mine>> POOLS = new HashMap<>();

    /** The pool a weapon of this player belongs to: the race they are in, else their own free-roam one. */
    public static String poolOf(UUID player) {
        String race = RaceManager.raceIdOf(player);
        return race != null ? "race:" + race : "free:" + player;
    }

    public static boolean inRace(String pool) {
        return pool.startsWith("race:");
    }

    public static void add(String pool, Mine mine, ServerLevel level) {
        Deque<Mine> q = POOLS.computeIfAbsent(pool, k -> new ArrayDeque<>());
        q.removeIf(Mine::isGone);
        while (q.size() >= CAPACITY) q.pollFirst().overflow(level);
        q.addLast(mine);
    }

    public static void remove(String pool, Mine mine) {
        Deque<Mine> q = POOLS.get(pool);
        if (q != null) q.remove(mine);
    }

    /** The race's weapons are cleared when it ends. */
    public static void clearRace(String raceId, ServerLevel level) {
        Deque<Mine> q = POOLS.remove("race:" + raceId);
        if (q != null) for (Mine m : q) if (!m.isGone()) m.vanish(level);
    }

    public static void clearAll() {
        POOLS.clear();
    }
}

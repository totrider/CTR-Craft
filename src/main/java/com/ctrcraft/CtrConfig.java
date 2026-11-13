package com.ctrcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings (config/ctrcraft-server.toml in the world's serverconfig folder). */
public final class CtrConfig {
    private CtrConfig() {}

    /** Who may place and break track nodes, link them and run test drives. A claims mod (Open Parties and Claims) hooks in later. */
    public enum EditPolicy { CREATIVE_OR_OPERATOR, EVERYONE }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<EditPolicy> TRACK_EDITING;
    public static final ModConfigSpec.IntValue RACE_MIN_RACERS;
    public static final ModConfigSpec.DoubleValue PICKUP_TRACK_RADIUS;
    public static final ModConfigSpec.BooleanValue ENTITY_CONTACT;
    public static final ModConfigSpec.BooleanValue ENTITY_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENTITY_DAMAGE_PLAYERS;
    public static final ModConfigSpec.DoubleValue ENTITY_DAMAGE_SCALE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        TRACK_EDITING = builder
                .comment("Who may edit race tracks (place and break track nodes, link them with the Track Tool).",
                        "CREATIVE_OR_OPERATOR: players in creative mode or with operator rights. EVERYONE: anyone holding the items.")
                .defineEnum("trackEditing", EditPolicy.CREATIVE_OR_OPERATOR);
        RACE_MIN_RACERS = builder
                .comment("How many karts have to be inside a Start node's area for a race to start by itself (the countdown then begins).")
                .defineInRange("raceMinRacers", 1, 1, 16);
        PICKUP_TRACK_RADIUS = builder
                .comment("A fruit, crate or power-up spot belongs to a track when it is within this many blocks of the track's line (the links between its nodes);",
                        "its pickup then appears while a race on that track is on. A spot with a redstone signal is always on.")
                .defineInRange("pickupTrackRadius", 96.0, 8.0, 512.0);
        builder.push("entities");
        ENTITY_CONTACT = builder
                .comment("Karts bump into other entities (mobs, boats, minecarts, players on foot): the kart bounces and the entity is thrown.",
                        "Kart against kart is CTR's own bump and is not affected by this.")
                .define("contact", true);
        ENTITY_DAMAGE = builder
                .comment("A kart hurts the living entities it hits at speed (the driver gets the credit). Needs contact to be on.")
                .define("damage", true);
        ENTITY_DAMAGE_PLAYERS = builder
                .comment("Players on foot can be hurt too. Still needs the server's PvP to be on, and follows teams and claims rules for harming players.")
                .define("damagePlayers", true);
        ENTITY_DAMAGE_SCALE = builder
                .comment("Multiplier on the damage: 1 is about 1 heart for every 2 blocks/s of speed over 6 (12 hp, 6 hearts, at top speed).")
                .defineInRange("damageScale", 1.0, 0.0, 10.0);
        builder.pop();
        SPEC = builder.build();
    }
}

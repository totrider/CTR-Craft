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
    public static final ModConfigSpec.DoubleValue RACE_START_RADIUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        TRACK_EDITING = builder
                .comment("Who may edit race tracks (place and break track nodes, link them with the Track Tool).",
                        "CREATIVE_OR_OPERATOR: players in creative mode or with operator rights. EVERYONE: anyone holding the items.")
                .defineEnum("trackEditing", EditPolicy.CREATIVE_OR_OPERATOR);
        RACE_MIN_RACERS = builder
                .comment("How many karts have to be inside a Start node's area for a race to start by itself (the countdown then begins).")
                .defineInRange("raceMinRacers", 1, 1, 16);
        RACE_START_RADIUS = builder
                .comment("Radius, in blocks, of the sphere round a Start node in which karts count as entering a race.")
                .defineInRange("raceStartRadius", 24.0, 8.0, 64.0);
        SPEC = builder.build();
    }
}

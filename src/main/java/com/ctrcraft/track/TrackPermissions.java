package com.ctrcraft.track;

import com.ctrcraft.CtrConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one place that decides who may edit tracks. Today: the server setting (creative / operators, or everyone). Tomorrow: a claims
 * mod (Open Parties and Claims) can be asked here whether {@code pos} is the player's to edit, without touching anything else.
 */
public final class TrackPermissions {
    private TrackPermissions() {}

    public static boolean canEdit(ServerPlayer player, BlockPos pos) {
        return switch (CtrConfig.TRACK_EDITING.get()) {
            case EVERYONE -> true;
            case CREATIVE_OR_OPERATOR -> player.isCreative() || player.hasPermissions(2);
        };
    }
}

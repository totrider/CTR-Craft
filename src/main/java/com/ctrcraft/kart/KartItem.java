package com.ctrcraft.kart;

import com.ctrcraft.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Right-click a block to place a kart facing the way you look. */
public class KartItem extends Item {
    public KartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        Vec3 at = face == Direction.UP
                ? new Vec3(context.getClickLocation().x, pos.getY() + 1, context.getClickLocation().z)
                : Vec3.atBottomCenterOf(pos.relative(face));
        KartEntity kart = ModEntities.KART.get().create(serverLevel);
        if (kart == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() : 0.0F;
        kart.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        kart.setDrawnYawAngle(KartScale.yawToAngle(yaw));
        serverLevel.addFreshEntity(kart);
        ItemStack stack = context.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}

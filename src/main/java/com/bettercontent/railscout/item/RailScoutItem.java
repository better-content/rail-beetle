package com.bettercontent.railscout.item;

import com.bettercontent.railscout.RailScoutRegistries;
import com.bettercontent.railscout.entity.RailScoutEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BaseRailBlock;

public final class RailScoutItem extends Item {
    public RailScoutItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos railPos = context.getClickedPos();
        if (!(context.getLevel().getBlockState(railPos).getBlock() instanceof BaseRailBlock)) {
            return InteractionResult.FAIL;
        }
        if (context.getLevel() instanceof ServerLevel level) {
            RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(level);
            if (scout == null) return InteractionResult.FAIL;
            scout.setPos(railPos.getX() + 0.5, railPos.getY() + 0.0625, railPos.getZ() + 0.5);
            scout.setYRot(context.getRotation());
            level.addFreshEntity(scout);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}

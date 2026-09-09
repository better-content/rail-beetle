package com.bettercontent.railbeetle.item;

import com.bettercontent.railbeetle.RailBeetleRegistries;
import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BaseRailBlock;

public final class RailBeetleItem extends Item {
    public RailBeetleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos railPos = context.getClickedPos();
        if (!(context.getLevel().getBlockState(railPos).getBlock() instanceof BaseRailBlock)) {
            return InteractionResult.FAIL;
        }
        if (context.getLevel() instanceof ServerLevel level) {
            RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(level);
            if (beetle == null) return InteractionResult.FAIL;
            beetle.setPos(railPos.getX() + 0.5, railPos.getY() + 0.0625, railPos.getZ() + 0.5);
            beetle.setYRot(context.getRotation());
            beetle.setInitialHeading(Direction.fromYRot(context.getRotation()));
            level.addFreshEntity(beetle);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}

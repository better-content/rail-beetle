package com.bettercontent.railbeetle.navigation;

import com.bettercontent.railbeetle.RailBeetleTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class RouteObstructions {
    private RouteObstructions() {}

    public static boolean isOpenOrClearable(BlockState state) {
        return state.isAir() || isClearable(state);
    }

    public static boolean isClearable(BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.hasBlockEntity()
                && !state.is(BlockTags.CROPS)
                && (state.is(BlockTags.LEAVES) || state.is(RailBeetleTags.CLEARABLE_OBSTRUCTIONS));
    }
}

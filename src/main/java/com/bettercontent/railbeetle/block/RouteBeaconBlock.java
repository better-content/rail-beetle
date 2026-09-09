package com.bettercontent.railbeetle.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RouteBeaconBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            box(6, 0, 6, 10, 12, 10),
            box(4, 11, 4, 12, 14, 12),
            box(6, 14, 6, 10, 16, 10));

    public RouteBeaconBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}

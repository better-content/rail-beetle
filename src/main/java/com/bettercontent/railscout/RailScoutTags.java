package com.bettercontent.railscout;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class RailScoutTags {
    public static final TagKey<Item> USABLE_RAILS = TagKey.create(
            Registries.ITEM, new ResourceLocation(RailScoutMod.MOD_ID, "usable_rails"));
    public static final TagKey<Block> USABLE_RAIL_BLOCKS = TagKey.create(
            Registries.BLOCK, new ResourceLocation(RailScoutMod.MOD_ID, "usable_rails"));
    public static final TagKey<Item> FORGE_RAILS = TagKey.create(
            Registries.ITEM, new ResourceLocation("forge", "rails"));
    public static final TagKey<Block> FORGE_RAIL_BLOCKS = TagKey.create(
            Registries.BLOCK, new ResourceLocation("forge", "rails"));

    private RailScoutTags() {}
}

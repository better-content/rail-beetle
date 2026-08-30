package com.bettercontent.railscout;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class RailScoutTags {
    public static final TagKey<Item> USABLE_RAILS = TagKey.create(
            Registries.ITEM, new ResourceLocation(RailScoutMod.MOD_ID, "usable_rails"));

    private RailScoutTags() {}
}

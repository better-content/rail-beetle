package com.bettercontent.railbeetle.item;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StarterBeetlePackageTest {
    @Test void onlyTheExplicitAuthorMarkerRequestsStarterSupplies() {
        CompoundTag ordinary = new CompoundTag();
        assertFalse(StarterBeetlePackage.requested(ordinary));

        ordinary.putBoolean(StarterBeetlePackage.NBT_KEY, true);
        assertTrue(StarterBeetlePackage.requested(ordinary));
    }
}

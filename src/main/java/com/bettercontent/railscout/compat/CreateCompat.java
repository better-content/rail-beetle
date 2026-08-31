package com.bettercontent.railscout.compat;

import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraftforge.fml.ModList;

/** Keeps Create-only symbols out of the Rail Scout entity's mandatory class path. */
public final class CreateCompat {
    private CreateCompat() {}

    public static void setExternallyStalled(AbstractMinecart cart, boolean stalled) {
        if (ModList.get().isLoaded("create")) Loaded.setExternallyStalled(cart, stalled);
    }

    private static final class Loaded {
        private Loaded() {}

        private static void setExternallyStalled(AbstractMinecart cart, boolean stalled) {
            cart.getCapability(com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController
                            .MINECART_CONTROLLER_CAPABILITY)
                    .ifPresent(controller -> controller.setStalledExternally(stalled));
        }
    }
}

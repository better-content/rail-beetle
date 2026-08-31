package com.bettercontent.railscout.compat;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraftforge.fml.ModList;

/** Keeps Create-only symbols out of the Rail Scout entity's mandatory class path. */
public final class CreateCompat {
    private CreateCompat() {}

    public static void setExternallyStalled(AbstractMinecart cart, boolean stalled) {
        if (ModList.get().isLoaded("create")) Loaded.setExternallyStalled(cart, stalled);
    }

    public static void applyConsistTraction(
            AbstractMinecart poweredCart,
            Direction direction,
            double targetSpeed,
            double acceleration
    ) {
        if (ModList.get().isLoaded("create")) {
            Loaded.applyConsistTraction(poweredCart, direction, targetSpeed, acceleration);
        }
    }

    public static boolean isInSameConsist(AbstractMinecart first, net.minecraft.world.entity.Entity candidate) {
        return ModList.get().isLoaded("create") && candidate instanceof AbstractMinecart cart
                && Loaded.isInSameConsist(first, cart);
    }

    private static final class Loaded {
        private Loaded() {}

        private static void setExternallyStalled(AbstractMinecart cart, boolean stalled) {
            cart.getCapability(com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController
                            .MINECART_CONTROLLER_CAPABILITY)
                    .ifPresent(controller -> controller.setStalledExternally(stalled));
        }

        private static void applyConsistTraction(
                AbstractMinecart poweredCart,
                Direction direction,
                double targetSpeed,
                double acceleration
        ) {
            if (!(poweredCart.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
            java.util.ArrayDeque<AbstractMinecart> frontier = new java.util.ArrayDeque<>();
            java.util.Set<java.util.UUID> visited = new java.util.HashSet<>();
            frontier.add(poweredCart);
            visited.add(poweredCart.getUUID());
            while (!frontier.isEmpty()) {
                AbstractMinecart current = frontier.removeFirst();
                current.getCapability(com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController
                                .MINECART_CONTROLLER_CAPABILITY)
                        .ifPresent(controller -> {
                            for (boolean side : new boolean[]{false, true}) {
                                java.util.UUID coupledId = controller.getCoupledCart(side);
                                if (coupledId == null || !visited.add(coupledId)) continue;
                                if (level.getEntity(coupledId) instanceof AbstractMinecart coupled) {
                                    applyRailAlignedAcceleration(coupled, direction, targetSpeed, acceleration);
                                    frontier.addLast(coupled);
                                }
                            }
                        });
            }
        }

        private static boolean isInSameConsist(AbstractMinecart first, AbstractMinecart candidate) {
            if (!(first.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
            java.util.ArrayDeque<AbstractMinecart> frontier = new java.util.ArrayDeque<>();
            java.util.Set<java.util.UUID> visited = new java.util.HashSet<>();
            frontier.add(first);
            visited.add(first.getUUID());
            while (!frontier.isEmpty()) {
                AbstractMinecart current = frontier.removeFirst();
                if (current == candidate) return true;
                current.getCapability(com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController
                                .MINECART_CONTROLLER_CAPABILITY)
                        .ifPresent(controller -> {
                            for (boolean side : new boolean[]{false, true}) {
                                java.util.UUID coupledId = controller.getCoupledCart(side);
                                if (coupledId == null || !visited.add(coupledId)) continue;
                                if (level.getEntity(coupledId) instanceof AbstractMinecart coupled) frontier.addLast(coupled);
                            }
                        });
            }
            return false;
        }

        private static void applyRailAlignedAcceleration(
                AbstractMinecart cart,
                Direction direction,
                double targetSpeed,
                double acceleration
        ) {
            net.minecraft.world.phys.Vec3 motion = cart.getDeltaMovement();
            double along = motion.x * direction.getStepX() + motion.z * direction.getStepZ();
            double change = net.minecraft.util.Mth.clamp(targetSpeed - along, 0, acceleration);
            if (change <= 0) return;
            cart.setDeltaMovement(
                    motion.x + direction.getStepX() * change,
                    motion.y,
                    motion.z + direction.getStepZ() * change);
        }
    }
}

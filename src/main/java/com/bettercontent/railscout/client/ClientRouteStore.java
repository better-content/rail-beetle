package com.bettercontent.railscout.client;

import com.bettercontent.railscout.navigation.RouteProposal;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientRouteStore {
    private static final Map<Integer, List<RouteProposal>> ROUTES = new HashMap<>();

    private ClientRouteStore() {}

    public static void receive(int entityId, List<RouteProposal> proposals) {
        if (proposals.isEmpty()) ROUTES.remove(entityId);
        else ROUTES.put(entityId, List.copyOf(proposals));
    }

    public static Map<Integer, List<RouteProposal>> routes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            ROUTES.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
        }
        return Map.copyOf(ROUTES);
    }

    @Nullable
    public static Selection crosshairSelection() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 end = eye.add(minecraft.player.getLookAngle().scale(96.0));
        Selection best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Integer, List<RouteProposal>> entry : routes().entrySet()) {
            Entity scout = minecraft.level.getEntity(entry.getKey());
            if (scout == null || minecraft.player.distanceToSqr(scout) > 256.0) continue;
            for (RouteProposal route : entry.getValue()) {
                for (var step : route.steps()) {
                    Vec3 center = Vec3.atLowerCornerOf(step.railPos()).add(0.5, 0.18, 0.5);
                    var hit = new AABB(center, center).inflate(0.8).clip(eye, end);
                    if (hit.isPresent()) {
                        double distance = eye.distanceToSqr(hit.get());
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = new Selection(entry.getKey(), route);
                        }
                    }
                }
            }
        }
        return best;
    }

    public record Selection(int entityId, RouteProposal route) {}
}

package com.bettercontent.railbeetle.client;

import com.bettercontent.railbeetle.navigation.RouteProposal;
import com.bettercontent.railbeetle.navigation.RouteSupplyStatus;
import net.minecraft.client.Minecraft;
import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientRouteStore {
    private static final double MAX_SELECTION_ANGLE = Math.toRadians(2.0);
    private static final Map<Integer, RouteSet> ROUTES = new HashMap<>();
    private static int lastControlledBeetle = -1;
    @Nullable private static ClientLevel knownLevel;

    private ClientRouteStore() {}

    public static void receive(int entityId, List<RouteProposal> proposals) {
        receive(entityId, proposals, java.util.Collections.nCopies(proposals.size(), RouteSupplyStatus.READY), null);
    }

    public static void receive(int entityId, List<RouteProposal> proposals, @Nullable RouteProposal activeRoute) {
        receive(entityId, proposals, java.util.Collections.nCopies(proposals.size(), RouteSupplyStatus.READY), activeRoute);
    }

    public static void receive(int entityId, List<RouteProposal> proposals, List<RouteSupplyStatus> supplies,
                               @Nullable RouteProposal activeRoute) {
        knownLevel = Minecraft.getInstance().level;
        if (proposals.isEmpty() && activeRoute == null) ROUTES.remove(entityId);
        else {
            Map<Integer, RouteSupplyStatus> byRoute = new HashMap<>();
            for (int index = 0; index < proposals.size(); index++) {
                RouteSupplyStatus status = index < supplies.size() ? supplies.get(index) : RouteSupplyStatus.READY;
                byRoute.put(proposals.get(index).id(), status);
            }
            ROUTES.put(entityId, new RouteSet(List.copyOf(proposals), Map.copyOf(byRoute), activeRoute));
        }
        if (activeRoute != null && lastControlledBeetle < 0) lastControlledBeetle = entityId;
    }

    public static Map<Integer, RouteSet> routes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != knownLevel) {
            ROUTES.clear();
            lastControlledBeetle = -1;
            knownLevel = minecraft.level;
        }
        if (minecraft.level != null) {
            ROUTES.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
            if (!ROUTES.containsKey(lastControlledBeetle)) lastControlledBeetle = -1;
        }
        return Map.copyOf(ROUTES);
    }

    public static void rememberControlled(int entityId) {
        lastControlledBeetle = entityId;
    }

    @Nullable
    public static Selection crosshairSelection() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle().normalize();
        Selection best = null;
        double bestAngle = MAX_SELECTION_ANGLE;
        double bestAlongRay = Double.MAX_VALUE;
        for (Map.Entry<Integer, RouteSet> entry : routes().entrySet()) {
            var beetle = minecraft.level.getEntity(entry.getKey());
            if (!(beetle instanceof RailBeetleEntity)) continue;
            for (RouteProposal route : entry.getValue().proposals()) {
                Vec3 previous = Vec3.atLowerCornerOf(route.origin()).add(0.5, 0.2, 0.5);
                for (var step : route.steps()) {
                    Vec3 current = Vec3.atLowerCornerOf(step.railPos()).add(0.5, 0.2, 0.5);
                    RayDistance distance = distanceToLookRay(eye, look, previous, current);
                    if (distance != null && (distance.angle() < bestAngle
                            || distance.angle() == bestAngle && distance.alongRay() < bestAlongRay)) {
                        bestAngle = distance.angle();
                        bestAlongRay = distance.alongRay();
                        best = new Selection(entry.getKey(), route,
                                entry.getValue().supplies().getOrDefault(route.id(), RouteSupplyStatus.READY));
                    }
                    previous = current;
                }
            }
        }
        return best;
    }

    @Nullable
    public static ActionTarget contextualTarget() {
        Selection selected = crosshairSelection();
        if (selected != null) return new ActionTarget(selected.entityId(), Action.FOLLOW,
                selected.route(), selected.supply());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        RailBeetleEntity fallback = beetleWithActiveRoute(lastControlledBeetle);
        if (fallback == null) {
            for (Map.Entry<Integer, RouteSet> entry : routes().entrySet()) {
                if (entry.getValue().activeRoute() != null) {
                    RailBeetleEntity candidate = beetleWithActiveRoute(entry.getKey());
                    if (candidate != null) fallback = candidate;
                }
            }
        }
        if (fallback == null) return null;
        Action action = fallback.mode().moves() ? Action.STOP : Action.CLEAR;
        return new ActionTarget(fallback.getId(), action, null, RouteSupplyStatus.READY);
    }

    @Nullable
    private static RailBeetleEntity beetleWithActiveRoute(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        RouteSet set = ROUTES.get(entityId);
        if (set == null || set.activeRoute() == null || minecraft.level == null) return null;
        return minecraft.level.getEntity(entityId) instanceof RailBeetleEntity beetle ? beetle : null;
    }

    @Nullable
    private static RayDistance distanceToLookRay(Vec3 origin, Vec3 ray, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        Vec3 offset = origin.subtract(start);
        double a = segment.lengthSqr();
        if (a < 1.0e-8) return pointDistance(origin, ray, start);
        double b = segment.dot(ray);
        double c = ray.lengthSqr();
        double d = segment.dot(offset);
        double e = ray.dot(offset);
        double denominator = a * c - b * b;
        double segmentT = denominator < 1.0e-8 ? 0.0 : clamp((c * d - b * e) / denominator, 0.0, 1.0);
        double rayT = Math.max(0.0, (b * segmentT - e) / c);
        segmentT = clamp((d + b * rayT) / a, 0.0, 1.0);
        rayT = Math.max(0.0, (b * segmentT - e) / c);
        if (rayT <= 0.0) return null;
        Vec3 onSegment = start.add(segment.scale(segmentT));
        Vec3 onRay = origin.add(ray.scale(rayT));
        double angle = Math.atan2(onSegment.distanceTo(onRay), rayT);
        return angle <= MAX_SELECTION_ANGLE ? new RayDistance(angle, rayT) : null;
    }

    @Nullable
    private static RayDistance pointDistance(Vec3 origin, Vec3 ray, Vec3 point) {
        double along = point.subtract(origin).dot(ray);
        if (along <= 0.0) return null;
        double angle = Math.atan2(point.distanceTo(origin.add(ray.scale(along))), along);
        return angle <= MAX_SELECTION_ANGLE ? new RayDistance(angle, along) : null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Action { FOLLOW, STOP, CLEAR }
    public record RouteSet(List<RouteProposal> proposals, Map<Integer, RouteSupplyStatus> supplies,
                           @Nullable RouteProposal activeRoute) {}
    public record Selection(int entityId, RouteProposal route, RouteSupplyStatus supply) {}
    public record ActionTarget(int entityId, Action action, @Nullable RouteProposal route,
                               RouteSupplyStatus supply) {}
    private record RayDistance(double angle, double alongRay) {}
}

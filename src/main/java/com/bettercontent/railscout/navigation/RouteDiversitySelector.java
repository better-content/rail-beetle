package com.bettercontent.railscout.navigation;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class RouteDiversitySelector {
    private RouteDiversitySelector() {}

    public static <T> List<T> select(
            BlockPos origin,
            Collection<T> candidates,
            Function<T, BlockPos> endpoint,
            int limit
    ) {
        return select(origin, candidates, endpoint, limit, limit);
    }

    public static <T> List<T> select(
            BlockPos origin,
            Collection<T> candidates,
            Function<T, BlockPos> endpoint,
            int guaranteedCount,
            int limit
    ) {
        List<T> remaining = new ArrayList<>(candidates);
        List<T> selected = new ArrayList<>(Math.min(limit, remaining.size()));
        double farthestDistance = Math.sqrt(remaining.stream()
                .mapToDouble(value -> endpoint.apply(value).distSqr(origin))
                .max()
                .orElse(0.0));
        double extraRouteSpacing = Math.max(4.0, Math.min(16.0, Math.floor(farthestDistance / 4.0)));
        while (selected.size() < limit && !remaining.isEmpty()) {
            T best = remaining.stream().max(Comparator
                    .comparingDouble((T value) -> minimumDistanceSquared(origin, endpoint.apply(value), selected, endpoint))
                    .thenComparingInt(value -> endpoint.apply(value).getX())
                    .thenComparingInt(value -> endpoint.apply(value).getY())
                    .thenComparingInt(value -> endpoint.apply(value).getZ()))
                    .orElseThrow();
            if (selected.size() >= guaranteedCount
                    && minimumDistanceSquared(origin, endpoint.apply(best), selected, endpoint)
                    < extraRouteSpacing * extraRouteSpacing) {
                break;
            }
            selected.add(best);
            BlockPos chosen = endpoint.apply(best);
            remaining.removeIf(value -> endpoint.apply(value).equals(chosen));
        }
        return List.copyOf(selected);
    }

    private static <T> double minimumDistanceSquared(
            BlockPos origin,
            BlockPos candidate,
            List<T> selected,
            Function<T, BlockPos> endpoint
    ) {
        double minimum = candidate.distSqr(origin);
        for (T value : selected) {
            minimum = Math.min(minimum, candidate.distSqr(endpoint.apply(value)));
        }
        return minimum;
    }
}

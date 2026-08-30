package com.bettercontent.railscout.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TerrainRoutePlanner {
    public static final int DEFAULT_NODE_CAP = 32_768;

    private TerrainRoutePlanner() {}

    public static Session begin(Level level, BlockPos originRail, Direction forward, int railCap, long generation) {
        if (!forward.getAxis().isHorizontal()) {
            forward = Direction.NORTH;
        }
        return new Session(level, originRail.immutable(), forward, railCap, generation);
    }

    public static final class Session {
        private final Level level;
        private final BlockPos origin;
        private final int railCap;
        private final long generation;
        private final ArrayDeque<Node> frontier = new ArrayDeque<>();
        private final Set<Key> visited = new HashSet<>();
        private final Map<BlockPos, Node> endpoints = new LinkedHashMap<>();
        private boolean complete;
        private int examined;

        private Session(Level level, BlockPos origin, Direction forward, int railCap, long generation) {
            this.level = level;
            this.origin = origin;
            this.railCap = Math.max(1, railCap);
            this.generation = generation;
            Node root = new Node(origin, forward, 0, null, null);
            frontier.add(root);
            visited.add(new Key(origin, forward));
        }

        public boolean advance(int nodeBudget) {
            int budget = Math.max(1, nodeBudget);
            while (budget-- > 0 && !frontier.isEmpty() && examined < DEFAULT_NODE_CAP) {
                Node current = frontier.removeFirst();
                examined++;
                if (current.depth > 0) {
                    endpoints.putIfAbsent(current.pos, current);
                }
                if (current.depth >= railCap) {
                    continue;
                }
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (direction == current.incoming.getOpposite()) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.pos.relative(direction).offset(0, dy, 0);
                        Placement placement = inspect(next);
                        if (placement == null || !geometryAllowed(current, direction, dy)) {
                            continue;
                        }
                        Key key = new Key(next, direction);
                        if (!visited.add(key)) {
                            continue;
                        }
                        frontier.addLast(new Node(next.immutable(), direction, current.depth + 1, current, placement.supportPos));
                    }
                }
            }
            complete = frontier.isEmpty() || examined >= DEFAULT_NODE_CAP;
            return complete;
        }

        public boolean isComplete() {
            return complete;
        }

        public List<RouteProposal> proposals() {
            if (!complete) {
                throw new IllegalStateException("Planning is not complete");
            }
            List<Node> diverse = RouteDiversitySelector.select(origin, endpoints.values(), node -> node.pos, 3);
            List<RouteProposal> result = new ArrayList<>(diverse.size());
            for (int index = 0; index < diverse.size(); index++) {
                Node endpoint = diverse.get(index);
                List<Node> path = reconstruct(endpoint);
                List<RouteStep> steps = new ArrayList<>(path.size());
                for (int stepIndex = 0; stepIndex < path.size(); stepIndex++) {
                    BlockPos previous = stepIndex == 0 ? origin : path.get(stepIndex - 1).pos;
                    BlockPos current = path.get(stepIndex).pos;
                    BlockPos next = stepIndex + 1 < path.size() ? path.get(stepIndex + 1).pos : null;
                    steps.add(new RouteStep(current, shapeFor(previous, current, next), path.get(stepIndex).supportPos));
                }
                result.add(new RouteProposal(index, generation, endpoint.pos, steps));
            }
            return List.copyOf(result);
        }

        private boolean geometryAllowed(Node current, Direction direction, int dy) {
            if (dy != 0 && current.parent != null && current.incoming.getAxis() != direction.getAxis()) {
                return false;
            }
            if (current.parent != null) {
                int previousDy = current.pos.getY() - current.parent.pos.getY();
                if (previousDy != 0 && current.incoming.getAxis() != direction.getAxis()) {
                    return false;
                }
            }
            return true;
        }

        @Nullable
        private Placement inspect(BlockPos railPos) {
            BlockPos above = railPos.above();
            if (!level.isLoaded(railPos) || !level.isLoaded(above) || !level.isLoaded(railPos.below(2))) {
                return null;
            }
            BlockState railSpace = level.getBlockState(railPos);
            BlockState headSpace = level.getBlockState(above);
            if (!railSpace.isAir() || !headSpace.isAir()
                    || railSpace.getFluidState().getType() != Fluids.EMPTY
                    || headSpace.getFluidState().getType() != Fluids.EMPTY) {
                return null;
            }
            BlockPos floor = railPos.below();
            BlockState floorState = level.getBlockState(floor);
            if (floorState.isFaceSturdy(level, floor, Direction.UP) && !(floorState.getBlock() instanceof BaseRailBlock)) {
                return new Placement(null);
            }
            BlockPos lowerFloor = floor.below();
            BlockState lowerState = level.getBlockState(lowerFloor);
            if (floorState.isAir() && lowerState.isFaceSturdy(level, lowerFloor, Direction.UP)
                    && lowerState.getFluidState().getType() == Fluids.EMPTY) {
                return new Placement(floor.immutable());
            }
            return null;
        }
    }

    private static List<Node> reconstruct(Node endpoint) {
        ArrayDeque<Node> reversed = new ArrayDeque<>();
        for (Node cursor = endpoint; cursor.parent != null; cursor = cursor.parent) {
            reversed.addFirst(cursor);
        }
        return List.copyOf(reversed);
    }

    public static RailShape shapeFor(BlockPos previous, BlockPos current, @Nullable BlockPos next) {
        BlockPos neighbor = next == null ? previous : next;
        int dyPrev = previous.getY() - current.getY();
        int dyNext = neighbor.getY() - current.getY();
        Direction toPrev = horizontalDirection(current, previous);
        Direction toNext = horizontalDirection(current, neighbor);

        if (dyPrev > 0) return ascending(toPrev);
        if (dyNext > 0) return ascending(toNext);
        if (toPrev.getAxis() == toNext.getAxis()) {
            return toPrev.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        }
        boolean north = toPrev == Direction.NORTH || toNext == Direction.NORTH;
        boolean south = toPrev == Direction.SOUTH || toNext == Direction.SOUTH;
        boolean east = toPrev == Direction.EAST || toNext == Direction.EAST;
        if (south && east) return RailShape.SOUTH_EAST;
        if (south) return RailShape.SOUTH_WEST;
        if (north && east) return RailShape.NORTH_EAST;
        return RailShape.NORTH_WEST;
    }

    private static RailShape ascending(Direction direction) {
        return switch (direction) {
            case NORTH -> RailShape.ASCENDING_NORTH;
            case SOUTH -> RailShape.ASCENDING_SOUTH;
            case EAST -> RailShape.ASCENDING_EAST;
            case WEST -> RailShape.ASCENDING_WEST;
            default -> throw new IllegalArgumentException("Vertical rail direction");
        };
    }

    private static Direction horizontalDirection(BlockPos from, BlockPos to) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private record Placement(@Nullable BlockPos supportPos) {}
    private record Key(BlockPos pos, Direction incoming) {}
    private record Node(BlockPos pos, Direction incoming, int depth, @Nullable Node parent, @Nullable BlockPos supportPos) {}
}

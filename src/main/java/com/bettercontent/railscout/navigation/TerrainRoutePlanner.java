package com.bettercontent.railscout.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

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
        private final Direction originHeading;
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
            this.originHeading = forward;
            Node root = new Node(origin, forward, 0, null, null, false, 0);
            frontier.add(root);
            visited.add(new Key(origin, forward, false, 0));
        }

        public boolean advance(int nodeBudget) {
            int budget = Math.max(1, nodeBudget);
            while (budget-- > 0 && !frontier.isEmpty() && examined < DEFAULT_NODE_CAP) {
                Node current = frontier.removeFirst();
                examined++;
                if (current.depth > 0 && current.supportPos == null) {
                    endpoints.putIfAbsent(current.pos, current);
                }
                if (current.depth >= railCap) {
                    continue;
                }
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (current.parent == null && direction != current.incoming) {
                        continue;
                    }
                    if (direction == current.incoming.getOpposite()) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = current.pos.relative(direction).offset(0, dy, 0);
                        Placement placement = inspect(next);
                        if (placement == null
                                || !isolatedBridgeAllowed(current, direction, dy, next, placement)
                                || !geometryAllowed(current, direction, dy)
                                || !adjacencyAllowed(current, next)) {
                            continue;
                        }
                        boolean turn = current.incoming.getAxis() != direction.getAxis();
                        boolean hasTurn = current.hasTurn || turn;
                        int straightSinceTurn = turn ? 0
                                : current.hasTurn ? Math.min(2, current.straightSinceTurn + 1) : 0;
                        Key key = new Key(next, direction, hasTurn, straightSinceTurn);
                        if (!visited.add(key)) {
                            continue;
                        }
                        frontier.addLast(new Node(next.immutable(), direction, current.depth + 1, current,
                                placement.supportPos, hasTurn, straightSinceTurn));
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
                result.add(new RouteProposal(index, generation, origin, originHeading, endpoint.pos, steps));
            }
            return List.copyOf(result);
        }

        private boolean geometryAllowed(Node current, Direction direction, int dy) {
            if (!turnSpacingAllowed(current.incoming, direction, current.hasTurn, current.straightSinceTurn)) {
                return false;
            }
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

        private boolean isolatedBridgeAllowed(
                Node current,
                Direction direction,
                int dy,
                BlockPos candidate,
                Placement placement
        ) {
            if (current.supportPos != null) {
                return direction == current.incoming && dy == 0 && placement.supportPos == null;
            }
            if (placement.supportPos == null) {
                if (dy != 0 && direction == current.incoming) {
                    BlockPos levelCandidate = current.pos.relative(direction);
                    Placement levelPlacement = inspect(levelCandidate);
                    Placement levelLanding = inspect(levelCandidate.relative(direction));
                    if (levelPlacement != null && levelPlacement.supportPos != null
                            && levelLanding != null && levelLanding.supportPos == null) {
                        return false;
                    }
                }
                return true;
            }
            if (direction != current.incoming || dy != 0) {
                return false;
            }
            Placement landing = inspect(candidate.relative(direction));
            return landing != null && landing.supportPos == null;
        }

        private boolean adjacencyAllowed(Node current, BlockPos candidate) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos adjacentColumn = candidate.relative(side);
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos adjacent = adjacentColumn.offset(0, dy, 0);
                    if (adjacent.equals(current.pos)) {
                        continue;
                    }
                    if (!level.isLoaded(adjacent)) {
                        return false;
                    }
                    if (BaseRailBlock.isRail(level.getBlockState(adjacent))) {
                        return false;
                    }
                    for (Node ancestor = current.parent; ancestor != null; ancestor = ancestor.parent) {
                        if (ancestor.pos.equals(adjacent)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        @Nullable
        private Placement inspect(BlockPos railPos) {
            return inspectPlacement(level, railPos);
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

    public static boolean turnSpacingAllowed(
            Direction incoming,
            Direction outgoing,
            boolean hasPreviousTurn,
            int straightRailsSinceTurn
    ) {
        return incoming.getAxis() == outgoing.getAxis()
                || !hasPreviousTurn
                || straightRailsSinceTurn >= 2;
    }

    public static boolean isRouteStillValid(Level level, RouteProposal proposal) {
        if (!originIsUsable(level, proposal.origin()) || !routeMetadataIsValid(proposal)) {
            return false;
        }
        for (int index = 0; index < proposal.steps().size(); index++) {
            if (!isUnbuiltStepValid(level, proposal, index)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isRouteStepStillValid(Level level, RouteProposal proposal, int stepIndex) {
        if (stepIndex < 0 || stepIndex >= proposal.steps().size() || !routeMetadataIsValid(proposal)) {
            return false;
        }
        BlockPos predecessor = stepIndex == 0
                ? proposal.origin()
                : proposal.steps().get(stepIndex - 1).railPos();
        return originIsUsable(level, predecessor) && isUnbuiltStepValid(level, proposal, stepIndex);
    }

    private static boolean routeMetadataIsValid(RouteProposal proposal) {
        List<RouteStep> steps = proposal.steps();
        if (steps.isEmpty() || !proposal.endpoint().equals(steps.get(steps.size() - 1).railPos())) {
            return false;
        }
        Direction incoming = proposal.originHeading();
        boolean hasTurn = false;
        int straightSinceTurn = 0;
        BlockPos previous = proposal.origin();
        for (int index = 0; index < steps.size(); index++) {
            RouteStep step = steps.get(index);
            BlockPos current = step.railPos();
            int dx = Math.abs(current.getX() - previous.getX());
            int dz = Math.abs(current.getZ() - previous.getZ());
            int dy = current.getY() - previous.getY();
            if (dx + dz != 1 || Math.abs(dy) > 1) {
                return false;
            }
            Direction outgoing = horizontalDirection(previous, current);
            if ((index == 0 && outgoing != proposal.originHeading())
                    || outgoing == incoming.getOpposite()
                    || !turnSpacingAllowed(incoming, outgoing, hasTurn, straightSinceTurn)) {
                return false;
            }
            if (index > 0 && dy != 0 && incoming.getAxis() != outgoing.getAxis()) {
                return false;
            }
            if (index > 0) {
                BlockPos beforePrevious = index == 1
                        ? proposal.origin()
                        : steps.get(index - 2).railPos();
                int previousDy = previous.getY() - beforePrevious.getY();
                if (previousDy != 0 && incoming.getAxis() != outgoing.getAxis()) {
                    return false;
                }
            }
            BlockPos next = index + 1 < steps.size() ? steps.get(index + 1).railPos() : null;
            if (step.supportPos() != null) {
                if (!step.supportPos().equals(current.below())
                        || dy != 0
                        || next == null
                        || next.getY() != current.getY()
                        || horizontalDirection(current, next) != outgoing
                        || steps.get(index + 1).supportPos() != null) {
                    return false;
                }
            }
            if (step.shape() != shapeFor(previous, current, next)) {
                return false;
            }
            boolean turn = incoming.getAxis() != outgoing.getAxis();
            straightSinceTurn = turn ? 0 : hasTurn ? Math.min(2, straightSinceTurn + 1) : 0;
            hasTurn |= turn;
            incoming = outgoing;
            previous = current;
        }
        return true;
    }

    private static boolean isUnbuiltStepValid(Level level, RouteProposal proposal, int stepIndex) {
        RouteStep step = proposal.steps().get(stepIndex);
        Placement placement = inspectPlacement(level, step.railPos());
        if (placement == null || !java.util.Objects.equals(placement.supportPos, step.supportPos())) {
            return false;
        }
        BlockPos predecessor = stepIndex == 0
                ? proposal.origin()
                : proposal.steps().get(stepIndex - 1).railPos();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos adjacentColumn = step.railPos().relative(side);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos adjacent = adjacentColumn.offset(0, dy, 0);
                if (adjacent.equals(predecessor)) {
                    continue;
                }
                if (!level.isLoaded(adjacent) || BaseRailBlock.isRail(level.getBlockState(adjacent))) {
                    return false;
                }
                for (int earlier = 0; earlier < stepIndex - 1; earlier++) {
                    if (proposal.steps().get(earlier).railPos().equals(adjacent)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean originIsUsable(Level level, BlockPos origin) {
        BlockPos above = origin.above();
        BlockPos floor = origin.below();
        if (!level.isLoaded(origin) || !level.isLoaded(above) || !level.isLoaded(floor)) {
            return false;
        }
        BlockState state = level.getBlockState(origin);
        return BaseRailBlock.isRail(state)
                && state.getFluidState().isEmpty()
                && level.getFluidState(above).isEmpty()
                && level.getFluidState(floor).isEmpty();
    }

    @Nullable
    private static Placement inspectPlacement(Level level, BlockPos railPos) {
        BlockPos above = railPos.above();
        BlockPos floor = railPos.below();
        if (!level.isLoaded(railPos) || !level.isLoaded(above) || !level.isLoaded(floor)) {
            return null;
        }
        BlockState railSpace = level.getBlockState(railPos);
        BlockState headSpace = level.getBlockState(above);
        if (!railSpace.isAir() || !headSpace.isAir()
                || !railSpace.getFluidState().isEmpty()
                || !headSpace.getFluidState().isEmpty()) {
            return null;
        }
        BlockState floorState = level.getBlockState(floor);
        if (floorState.getFluidState().isEmpty()
                && floorState.isFaceSturdy(level, floor, Direction.UP)
                && !(floorState.getBlock() instanceof BaseRailBlock)) {
            return new Placement(null);
        }
        if (floorState.isAir()
                && floorState.getFluidState().isEmpty()) {
            return new Placement(floor.immutable());
        }
        return null;
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
    private record Key(BlockPos pos, Direction incoming, boolean hasTurn, int straightSinceTurn) {}
    private record Node(
            BlockPos pos,
            Direction incoming,
            int depth,
            @Nullable Node parent,
            @Nullable BlockPos supportPos,
            boolean hasTurn,
            int straightSinceTurn
    ) {}
}

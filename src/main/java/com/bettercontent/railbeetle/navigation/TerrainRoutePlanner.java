package com.bettercontent.railbeetle.navigation;

import com.bettercontent.railbeetle.RailBeetleRegistries;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public final class TerrainRoutePlanner {
    public static final int MINIMUM_NODE_CAP = 262_144;
    private static final int MAX_BEACON_ROUTES = 3;
    private static final int SEARCH_THREADS = Math.max(1,
            Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
    private static final ForkJoinPool SEARCH_POOL = new ForkJoinPool(SEARCH_THREADS, pool -> {
        ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        worker.setName("rail-beetle-route-" + worker.getPoolIndex());
        worker.setDaemon(true);
        return worker;
    }, null, false);

    private TerrainRoutePlanner() {}

    public static int nodeCapFor(int railCap) {
        int boundedCap = Math.max(1, railCap);
        return Math.max(MINIMUM_NODE_CAP, 32 * boundedCap * boundedCap);
    }

    static int searchThreadCount() {
        return SEARCH_THREADS;
    }

    public static Session begin(Level level, BlockPos originRail, Direction forward, int railCap, long generation) {
        return begin(level, originRail, forward, railCap, generation, 4, 1);
    }

    public static Session begin(Level level, BlockPos originRail, Direction forward, int railCap, long generation,
                                int supportDepth, int bridgeWidth) {
        if (!forward.getAxis().isHorizontal()) {
            forward = Direction.NORTH;
        }
        return new Session(level, originRail.immutable(), forward, railCap, generation,
                supportDepth, bridgeWidth, true);
    }

    public static Session beginSingleThreadedReference(
            Level level, BlockPos originRail, Direction forward, int railCap, long generation
    ) {
        if (!forward.getAxis().isHorizontal()) forward = Direction.NORTH;
        return new Session(level, originRail.immutable(), forward, railCap, generation, 4, 1, false);
    }

    public static final class Session {
        private final BlockPos origin;
        private final int railCap;
        private final long generation;
        private final Direction originHeading;
        private final int supportDepth;
        private final int bridgeWidth;
        private final boolean parallel;
        private final TerrainMeshBuilder meshBuilder;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean previewRequested = new AtomicBoolean();
        private final AtomicReference<SearchSnapshot> snapshot = new AtomicReference<>();
        @Nullable private java.util.concurrent.ForkJoinTask<?> searchTask;

        private Session(Level level, BlockPos origin, Direction forward, int railCap, long generation,
                        int supportDepth, int bridgeWidth, boolean parallel) {
            this.origin = origin;
            this.railCap = Math.max(1, railCap);
            this.generation = generation;
            this.originHeading = forward;
            this.supportDepth = Math.max(1, Math.min(32, supportDepth));
            this.bridgeWidth = Math.max(1, Math.min(12, bridgeWidth));
            this.parallel = parallel;
            this.meshBuilder = new TerrainMeshBuilder(level, origin, this.railCap, this.supportDepth);
        }

        public boolean advance(int nodeBudget) {
            if (cancelled.get()) return true;
            if (searchTask == null) {
                if (!meshBuilder.advance(Math.max(1, nodeBudget))) return false;
                TerrainMesh mesh = meshBuilder.freeze();
                searchTask = SEARCH_POOL.submit(() -> search(mesh, origin, originHeading, railCap,
                        generation, bridgeWidth, parallel, cancelled, previewRequested, snapshot));
            }
            if (!searchTask.isDone()) return false;
            if (!searchTask.isCancelled()) searchTask.join();
            return true;
        }

        public boolean isComplete() {
            return searchTask != null && searchTask.isDone();
        }

        public List<RouteProposal> proposals() {
            if (!isComplete()) {
                throw new IllegalStateException("Planning is not complete");
            }
            SearchSnapshot latest = snapshot.get();
            return latest == null ? List.of() : latest.proposals();
        }

        @Nullable
        public SearchSnapshot latestSnapshot() {
            return snapshot.get();
        }

        public void cancel() {
            cancelled.set(true);
            if (searchTask != null) searchTask.cancel(false);
        }

        public void requestPreview() {
            previewRequested.set(true);
        }
    }

    public record SearchSnapshot(List<RouteProposal> proposals, int deepestCompletedLayer,
                                 int examined, CompletionReason reason) {
        public SearchSnapshot {
            proposals = List.copyOf(proposals);
        }

        public boolean complete() {
            return reason != CompletionReason.SEARCHING;
        }
    }

    public enum CompletionReason { SEARCHING, EXHAUSTED, RAIL_CAP, NODE_LIMIT, CANCELLED }

    private static final class TerrainMeshBuilder {
        private final Level level;
        private final BlockPos origin;
        private final int railCap;
        private final int supportDepth;
        private final ArrayDeque<MeshPoint> frontier = new ArrayDeque<>();
        private final Map<BlockPos, Integer> queuedDepth = new HashMap<>();
        private final Map<BlockPos, Placement> placements = new HashMap<>();
        private final Set<BlockPos> blocked = new HashSet<>();
        private final Map<BlockPos, WorldCell> world = new HashMap<>();
        private final Set<BlockPos> beacons = new HashSet<>();

        private TerrainMeshBuilder(Level level, BlockPos origin, int railCap, int supportDepth) {
            this.level = level;
            this.origin = origin.immutable();
            this.railCap = railCap;
            this.supportDepth = supportDepth;
            enqueue(this.origin, 0);
        }

        private boolean advance(int budget) {
            while (budget-- > 0 && !frontier.isEmpty()) {
                MeshPoint point = frontier.removeFirst();
                sampleHalo(point.pos);
                if (point.depth >= railCap) continue;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos candidate = point.pos.relative(direction).offset(0, dy, 0).immutable();
                        Placement placement = classify(candidate);
                        if (placement == null) continue;
                        enqueue(candidate, point.depth + 1);
                    }
                }
            }
            return frontier.isEmpty();
        }

        @Nullable
        private Placement classify(BlockPos pos) {
            Placement known = placements.get(pos);
            if (known != null) return known;
            if (blocked.contains(pos)) return null;
            Placement placement = inspectPlacement(level, pos, supportDepth);
            sampleHalo(pos);
            if (placement == null) blocked.add(pos);
            else placements.put(pos, placement);
            return placement;
        }

        private void enqueue(BlockPos pos, int depth) {
            Integer old = queuedDepth.get(pos);
            if (old != null && old <= depth) return;
            queuedDepth.put(pos, depth);
            frontier.addLast(new MeshPoint(pos.immutable(), depth));
        }

        private void sampleHalo(BlockPos pos) {
            sampleWorld(pos);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos column = pos.relative(side);
                for (int dy = -1; dy <= 1; dy++) sampleWorld(column.offset(0, dy, 0));
            }
        }

        private void sampleWorld(BlockPos pos) {
            BlockPos immutable = pos.immutable();
            if (world.containsKey(immutable)) return;
            if (!level.isLoaded(immutable)) {
                world.put(immutable, new WorldCell(false, false));
                return;
            }
            BlockState state = level.getBlockState(immutable);
            if (state.is(RailBeetleRegistries.ROUTE_BEACON.get())) beacons.add(immutable);
            world.put(immutable, new WorldCell(true, BaseRailBlock.isRail(state)));
        }

        private TerrainMesh freeze() {
            if (!frontier.isEmpty()) throw new IllegalStateException("Terrain mesh is incomplete");
            Long2ObjectOpenHashMap<Placement> packedPlacements = new Long2ObjectOpenHashMap<>(placements.size());
            placements.forEach((pos, placement) -> packedPlacements.put(pos.asLong(), placement));
            Long2ByteOpenHashMap packedWorld = new Long2ByteOpenHashMap(world.size());
            world.forEach((pos, cell) -> packedWorld.put(pos.asLong(),
                    (byte) (cell.loaded ? cell.rail ? 2 : 1 : 0)));
            packedWorld.defaultReturnValue((byte) 0);
            return new TerrainMesh(Long2ObjectMaps.unmodifiable(packedPlacements),
                    Long2ByteMaps.unmodifiable(packedWorld), Set.copyOf(beacons));
        }
    }

    private record TerrainMesh(Long2ObjectMap<Placement> placements, Long2ByteMap world,
                               Set<BlockPos> beacons) {
        @Nullable Placement placement(BlockPos pos) { return placements.get(pos.asLong()); }
        boolean loadedAndRailFree(BlockPos pos) {
            return world.get(pos.asLong()) == 1;
        }
    }

    private static void search(TerrainMesh mesh, BlockPos origin, Direction originHeading, int railCap,
                               long generation, int bridgeWidth, boolean parallel, AtomicBoolean cancelled,
                               AtomicBoolean previewRequested,
                               AtomicReference<SearchSnapshot> published) {
        int nodeCap = nodeCapFor(railCap);
        long sequence = 1;
        long originHash = bloomHash(origin);
        List<Node> frontier = List.of(new Node(origin, originHeading, 0, null, List.of(), 0, false, 0, 0,
                bloomBitA(originHash), bloomBitB(originHash)));
        Set<Key> visited = new HashSet<>();
        visited.add(new Key(origin, originHeading, 0, false, 0));
        Map<BlockPos, Node> endpoints = new LinkedHashMap<>();
        int examined = 0;
        int completedDepth = -1;

        while (!frontier.isEmpty()) {
            if (cancelled.get()) {
                publish(published, mesh, origin, originHeading, generation, endpoints, completedDepth,
                        examined, CompletionReason.CANCELLED);
                return;
            }
            if (examined + frontier.size() > nodeCap) {
                publish(published, mesh, origin, originHeading, generation, endpoints, completedDepth,
                        examined, CompletionReason.NODE_LIMIT);
                return;
            }
            int depth = frontier.get(0).depth;
            for (Node node : frontier) {
                if (node.depth > 0 && node.bridgeRun == 0) endpoints.putIfAbsent(node.pos, node);
            }
            examined += frontier.size();
            completedDepth = depth;
            if (depth >= railCap) {
                publish(published, mesh, origin, originHeading, generation, endpoints, completedDepth,
                        examined, CompletionReason.RAIL_CAP);
                return;
            }
            if (previewRequested.getAndSet(false)) {
                publish(published, mesh, origin, originHeading, generation, endpoints, completedDepth,
                        examined, CompletionReason.SEARCHING);
            }

            final List<Node> layer = frontier;
            IntStream parents = IntStream.range(0, layer.size());
            if (parallel) parents = parents.parallel();
            List<List<Candidate>> batches = parents
                    .mapToObj(index -> expand(mesh, layer.get(index), bridgeWidth, cancelled)).toList();
            List<Node> next = new ArrayList<>();
            for (List<Candidate> batch : batches) {
                for (Candidate candidate : batch) {
                    if (!visited.add(candidate.key)) continue;
                    long hash = bloomHash(candidate.pos);
                    Node child = new Node(candidate.pos, candidate.direction, depth + 1, candidate.parent,
                            candidate.supportPositions, candidate.bridgeRun, candidate.hasTurn,
                            candidate.straightSinceTurn, sequence++,
                            candidate.parent.ancestryBloomA | bloomBitA(hash),
                            candidate.parent.ancestryBloomB | bloomBitB(hash));
                    next.add(child);
                }
            }
            frontier = List.copyOf(next);
        }
        publish(published, mesh, origin, originHeading, generation, endpoints, completedDepth,
                examined, CompletionReason.EXHAUSTED);
    }

    private static List<Candidate> expand(TerrainMesh mesh, Node current, int bridgeWidth,
                                          AtomicBoolean cancelled) {
        if (cancelled.get()) return List.of();
        List<Candidate> result = new ArrayList<>(8);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (current.parent == null && direction != current.incoming) continue;
            if (direction == current.incoming.getOpposite()) continue;
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos next = current.pos.relative(direction).offset(0, dy, 0).immutable();
                Placement placement = mesh.placement(next);
                if (placement == null
                        || !bridgeAllowed(current, direction, dy, placement, bridgeWidth)
                        || !geometryAllowed(current, direction, dy)
                        || !adjacencyAllowed(mesh, current, next)) continue;
                boolean turn = current.incoming.getAxis() != direction.getAxis();
                boolean hasTurn = current.hasTurn || turn;
                int straight = turn ? 0 : current.hasTurn ? Math.min(2, current.straightSinceTurn + 1) : 0;
                int bridgeRun = placement.supportPositions.isEmpty() ? 0 : current.bridgeRun + 1;
                result.add(new Candidate(next, direction, current, placement.supportPositions, bridgeRun,
                        hasTurn, straight, new Key(next, direction, bridgeRun, hasTurn, straight)));
            }
        }
        return result;
    }

    private static boolean geometryAllowed(Node current, Direction direction, int dy) {
        if (!turnSpacingAllowed(current.incoming, direction, current.hasTurn, current.straightSinceTurn)) return false;
        if (dy != 0 && current.parent != null && current.incoming.getAxis() != direction.getAxis()) return false;
        if (current.parent != null) {
            int previousDy = current.pos.getY() - current.parent.pos.getY();
            if (previousDy < 0 && dy > 0) return false;
            if (previousDy != 0 && current.incoming.getAxis() != direction.getAxis()) return false;
        }
        return true;
    }

    private static boolean bridgeAllowed(Node current, Direction direction, int dy,
                                         Placement placement, int bridgeWidth) {
        boolean supported = !placement.supportPositions.isEmpty();
        if (current.bridgeRun > 0) {
            if (direction != current.incoming || dy != 0) return false;
            return !supported || current.bridgeRun < bridgeWidth;
        }
        return !supported || (direction == current.incoming && dy == 0 && bridgeWidth > 0);
    }

    private static boolean levelBridgeRequired(TerrainMesh mesh, BlockPos current, Direction direction) {
        Placement bridge = mesh.placement(current.relative(direction));
        Placement landing = mesh.placement(current.relative(direction, 2));
        return bridge != null && !bridge.supportPositions.isEmpty()
                && landing != null && landing.supportPositions.isEmpty();
    }

    private static boolean adjacencyAllowed(TerrainMesh mesh, Node current, BlockPos candidate) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos column = candidate.relative(side);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos adjacent = column.offset(0, dy, 0);
                if (adjacent.equals(current.pos)) continue;
                if (!mesh.loadedAndRailFree(adjacent)) return false;
                if (current.parent != null && bloomMightContain(current.parent, adjacent)) {
                    for (Node ancestor = current.parent; ancestor != null; ancestor = ancestor.parent) {
                        if (ancestor.pos.equals(adjacent)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean bloomMightContain(Node node, BlockPos pos) {
        long hash = bloomHash(pos);
        return (node.ancestryBloomA & bloomBitA(hash)) != 0
                && (node.ancestryBloomB & bloomBitB(hash)) != 0;
    }

    private static long bloomHash(BlockPos pos) {
        long value = pos.asLong();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        return value ^ value >>> 33;
    }

    private static long bloomBitA(long hash) { return 1L << (hash & 63); }
    private static long bloomBitB(long hash) { return 1L << ((hash >>> 32) & 63); }

    private static void publish(AtomicReference<SearchSnapshot> target, TerrainMesh mesh,
                                BlockPos origin, Direction heading,
                                long generation, Map<BlockPos, Node> endpoints, int depth, int examined,
                                CompletionReason reason) {
        List<BeaconPath> beaconPaths = mesh.beacons().stream()
                .map(beacon -> shortestBeaconPath(beacon, endpoints.values()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt((BeaconPath path) -> path.endpoint.depth)
                        .thenComparingLong(path -> path.beacon.asLong()))
                .limit(MAX_BEACON_ROUTES)
                .toList();
        Set<BlockPos> reservedEndpoints = beaconPaths.stream()
                .map(path -> path.endpoint.pos).collect(java.util.stream.Collectors.toSet());
        List<Node> surveyEndpoints = endpoints.values().stream()
                .filter(node -> !reservedEndpoints.contains(node.pos)).toList();
        List<Node> diverse = RouteDiversitySelector.select(origin, surveyEndpoints, node -> node.pos, 3, 7);
        List<RouteProposal> result = new ArrayList<>(diverse.size() + beaconPaths.size());
        for (Node endpoint : diverse) {
            result.add(proposal(result.size(), generation, origin, heading, endpoint, RouteKind.SURVEY, null));
        }
        for (BeaconPath path : beaconPaths) {
            result.add(proposal(result.size(), generation, origin, heading,
                    path.endpoint, RouteKind.BEACON, path.beacon));
        }
        target.set(new SearchSnapshot(result, depth, examined, reason));
    }

    @Nullable
    private static BeaconPath shortestBeaconPath(BlockPos beacon, java.util.Collection<Node> endpoints) {
        return endpoints.stream()
                .filter(node -> node.pos.getY() == beacon.getY()
                        && Math.abs(node.pos.getX() - beacon.getX())
                        + Math.abs(node.pos.getZ() - beacon.getZ()) == 1)
                .min(Comparator.comparingInt((Node node) -> node.depth).thenComparingLong(node -> node.sequence))
                .map(node -> new BeaconPath(beacon, node))
                .orElse(null);
    }

    private static RouteProposal proposal(int id, long generation, BlockPos origin, Direction heading,
                                          Node endpoint, RouteKind kind, @Nullable BlockPos beacon) {
        List<Node> path = reconstruct(endpoint);
        List<RouteStep> steps = new ArrayList<>(path.size());
        for (int step = 0; step < path.size(); step++) {
            BlockPos previous = step == 0 ? origin : path.get(step - 1).pos;
            BlockPos current = path.get(step).pos;
            BlockPos next = step + 1 < path.size() ? path.get(step + 1).pos : null;
            steps.add(new RouteStep(current, shapeFor(previous, current, next), path.get(step).supportPositions));
        }
        return new RouteProposal(id, generation, origin, heading, endpoint.pos, steps, kind, beacon);
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
        if (proposal.kind() == RouteKind.BEACON
                && !level.getBlockState(proposal.beaconTarget()).is(RailBeetleRegistries.ROUTE_BEACON.get())) {
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
        if (proposal.kind() == RouteKind.BEACON) {
            BlockPos beacon = proposal.beaconTarget();
            if (beacon == null || proposal.endpoint().getY() != beacon.getY()
                    || Math.abs(proposal.endpoint().getX() - beacon.getX())
                    + Math.abs(proposal.endpoint().getZ() - beacon.getZ()) != 1) return false;
        } else if (proposal.beaconTarget() != null) {
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
                if (previousDy < 0 && dy > 0) {
                    return false;
                }
                if (previousDy != 0 && incoming.getAxis() != outgoing.getAxis()) {
                    return false;
                }
            }
            BlockPos next = index + 1 < steps.size() ? steps.get(index + 1).railPos() : null;
            if (!step.supportPositions().isEmpty()) {
                if (dy != 0 || next == null || next.getY() != current.getY()
                        || horizontalDirection(current, next) != outgoing) return false;
                BlockPos expected = current.below(step.supportPositions().size());
                for (BlockPos support : step.supportPositions()) {
                    if (!support.equals(expected)) return false;
                    expected = expected.above();
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
        Placement placement = inspectPlacement(level, step.railPos(), 32);
        if (placement == null || !placement.supportPositions.equals(step.supportPositions())) {
            return false;
        }
        BlockPos predecessor = stepIndex == 0
                ? proposal.origin()
                : proposal.steps().get(stepIndex - 1).railPos();
        int verticalDelta = step.railPos().getY() - predecessor.getY();
        Direction outgoing = horizontalDirection(predecessor, step.railPos());
        if (verticalDelta != 0 && levelBridgeRequired(level, predecessor, outgoing)) {
            return false;
        }
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
    private static Placement inspectPlacement(Level level, BlockPos railPos, int supportDepth) {
        BlockPos above = railPos.above();
        BlockPos floor = railPos.below();
        if (!level.isLoaded(railPos) || !level.isLoaded(above) || !level.isLoaded(floor)) {
            return null;
        }
        BlockState railSpace = level.getBlockState(railPos);
        BlockState headSpace = level.getBlockState(above);
        if (!RouteObstructions.isOpenOrClearable(railSpace)
                || !RouteObstructions.isOpenOrClearable(headSpace)
                || !railSpace.getFluidState().isEmpty()
                || !headSpace.getFluidState().isEmpty()) {
            return null;
        }
        BlockState floorState = level.getBlockState(floor);
        if (!floorState.getFluidState().isEmpty()) return null;
        if (floorState.isFaceSturdy(level, floor, Direction.UP)
                && !(floorState.getBlock() instanceof BaseRailBlock)) {
            return new Placement(List.of());
        }
        List<BlockPos> topDown = new ArrayList<>();
        BlockPos cursor = floor;
        for (int depth = 1; depth <= supportDepth; depth++, cursor = cursor.below()) {
            if (!level.isLoaded(cursor)) return null;
            BlockState state = level.getBlockState(cursor);
            if (!state.getFluidState().isEmpty() || (!state.isAir() && !RouteObstructions.isClearable(state))) {
                if (!state.isFaceSturdy(level, cursor, Direction.UP)
                        || state.getBlock() instanceof BaseRailBlock) return null;
                java.util.Collections.reverse(topDown);
                return topDown.isEmpty() ? new Placement(List.of()) : new Placement(List.copyOf(topDown));
            }
            topDown.add(cursor.immutable());
        }
        return null;
    }

    private static boolean levelBridgeRequired(Level level, BlockPos current, Direction direction) {
        BlockPos bridge = current.relative(direction);
        Placement bridgePlacement = inspectPlacement(level, bridge, 32);
        Placement landingPlacement = inspectPlacement(level, bridge.relative(direction), 32);
        return bridgePlacement != null && !bridgePlacement.supportPositions.isEmpty()
                && landingPlacement != null && landingPlacement.supportPositions.isEmpty();
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

    private record Placement(List<BlockPos> supportPositions) {
        private Placement { supportPositions = List.copyOf(supportPositions); }
    }
    private record MeshPoint(BlockPos pos, int depth) {}
    private record WorldCell(boolean loaded, boolean rail) {}
    private record BeaconPath(BlockPos beacon, Node endpoint) {}
    private record Key(BlockPos pos, Direction incoming, int bridgeRun,
                       boolean hasTurn, int straightSinceTurn) {}
    private record Candidate(
            BlockPos pos,
            Direction direction,
            Node parent,
            List<BlockPos> supportPositions,
            int bridgeRun,
            boolean hasTurn,
            int straightSinceTurn,
            Key key
    ) {}
    private record Node(
            BlockPos pos,
            Direction incoming,
            int depth,
            @Nullable Node parent,
            List<BlockPos> supportPositions,
            int bridgeRun,
            boolean hasTurn,
            int straightSinceTurn,
            long sequence,
            long ancestryBloomA,
            long ancestryBloomB
    ) {}
}

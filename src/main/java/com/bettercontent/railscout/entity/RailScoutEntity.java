package com.bettercontent.railscout.entity;

import com.bettercontent.railscout.RailScoutConfig;
import com.bettercontent.railscout.RailScoutRegistries;
import com.bettercontent.railscout.compat.CreateCompat;
import com.bettercontent.railscout.menu.RailScoutMenu;
import com.bettercontent.railscout.navigation.RouteProposal;
import com.bettercontent.railscout.navigation.RouteStep;
import com.bettercontent.railscout.navigation.RouteObstructions;
import com.bettercontent.railscout.navigation.TerrainRoutePlanner;
import com.bettercontent.railscout.network.RailScoutNetwork;
import com.bettercontent.railscout.network.ScoutControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RailScoutEntity extends Minecart implements MenuProvider {
    public static final int INVENTORY_SIZE = 27;
    private static final int SAVE_VERSION = 2;
    private static final int PLAN_NODE_BUDGET = 2_048;
    private static final int PLAN_REFRESH_TICKS = 20;
    private static final int GENERATION_GRACE_TICKS = 40;
    private static final int G_COOLDOWN_TICKS = 20;
    private static final int DEPARTURE_TICKS = 60;
    private static final double REVERSE_SPEED = 0.5 / 20.0;
    private static final double ACCELERATION = 0.4;
    private static final double SERVICE_DECELERATION = 0.02;
    private static final double MAX_RAIL_SPEED = 8.0 / 20.0;
    private static final double AIM_TAN = 0.03492076949;
    private static final ResourceLocation BRASS_CASING = ResourceLocation.fromNamespaceAndPath("create", "brass_casing");

    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FORCED_BRAKE = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RAILS = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SUPPORTS = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROGRESS = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ROUTE_LENGTH = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_NOSE = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SPEED = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE);
    private LazyOptional<ItemStackHandler> inventoryCapability = LazyOptional.of(() -> inventory);
    private final Set<UUID> trackingPlayers = new HashSet<>();
    private final Map<UUID, Long> lastContextAction = new HashMap<>();
    private final Set<BlockPos> placedRouteRails = new HashSet<>();

    private ScoutMode mode = ScoutMode.STOPPED;
    private ScoutSpeed speedTier = ScoutSpeed.NORMAL;
    private Direction noseHeading = Direction.NORTH;
    private boolean headingInitialized;
    private boolean forcedBrake;
    private boolean automaticBrakeApplied;
    private int fuelTicks;
    private int departureTicks;
    private int activeStep;
    private long proposalGeneration;
    private long previousGenerationExpires;
    private long nextRefreshTick;
    private long planningStartedTick;
    private List<RouteProposal> proposals = List.of();
    private List<RouteProposal> previousProposals = List.of();
    @Nullable private TerrainRoutePlanner.Session planningSession;
    @Nullable private BlockPos planningOrigin;
    @Nullable private RouteProposal activeRoute;
    @Nullable private BlockPos lastRail;
    @Nullable private BlockPos commandedFromRail;
    @Nullable private BlockPos commandedNextRail;
    private boolean commandedTravelIsForward;
    private boolean inventoryDropped;

    public RailScoutEntity(EntityType<? extends RailScoutEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MODE, ScoutMode.STOPPED.ordinal());
        entityData.define(DATA_FORCED_BRAKE, false);
        entityData.define(DATA_FUEL, 0);
        entityData.define(DATA_RAILS, 0);
        entityData.define(DATA_SUPPORTS, 0);
        entityData.define(DATA_PROGRESS, 0);
        entityData.define(DATA_ROUTE_LENGTH, 0);
        entityData.define(DATA_NOSE, Direction.NORTH.get2DDataValue());
        entityData.define(DATA_SPEED, ScoutSpeed.NORMAL.ordinal());
    }

    public ItemStackHandler inventory() { return inventory; }

    public ScoutMode mode() {
        if (!level().isClientSide) return mode;
        int value = entityData.get(DATA_MODE);
        return value >= 0 && value < ScoutMode.values().length ? ScoutMode.values()[value] : ScoutMode.STOPPED;
    }

    public ScoutSpeed speedTier() {
        if (!level().isClientSide) return speedTier;
        int value = entityData.get(DATA_SPEED);
        return value >= 0 && value < ScoutSpeed.values().length ? ScoutSpeed.values()[value] : ScoutSpeed.NORMAL;
    }

    public Direction noseHeading() {
        return level().isClientSide ? Direction.from2DDataValue(entityData.get(DATA_NOSE)) : noseHeading;
    }

    public boolean forcedBrake() { return level().isClientSide ? entityData.get(DATA_FORCED_BRAKE) : forcedBrake; }
    public int fuelTicks() { return level().isClientSide ? entityData.get(DATA_FUEL) : fuelTicks; }
    public int activeStep() { return level().isClientSide ? entityData.get(DATA_PROGRESS) : activeStep; }
    public int activeRouteLength() { return level().isClientSide ? entityData.get(DATA_ROUTE_LENGTH) : activeRoute == null ? 0 : activeRoute.steps().size(); }
    public int railCount() { return level().isClientSide ? entityData.get(DATA_RAILS) : ScoutSupplies.countRails(inventory); }
    public int supportCount() { return level().isClientSide ? entityData.get(DATA_SUPPORTS) : ScoutSupplies.countSupports(inventory); }
    public List<RouteProposal> proposals() { return proposals; }
    @Nullable public RouteProposal activeRoute() { return activeRoute; }

    public void setInitialHeading(Direction heading) {
        if (heading.getAxis().isHorizontal()) {
            noseHeading = heading;
            headingInitialized = false;
        }
    }

    @Override
    public void tick() {
        if (!level().isClientSide) serverPreTick();
        super.tick();
        if (!level().isClientSide) serverPostTick();
    }

    private void serverPreTick() {
        commandedFromRail = null;
        commandedNextRail = null;
        BlockPos rail = railPosition();
        initializeHeading(rail);
        normalizeState(rail);
        boolean brake = forcedBrake || !mode.moves();
        applyAutomaticBrake(brake);
        if (brake) return;
        if (!ensureFuel()) {
            pause();
            return;
        }
        boolean commanded = switch (mode) {
            case DEPARTING, AUTO_BUILD -> prepareAutomaticMovement(rail);
            case MANUAL_FORWARD, MANUAL_REVERSE -> prepareManualMovement(rail);
            default -> false;
        };
        if (commanded) fuelTicks--;
    }

    private void serverPostTick() {
        BlockPos rail = railPosition();
        updateHeadingForRailTransition(rail);
        if (forcedBrake || !mode.moves()) setDeltaMovement(Vec3.ZERO);
        if (mode == ScoutMode.DEPARTING && departureTicks > 0 && --departureTicks == 0) mode = ScoutMode.AUTO_BUILD;
        reconcileActiveProgress(rail);
        syncStatus();
    }

    private void normalizeState(@Nullable BlockPos rail) {
        if (rail == null) {
            clearPlanningAndProposals();
            clearActiveRoute();
            mode = ScoutMode.STOPPED;
            return;
        }
        if ((mode == ScoutMode.DEPARTING || mode == ScoutMode.AUTO_BUILD || mode == ScoutMode.PAUSED) && activeRoute == null) {
            mode = ScoutMode.STOPPED;
        }
        if (activeRoute == null && (mode == ScoutMode.STOPPED || mode == ScoutMode.COMPLETE)) {
            if (isForwardTerminus(rail)) beginPlanning(rail); else mode = ScoutMode.STOPPED;
        }
        if (mode == ScoutMode.PLANNING || mode == ScoutMode.READY) {
            if (planningOrigin != null && (!rail.equals(planningOrigin) || !isForwardTerminus(rail))) {
                clearPlanningAndProposals();
                mode = ScoutMode.STOPPED;
                return;
            }
            advancePlanning(rail);
        }
        if (!previousProposals.isEmpty() && level().getGameTime() > previousGenerationExpires) previousProposals = List.of();
    }

    private void initializeHeading(@Nullable BlockPos rail) {
        if (headingInitialized || rail == null) return;
        EnumSet<Direction> exits = railExits(rail);
        if (!exits.isEmpty()) noseHeading = closestDirection(exits, noseHeading);
        headingInitialized = true;
        lastRail = rail;
    }

    private void updateHeadingForRailTransition(@Nullable BlockPos rail) {
        if (rail == null || rail.equals(lastRail)) return;
        if (lastRail != null && lastRail.equals(commandedFromRail) && commandedNextRail != null) {
            Direction travel = horizontalDirection(lastRail, rail);
            if (travel != null) {
                boolean followedCommand = rail.equals(commandedNextRail);
                boolean travelledForward = followedCommand == commandedTravelIsForward;
                noseHeading = travelledForward ? travel : travel.getOpposite();
            }
        }
        lastRail = rail;
    }

    private void expectTransition(BlockPos from, BlockPos next, boolean travellingForward) {
        commandedFromRail = from.immutable();
        commandedNextRail = next.immutable();
        commandedTravelIsForward = travellingForward;
    }

    private void beginPlanning(BlockPos origin) {
        if (!isForwardTerminus(origin)) {
            mode = ScoutMode.STOPPED;
            return;
        }
        clearActiveRoute();
        clearPlanning();
        proposals = List.of();
        previousProposals = List.of();
        planningOrigin = origin.immutable();
        mode = ScoutMode.PLANNING;
        nextRefreshTick = level().getGameTime();
        syncRoutes();
    }

    private void advancePlanning(BlockPos rail) {
        long now = level().getGameTime();
        if (planningSession == null) {
            if (mode == ScoutMode.READY && now < nextRefreshTick) return;
            proposalGeneration++;
            planningStartedTick = now;
            planningOrigin = rail.immutable();
            planningSession = TerrainRoutePlanner.begin(level(), rail, noseHeading,
                    RailScoutConfig.ROUTE_RAIL_CAP.get(), proposalGeneration);
        }
        if (!planningSession.advance(PLAN_NODE_BUDGET)) return;
        List<RouteProposal> fresh = planningSession.proposals();
        planningSession = null;
        previousProposals = proposals;
        previousGenerationExpires = now + GENERATION_GRACE_TICKS;
        proposals = fresh;
        mode = ScoutMode.READY;
        nextRefreshTick = planningStartedTick + PLAN_REFRESH_TICKS;
        syncRoutes();
    }

    private boolean prepareAutomaticMovement(@Nullable BlockPos rail) {
        if (activeRoute == null || rail == null) {
            recoverFromInvalidRoute();
            return false;
        }
        reconcileActiveProgress(rail);
        int lookAhead = Math.min(activeRoute.steps().size(), activeStep + 4);
        for (int index = activeStep; index < lookAhead; index++) {
            if (!ensureStepPlaced(index)) return false;
        }
        if (activeStep >= activeRoute.steps().size()) {
            double remaining = horizontalDistance(position(), railCenter(activeRoute.endpoint()));
            if (remaining < 0.18 && horizontalSpeed() < 0.055) {
                finishRoute();
                return false;
            }
            return accelerateAlong(noseHeading, stoppingSpeed(remaining), false);
        }
        BlockPos next = activeRoute.steps().get(activeStep).railPos();
        Direction travel = horizontalDirection(rail, next);
        if (travel == null) travel = noseHeading;
        noseHeading = travel;
        expectTransition(rail, next, true);
        double selected = mode == ScoutMode.DEPARTING ? ScoutSpeed.HALF.blocksPerTick() : speedTier.blocksPerTick();
        return accelerateAlong(travel, Math.min(selected, stoppingSpeed(remainingRouteDistance())), next.getY() > rail.getY());
    }

    private boolean prepareManualMovement(@Nullable BlockPos rail) {
        if (rail == null) {
            pause();
            return false;
        }
        boolean reverse = mode == ScoutMode.MANUAL_REVERSE;
        Direction travelHeading = reverse ? noseHeading.getOpposite() : noseHeading;
        Direction exit = resolveExit(rail, travelHeading);
        if (exit != null) noseHeading = reverse ? exit.getOpposite() : exit;
        Direction movementHeading = exit == null ? travelHeading : exit;
        BlockPos next = exit == null ? null : connectedRail(rail, exit);
        if (next == null) {
            double toCenter = forwardDistance(position(), railCenter(rail), movementHeading);
            if (horizontalSpeed() < 0.035 || toCenter <= 0.08) {
                setDeltaMovement(Vec3.ZERO);
                if (!reverse && isForwardTerminus(rail)) beginPlanning(rail); else mode = ScoutMode.STOPPED;
                return false;
            }
            return accelerateAlong(movementHeading, stoppingSpeed(Math.max(0, toCenter)), false);
        }
        double requested = reverse ? REVERSE_SPEED : speedTier.blocksPerTick();
        expectTransition(rail, next, !reverse);
        return accelerateAlong(exit, Math.min(requested, stoppingSpeed(connectedDistanceAhead(rail, exit, 12))),
                next.getY() > rail.getY());
    }

    private boolean accelerateAlong(Direction direction, double targetSpeed, boolean uphill) {
        Vec3 motion = getDeltaMovement();
        Vec3 tangent = new Vec3(direction.getStepX(), 0, direction.getStepZ());
        double along = motion.x * tangent.x + motion.z * tangent.z;
        double difference = targetSpeed - along;
        double limit = difference >= 0 ? ACCELERATION + (uphill ? getSlopeAdjustment() : 0) : SERVICE_DECELERATION;
        double change = Mth.clamp(difference, -limit, limit);
        double perpendicularX = motion.x - tangent.x * along;
        double perpendicularZ = motion.z - tangent.z * along;
        setCurrentCartSpeedCapOnRail((float) MAX_RAIL_SPEED);
        setDeltaMovement(tangent.x * (along + change) + perpendicularX * 0.5,
                motion.y, tangent.z * (along + change) + perpendicularZ * 0.5);
        CreateCompat.applyConsistTraction(this, direction, targetSpeed, ACCELERATION);
        return Math.abs(along + change) > 1.0e-4;
    }

    private double stoppingSpeed(double distance) {
        return Math.min(MAX_RAIL_SPEED, Math.sqrt(Math.max(0, 2.0 * SERVICE_DECELERATION * distance)));
    }

    private double remainingRouteDistance() {
        if (activeRoute == null) return 0;
        double distance = 0;
        Vec3 cursor = position();
        for (int index = activeStep; index < activeRoute.steps().size(); index++) {
            Vec3 next = railCenter(activeRoute.steps().get(index).railPos());
            distance += horizontalDistance(cursor, next);
            cursor = next;
        }
        return distance;
    }

    private double connectedDistanceAhead(BlockPos start, Direction heading, int cap) {
        BlockPos current = start;
        Direction travel = heading;
        double distance = Math.max(0, forwardDistance(position(), railCenter(start), heading)) + 0.5;
        Set<BlockPos> visited = new HashSet<>();
        visited.add(current);
        for (int count = 0; count < cap; count++) {
            Direction exit = resolveExit(current, travel);
            if (exit == null) break;
            BlockPos next = connectedRail(current, exit);
            if (next == null || !visited.add(next)) break;
            distance += 1.0;
            Direction nextTravel = horizontalDirection(current, next);
            if (nextTravel != null) travel = nextTravel;
            current = next;
        }
        return distance;
    }

    private void reconcileActiveProgress(@Nullable BlockPos rail) {
        if (activeRoute == null || rail == null || !mode.hasActiveRoute()) return;
        if (rail.equals(activeRoute.origin())) {
            activeStep = 0;
            return;
        }
        for (int index = 0; index < activeRoute.steps().size(); index++) {
            if (rail.equals(activeRoute.steps().get(index).railPos())) {
                activeStep = index + 1;
                return;
            }
        }
    }

    private void finishRoute() {
        setDeltaMovement(Vec3.ZERO);
        clearActiveRoute();
        mode = ScoutMode.COMPLETE;
        syncRoutes();
    }

    private void recoverFromInvalidRoute() {
        clearActiveRoute();
        clearPlanningAndProposals();
        mode = ScoutMode.STOPPED;
        BlockPos rail = railPosition();
        if (rail != null && isForwardTerminus(rail)) beginPlanning(rail);
    }

    private boolean ensureFuel() {
        if (fuelTicks > 0) return true;
        fuelTicks = ScoutSupplies.takeFuel(inventory);
        return fuelTicks > 0;
    }

    private boolean ensureStepPlaced(int stepIndex) {
        if (activeRoute == null) return false;
        RouteStep step = activeRoute.steps().get(stepIndex);
        BlockState existing = level().getBlockState(step.railPos());
        if (existing.getBlock() instanceof BaseRailBlock) {
            RailShape actual = railShape(existing, step.railPos());
            if (actual == step.shape()) return clearRouteObstruction(step.railPos().above());
            if (!placedRouteRails.contains(step.railPos())) {
                invalidateRoute();
                return false;
            }
            BaseRailBlock rail = (BaseRailBlock) existing.getBlock();
            if (!rail.getShapeProperty().getPossibleValues().contains(step.shape())) {
                invalidateRoute();
                return false;
            }
            return level().setBlock(step.railPos(), existing.setValue(rail.getShapeProperty(), step.shape()), 3)
                    && clearRouteObstruction(step.railPos().above());
        }
        if (!TerrainRoutePlanner.isRouteStepStillValid(level(), activeRoute, stepIndex)) {
            invalidateRoute();
            return false;
        }
        boolean needsSupport = step.supportPos() != null && level().getBlockState(step.supportPos()).isAir();
        if (ScoutSupplies.countRails(inventory) < 1 || (needsSupport && ScoutSupplies.countSupports(inventory) < 1)) {
            pause();
            return false;
        }
        BlockItem railItem = ScoutSupplies.takeRail(inventory, step.shape());
        BlockItem supportItem = needsSupport ? ScoutSupplies.takeSupport(inventory) : null;
        if (railItem == null || (needsSupport && supportItem == null)) {
            pause();
            return false;
        }
        if (!clearRouteObstruction(step.railPos()) || !clearRouteObstruction(step.railPos().above())) {
            returnItem(new ItemStack(railItem));
            if (supportItem != null) returnItem(new ItemStack(supportItem));
            invalidateRoute();
            return false;
        }
        if (needsSupport && !level().setBlock(step.supportPos(), supportItem.getBlock().defaultBlockState(), 3)) {
            returnItem(new ItemStack(railItem));
            returnItem(new ItemStack(supportItem));
            invalidateRoute();
            return false;
        }
        BaseRailBlock railBlock = (BaseRailBlock) railItem.getBlock();
        BlockState railState = railBlock.defaultBlockState().setValue(railBlock.getShapeProperty(), step.shape());
        if (!level().setBlock(step.railPos(), railState, 3)) {
            if (needsSupport) level().setBlock(step.supportPos(), Blocks.AIR.defaultBlockState(), 3);
            returnItem(new ItemStack(railItem));
            if (supportItem != null) returnItem(new ItemStack(supportItem));
            invalidateRoute();
            return false;
        }
        placedRouteRails.add(step.railPos().immutable());
        return true;
    }

    private boolean clearRouteObstruction(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (state.isAir()) return true;
        if (!RouteObstructions.isClearable(state)) return false;
        return level().destroyBlock(pos, true, this, 512) || level().getBlockState(pos).isAir();
    }

    public void contextualAction(ServerPlayer player, long generation, int routeId) {
        if (!canContextPlayer(player)) return;
        long now = level().getGameTime();
        if (now - lastContextAction.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < G_COOLDOWN_TICKS) return;
        if (generation >= 0 && routeId >= 0) {
            if (!followRoute(player, generation, routeId)) return;
        } else if (activeRoute != null) {
            if (mode.moves()) {
                mode = ScoutMode.PAUSED;
                setDeltaMovement(Vec3.ZERO);
            } else {
                clearActiveRoute();
                clearPlanningAndProposals();
                mode = ScoutMode.STOPPED;
                BlockPos rail = railPosition();
                if (rail != null && isForwardTerminus(rail)) beginPlanning(rail);
            }
            syncRoutes();
        } else return;
        lastContextAction.put(player.getUUID(), now);
    }

    public void selectRoute(Player player, long generation, int routeId) {
        if (canNearbyPlayerControl(player)) followRoute(player, generation, routeId);
    }

    private boolean followRoute(Player player, long generation, int routeId) {
        if (forcedBrake || mode != ScoutMode.READY || activeRoute != null) return false;
        RouteProposal selected = findProposal(generation, routeId);
        BlockPos rail = railPosition();
        if (selected == null || rail == null || !rail.equals(selected.origin())
                || noseHeading != selected.originHeading() || !isForwardTerminus(rail)
                || !aimsAt(player, selected) || !TerrainRoutePlanner.isRouteStillValid(level(), selected)) {
            player.displayClientMessage(Component.translatable("message.rail_scout.route_changed"), true);
            nextRefreshTick = level().getGameTime();
            return false;
        }
        activeRoute = selected;
        activeStep = 0;
        placedRouteRails.clear();
        proposals = List.of();
        previousProposals = List.of();
        clearPlanning();
        mode = ScoutMode.DEPARTING;
        departureTicks = DEPARTURE_TICKS;
        setDeltaMovement(Vec3.ZERO);
        level().playSound(null, blockPosition(), RailScoutRegistries.WHISTLE.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
        syncRoutes();
        return true;
    }

    @Nullable
    private RouteProposal findProposal(long generation, int routeId) {
        for (RouteProposal route : proposals) if (route.generation() == generation && route.id() == routeId) return route;
        if (level().getGameTime() <= previousGenerationExpires) {
            for (RouteProposal route : previousProposals) if (route.generation() == generation && route.id() == routeId) return route;
        }
        return null;
    }

    public void control(Player player, ScoutControl action) {
        if (!canNearbyPlayerControl(player)) return;
        switch (action) {
            case TOGGLE_HAND_BRAKE -> {
                forcedBrake = !forcedBrake;
                if (forcedBrake && mode.moves()) mode = activeRoute == null ? ScoutMode.STOPPED : ScoutMode.PAUSED;
            }
            case STOP -> {
                mode = activeRoute == null ? ScoutMode.STOPPED : ScoutMode.PAUSED;
                setDeltaMovement(Vec3.ZERO);
            }
            case REVERSE -> {
                if (forcedBrake) return;
                clearActiveRoute();
                clearPlanningAndProposals();
                mode = ScoutMode.MANUAL_REVERSE;
            }
            case HALF_SPEED -> selectForwardSpeed(ScoutSpeed.HALF);
            case NORMAL_SPEED -> selectForwardSpeed(ScoutSpeed.NORMAL);
            case DOUBLE_SPEED -> selectForwardSpeed(ScoutSpeed.DOUBLE);
        }
        syncRoutes();
        syncStatus();
    }

    private void selectForwardSpeed(ScoutSpeed selected) {
        speedTier = selected;
        if (forcedBrake) return;
        if (activeRoute != null) mode = ScoutMode.AUTO_BUILD;
        else {
            clearPlanningAndProposals();
            mode = ScoutMode.MANUAL_FORWARD;
        }
    }

    private boolean canContextPlayer(Player player) {
        return isAlive() && player.level() == level() && trackingPlayers.contains(player.getUUID());
    }

    private boolean canNearbyPlayerControl(Player player) {
        return isAlive() && player.level() == level() && player.distanceToSqr(this) <= 256.0;
    }

    private boolean aimsAt(Player player, RouteProposal proposal) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        for (RouteStep step : proposal.steps()) {
            Vec3 target = railCenter(step.railPos()).subtract(eye);
            double along = target.dot(look);
            if (along <= 0) continue;
            double perpendicular = target.subtract(look.scale(along)).length();
            if (perpendicular <= Math.max(0.8, along * AIM_TAN)) return true;
        }
        return false;
    }

    private void applyAutomaticBrake(boolean brake) {
        if (automaticBrakeApplied == brake) {
            if (brake) setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (brake) {
            setDeltaMovement(Vec3.ZERO);
            setCurrentCartSpeedCapOnRail(0);
        } else setCurrentCartSpeedCapOnRail((float) MAX_RAIL_SPEED);
        CreateCompat.setExternallyStalled(this, brake);
        automaticBrakeApplied = brake;
    }

    private boolean isForwardTerminus(BlockPos rail) {
        Direction exit = resolveExit(rail, noseHeading);
        return exit != null && connectedRail(rail, exit) == null;
    }

    @Nullable
    private Direction resolveExit(BlockPos rail, Direction desired) {
        EnumSet<Direction> exits = railExits(rail);
        if (exits.isEmpty()) return null;
        return exits.contains(desired) ? desired : closestDirection(exits, desired);
    }

    private EnumSet<Direction> railExits(BlockPos rail) {
        BlockState state = level().getBlockState(rail);
        if (!(state.getBlock() instanceof BaseRailBlock block)) return EnumSet.noneOf(Direction.class);
        RailShape shape = block.getRailDirection(state, level(), rail, this);
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> EnumSet.of(Direction.NORTH, Direction.SOUTH);
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> EnumSet.of(Direction.EAST, Direction.WEST);
            case SOUTH_EAST -> EnumSet.of(Direction.SOUTH, Direction.EAST);
            case SOUTH_WEST -> EnumSet.of(Direction.SOUTH, Direction.WEST);
            case NORTH_WEST -> EnumSet.of(Direction.NORTH, Direction.WEST);
            case NORTH_EAST -> EnumSet.of(Direction.NORTH, Direction.EAST);
        };
    }

    @Nullable
    private BlockPos connectedRail(BlockPos rail, Direction exit) {
        for (int dy : new int[]{0, 1, -1}) {
            BlockPos candidate = rail.relative(exit).offset(0, dy, 0);
            if (level().getBlockState(candidate).getBlock() instanceof BaseRailBlock && railExits(candidate).contains(exit.getOpposite())) return candidate;
        }
        return null;
    }

    private RailShape railShape(BlockState state, BlockPos pos) {
        return ((BaseRailBlock) state.getBlock()).getRailDirection(state, level(), pos, this);
    }

    private static Direction closestDirection(Set<Direction> choices, Direction desired) {
        Direction best = choices.iterator().next();
        int bestDot = Integer.MIN_VALUE;
        for (Direction choice : choices) {
            int dot = choice.getStepX() * desired.getStepX() + choice.getStepZ() * desired.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = choice;
            }
        }
        return best;
    }

    @Nullable
    private static Direction horizontalDirection(BlockPos from, BlockPos to) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (dx == 0 && dz == 0) return null;
        return Math.abs(to.getX() - from.getX()) >= Math.abs(to.getZ() - from.getZ())
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double forwardDistance(Vec3 from, Vec3 to, Direction direction) {
        return (to.x - from.x) * direction.getStepX() + (to.z - from.z) * direction.getStepZ();
    }

    private double horizontalSpeed() { return getDeltaMovement().horizontalDistance(); }

    private void invalidateRoute() { recoverFromInvalidRoute(); }

    private void pause() {
        mode = activeRoute == null ? ScoutMode.STOPPED : ScoutMode.PAUSED;
        setDeltaMovement(Vec3.ZERO);
    }

    private void clearActiveRoute() {
        activeRoute = null;
        activeStep = 0;
        departureTicks = 0;
        placedRouteRails.clear();
    }

    private void clearPlanning() {
        planningSession = null;
        planningOrigin = null;
    }

    private void clearPlanningAndProposals() {
        clearPlanning();
        proposals = List.of();
        previousProposals = List.of();
        syncRoutes();
    }

    private void returnItem(ItemStack stack) {
        ItemStack pending = stack;
        for (int slot = 0; slot < inventory.getSlots() && !pending.isEmpty(); slot++) pending = inventory.insertItem(slot, pending, false);
        if (!pending.isEmpty()) spawnAtLocation(pending);
    }

    @Nullable
    private BlockPos railPosition() {
        BlockPos current = getCurrentRailPosition();
        if (level().getBlockState(current).getBlock() instanceof BaseRailBlock) return current;
        BlockPos at = blockPosition();
        if (level().getBlockState(at).getBlock() instanceof BaseRailBlock) return at;
        BlockPos below = at.below();
        return level().getBlockState(below).getBlock() instanceof BaseRailBlock ? below : null;
    }

    public static Vec3 railCenter(BlockPos pos) { return new Vec3(pos.getX() + 0.5, pos.getY() + 0.0625, pos.getZ() + 0.5); }

    private void syncStatus() {
        entityData.set(DATA_MODE, mode.ordinal());
        entityData.set(DATA_FORCED_BRAKE, forcedBrake);
        entityData.set(DATA_FUEL, fuelTicks);
        entityData.set(DATA_RAILS, ScoutSupplies.countRails(inventory));
        entityData.set(DATA_SUPPORTS, ScoutSupplies.countSupports(inventory));
        entityData.set(DATA_PROGRESS, activeStep);
        entityData.set(DATA_ROUTE_LENGTH, activeRoute == null ? 0 : activeRoute.steps().size());
        entityData.set(DATA_NOSE, noseHeading.get2DDataValue());
        entityData.set(DATA_SPEED, speedTier.ordinal());
    }

    private void syncRoutes() {
        if (!level().isClientSide) RailScoutNetwork.syncRoutes(this, proposals, activeRoute);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        trackingPlayers.add(player.getUUID());
        RailScoutNetwork.syncRoutesTo(player, this, proposals, activeRoute);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        trackingPlayers.remove(player.getUUID());
        lastContextAction.remove(player.getUUID());
        super.stopSeenByPlayer(player);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) NetworkHooks.openScreen(serverPlayer, this, buffer -> buffer.writeVarInt(getId()));
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override public Component getDisplayName() { return Component.translatable("entity.rail_scout.rail_scout"); }

    @Override public boolean canBeRidden() { return false; }

    @Override public boolean isPoweredCart() { return true; }

    @Override public boolean isPushable() { return false; }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return displayBlockState(ForgeRegistries.BLOCKS.getValue(BRASS_CASING));
    }

    static BlockState displayBlockState(@Nullable Block brassCasing) {
        return brassCasing == null || brassCasing == Blocks.AIR
                ? Blocks.COPPER_BLOCK.defaultBlockState()
                : brassCasing.defaultBlockState();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) { return new RailScoutMenu(id, playerInventory, this); }

    @Override protected Item getDropItem() { return RailScoutRegistries.RAIL_SCOUT_ITEM.get(); }
    @Override public ItemStack getPickResult() { return new ItemStack(RailScoutRegistries.RAIL_SCOUT_ITEM.get()); }

    @Override
    public void destroy(net.minecraft.world.damagesource.DamageSource source) {
        dropInventory();
        super.destroy(source);
    }

    private void dropInventory() {
        if (inventoryDropped || level().isClientSide) return;
        inventoryDropped = true;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) spawnAtLocation(stack.copy());
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RailScoutDataVersion", SAVE_VERSION);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putBoolean("ForcedBrake", forcedBrake);
        tag.putInt("FuelTicks", fuelTicks);
        tag.putLong("ProposalGeneration", proposalGeneration);
        tag.putInt("ActiveStep", activeStep);
        tag.putString("NoseHeading", noseHeading.getName());
        tag.putString("SpeedTier", speedTier.name());
        if (activeRoute != null) tag.put("ActiveRoute", writeRoute(activeRoute));
        long[] placed = placedRouteRails.stream().mapToLong(BlockPos::asLong).toArray();
        tag.putLongArray("PlacedRouteRails", placed);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        forcedBrake = tag.getBoolean("ForcedBrake");
        fuelTicks = Math.max(0, tag.getInt("FuelTicks"));
        proposalGeneration = tag.getLong("ProposalGeneration");
        speedTier = parseSpeed(tag.getString("SpeedTier"));
        Direction savedHeading = Direction.byName(tag.getString("NoseHeading"));
        if (savedHeading != null && savedHeading.getAxis().isHorizontal()) {
            noseHeading = savedHeading;
            headingInitialized = true;
        }
        boolean currentFormat = tag.getInt("RailScoutDataVersion") >= SAVE_VERSION;
        activeRoute = currentFormat && tag.contains("ActiveRoute", Tag.TAG_COMPOUND) ? readRoute(tag.getCompound("ActiveRoute")) : null;
        activeStep = activeRoute == null ? 0 : Mth.clamp(tag.getInt("ActiveStep"), 0, activeRoute.steps().size());
        placedRouteRails.clear();
        if (activeRoute != null) {
            for (long packed : tag.getLongArray("PlacedRouteRails")) placedRouteRails.add(BlockPos.of(packed));
        }
        mode = activeRoute == null ? ScoutMode.STOPPED : ScoutMode.PAUSED;
        proposals = List.of();
        previousProposals = List.of();
        clearPlanning();
    }

    private static ScoutSpeed parseSpeed(String name) {
        try { return ScoutSpeed.valueOf(name); }
        catch (IllegalArgumentException exception) { return ScoutSpeed.NORMAL; }
    }

    private static CompoundTag writeRoute(RouteProposal route) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", route.id());
        tag.putLong("Generation", route.generation());
        tag.putLong("Origin", route.origin().asLong());
        tag.putString("OriginHeading", route.originHeading().getName());
        tag.putLong("Endpoint", route.endpoint().asLong());
        ListTag steps = new ListTag();
        for (RouteStep step : route.steps()) {
            CompoundTag stepTag = new CompoundTag();
            stepTag.putLong("Rail", step.railPos().asLong());
            stepTag.putString("Shape", step.shape().name());
            if (step.supportPos() != null) stepTag.putLong("Support", step.supportPos().asLong());
            steps.add(stepTag);
        }
        tag.put("Steps", steps);
        return tag;
    }

    private static RouteProposal readRoute(CompoundTag tag) {
        List<RouteStep> steps = new ArrayList<>();
        ListTag list = tag.getList("Steps", Tag.TAG_COMPOUND);
        for (Tag value : list) {
            CompoundTag stepTag = (CompoundTag) value;
            RailShape shape;
            try { shape = RailShape.valueOf(stepTag.getString("Shape")); }
            catch (IllegalArgumentException exception) { shape = RailShape.NORTH_SOUTH; }
            BlockPos support = stepTag.contains("Support", Tag.TAG_LONG) ? BlockPos.of(stepTag.getLong("Support")) : null;
            steps.add(new RouteStep(BlockPos.of(stepTag.getLong("Rail")), shape, support));
        }
        Direction heading = Direction.byName(tag.getString("OriginHeading"));
        if (heading == null || !heading.getAxis().isHorizontal()) heading = Direction.NORTH;
        return new RouteProposal(tag.getInt("Id"), tag.getLong("Generation"), BlockPos.of(tag.getLong("Origin")),
                heading, BlockPos.of(tag.getLong("Endpoint")), steps);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) return inventoryCapability.cast();
        return super.getCapability(capability, side);
    }

    @Override public void invalidateCaps() { super.invalidateCaps(); inventoryCapability.invalidate(); }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inventoryCapability = LazyOptional.of(() -> inventory);
    }

    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

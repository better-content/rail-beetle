package com.bettercontent.railbeetle.entity;

import com.bettercontent.railbeetle.RailBeetleConfig;
import com.bettercontent.railbeetle.RailBeetleRegistries;
import com.bettercontent.railbeetle.compat.CreateCompat;
import com.bettercontent.railbeetle.compat.GoetySoulIntegration;
import com.bettercontent.railbeetle.menu.RailBeetleMenu;
import com.bettercontent.railbeetle.menu.RemoteBeetleMenu;
import com.bettercontent.railbeetle.navigation.RouteProposal;
import com.bettercontent.railbeetle.navigation.RouteKind;
import com.bettercontent.railbeetle.navigation.RouteSupplyStatus;
import com.bettercontent.railbeetle.navigation.RouteStep;
import com.bettercontent.railbeetle.navigation.RouteObstructions;
import com.bettercontent.railbeetle.navigation.TerrainRoutePlanner;
import com.bettercontent.railbeetle.network.RailBeetleNetwork;
import com.bettercontent.railbeetle.network.BeetleControl;
import com.bettercontent.railbeetle.item.EngineItem;
import com.bettercontent.railbeetle.item.ModuleItem;
import com.bettercontent.railbeetle.item.RemoteControlItem;
import com.bettercontent.railbeetle.upgrade.BeetleProfile;
import com.bettercontent.railbeetle.upgrade.EngineKind;
import com.bettercontent.railbeetle.upgrade.ModuleFamily;
import com.bettercontent.railbeetle.upgrade.ModuleKind;
import com.bettercontent.railbeetle.upgrade.WorkAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.FriendlyByteBuf;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
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

public final class RailBeetleEntity extends Minecart implements MenuProvider {
    public static final int INVENTORY_SIZE = 27;
    public static final int MODULE_SLOTS = 6;
    private static final int SAVE_VERSION = 1;
    private static final int PLAN_NODE_BUDGET = 2_048;
    private static final int PLAN_REFRESH_TICKS = 20;
    private static final int GENERATION_GRACE_TICKS = 40;
    private static final int G_COOLDOWN_TICKS = 20;
    static final int DEPARTURE_TICKS = 25;
    private static final double REVERSE_SPEED = 0.5 / 20.0;
    private static final double ACCELERATION = 0.4;
    private static final double LIVING_SHOVE = 0.35;
    private static final double CART_SHOVE = 0.45;
    private static final double AIM_TAN = 0.03492076949;
    private static final ResourceLocation BRASS_CASING = ResourceLocation.fromNamespaceAndPath("create", "brass_casing");

    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FORCED_BRAKE = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BRAKE_APPLIED = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RAILS = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SUPPORTS = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROGRESS = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ROUTE_LENGTH = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_NOSE = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SPEED = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ENGINE = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ENGINE_RESOURCE = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_MODULES = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_SEARCHLIGHT = SynchedEntityData.defineId(RailBeetleEntity.class, EntityDataSerializers.BOOLEAN);

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            if (!level().isClientSide && !proposals.isEmpty()) syncRoutes();
        }
    };
    private LazyOptional<ItemStackHandler> inventoryCapability = LazyOptional.of(() -> inventory);
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(EnergyDock::new);
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(FluidDock::new);
    private final ItemStackHandler engine = new ItemStackHandler(1) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.getItem() instanceof EngineItem; }
        @Override protected void onContentsChanged(int slot) { machineryChanged(); }
    };
    private final ItemStackHandler modules = new ItemStackHandler(MODULE_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (!(stack.getItem() instanceof ModuleItem candidate)) return false;
            for (int index = 0; index < getSlots(); index++) {
                ItemStack installed = getStackInSlot(index);
                if (index != slot && installed.getItem() instanceof ModuleItem module
                        && module.kind().family() == candidate.kind().family()) return false;
            }
            return true;
        }
        @Override protected void onContentsChanged(int slot) { machineryChanged(); }
    };
    private final Set<UUID> trackingPlayers = new HashSet<>();
    private final Map<UUID, Long> lastContextAction = new HashMap<>();
    private final Set<BlockPos> placedRouteRails = new HashSet<>();

    private BeetleMode mode = BeetleMode.STOPPED;
    private BeetleSpeed speedTier = BeetleSpeed.NORMAL;
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
    private long nextPreviewTick;
    private int planningPublishedDepth = -1;
    private List<RouteProposal> proposals = List.of();
    private List<RouteProposal> previousProposals = List.of();
    @Nullable private TerrainRoutePlanner.Session planningSession;
    @Nullable private BlockPos planningOrigin;
    @Nullable private RouteProposal activeRoute;
    @Nullable private BlockPos lastRail;
    @Nullable private BlockPos commandedFromRail;
    @Nullable private BlockPos commandedNextRail;
    @Nullable private BlockPos preTickRail;
    @Nullable private Vec3 preTickPosition;
    private boolean commandedTravelIsForward;
    private boolean commandedUphill;
    private double commandedHorizontalSpeed;
    private boolean clearedLivingBlocker;
    private boolean inventoryDropped;

    public RailBeetleEntity(EntityType<? extends RailBeetleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MODE, BeetleMode.STOPPED.ordinal());
        entityData.define(DATA_FORCED_BRAKE, false);
        entityData.define(DATA_BRAKE_APPLIED, true);
        entityData.define(DATA_FUEL, 0);
        entityData.define(DATA_RAILS, 0);
        entityData.define(DATA_SUPPORTS, 0);
        entityData.define(DATA_PROGRESS, 0);
        entityData.define(DATA_ROUTE_LENGTH, 0);
        entityData.define(DATA_NOSE, Direction.NORTH.get2DDataValue());
        entityData.define(DATA_SPEED, BeetleSpeed.NORMAL.ordinal());
        entityData.define(DATA_ENGINE, EngineKind.FIREBOX.ordinal());
        entityData.define(DATA_ENGINE_RESOURCE, 0);
        entityData.define(DATA_MODULES, 0L);
        entityData.define(DATA_SEARCHLIGHT, false);
    }

    public ItemStackHandler inventory() { return inventory; }
    public ItemStackHandler engineInventory() { return engine; }
    public ItemStackHandler moduleInventory() { return modules; }

    public BeetleProfile profile() {
        if (level().isClientSide) {
            return profileForMask(entityData.get(DATA_MODULES));
        }
        List<ModuleKind> active = new ArrayList<>();
        for (int slot = 0; slot < modules.getSlots(); slot++) {
            if (modules.getStackInSlot(slot).getItem() instanceof ModuleItem item) active.add(item.kind());
        }
        return BeetleProfile.from(active);
    }

    public static BeetleProfile profileForMask(long mask) {
        List<ModuleKind> active = new ArrayList<>();
        for (ModuleKind kind : ModuleKind.values()) {
            if ((mask & (1L << kind.ordinal())) != 0) active.add(kind);
        }
        return BeetleProfile.from(active);
    }

    public EngineKind engineKind() {
        if (level().isClientSide) {
            int value = entityData.get(DATA_ENGINE);
            return value >= 0 && value < EngineKind.values().length ? EngineKind.values()[value] : EngineKind.FIREBOX;
        }
        return engine.getStackInSlot(0).getItem() instanceof EngineItem item ? item.kind() : EngineKind.FIREBOX;
    }

    public int engineResource() {
        if (level().isClientSide) return entityData.get(DATA_ENGINE_RESOURCE);
        ItemStack stack = engine.getStackInSlot(0);
        return stack.getItem() instanceof EngineItem item ? item.resource(stack) : fuelTicks;
    }

    public boolean searchlightOn() { return entityData.get(DATA_SEARCHLIGHT) && profile().searchlight(); }
    public void toggleSearchlight() {
        if (!profile().searchlight()) return;
        entityData.set(DATA_SEARCHLIGHT, !entityData.get(DATA_SEARCHLIGHT));
    }

    public boolean canConfigureMachinery() {
        // Forge may ask for capabilities from Entity's constructor, before this class's
        // field initializers have created the handlers and server-side mode state.
        if (inventoryCapability == null) return false;
        BeetleMode current = mode();
        return current != null && !current.moves() && brakeApplied();
    }

    public BeetleMode mode() {
        if (!level().isClientSide) return mode;
        int value = entityData.get(DATA_MODE);
        return value >= 0 && value < BeetleMode.values().length ? BeetleMode.values()[value] : BeetleMode.STOPPED;
    }

    public BeetleSpeed speedTier() {
        if (!level().isClientSide) return speedTier;
        int value = entityData.get(DATA_SPEED);
        return value >= 0 && value < BeetleSpeed.values().length ? BeetleSpeed.values()[value] : BeetleSpeed.NORMAL;
    }

    public Direction noseHeading() {
        return level().isClientSide ? Direction.from2DDataValue(entityData.get(DATA_NOSE)) : noseHeading;
    }

    public boolean forcedBrake() { return level().isClientSide ? entityData.get(DATA_FORCED_BRAKE) : forcedBrake; }
    public boolean brakeApplied() { return level().isClientSide ? entityData.get(DATA_BRAKE_APPLIED) : automaticBrakeApplied; }
    public boolean neutral() { return mode() == BeetleMode.NEUTRAL; }
    public int fuelTicks() { return level().isClientSide ? entityData.get(DATA_FUEL) : fuelTicks; }
    public int fallbackFuel() { return fuelTicks(); }
    public int activeStep() { return level().isClientSide ? entityData.get(DATA_PROGRESS) : activeStep; }
    public int activeRouteLength() { return level().isClientSide ? entityData.get(DATA_ROUTE_LENGTH) : activeRoute == null ? 0 : activeRoute.steps().size(); }
    public int railCount() { return level().isClientSide ? entityData.get(DATA_RAILS) : BeetleSupplies.countRails(inventory); }
    public int supportCount() { return level().isClientSide ? entityData.get(DATA_SUPPORTS) : BeetleSupplies.countSupports(inventory); }
    public List<RouteProposal> proposals() { return proposals; }
    @Nullable public RouteProposal activeRoute() { return activeRoute; }
    public RouteSupplyStatus supplyStatus(RouteProposal route) {
        return BeetleSupplies.supplyStatus(inventory, engineResource() > 0 ? 1 : fuelTicks, route);
    }

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
        commandedHorizontalSpeed = 0;
        commandedUphill = false;
        clearedLivingBlocker = false;
        BlockPos rail = railPosition();
        rail = recoverNearbyRail(rail);
        preTickRail = rail == null ? null : rail.immutable();
        preTickPosition = position();
        initializeHeading(rail);
        normalizeState(rail);
        boolean brake = shouldBrake();
        applyAutomaticBrake(brake);
        if (brake || mode == BeetleMode.NEUTRAL) return;
        boolean commanded = switch (mode) {
            case DEPARTING, AUTO_BUILD -> prepareAutomaticMovement(rail);
            case MANUAL_FORWARD, MANUAL_REVERSE -> prepareManualMovement(rail);
            default -> false;
        };
        if (commanded) {
            int trailers = CreateCompat.countTrailingCarts(this, profile().trailingCartLimit() + 1);
            if (trailers > profile().trailingCartLimit()) {
                pause();
                setDeltaMovement(Vec3.ZERO);
                notifyTrackingPlayers("message.rail_beetle.overloaded");
                return;
            }
            if (!consumeWork(WorkAction.MOTION, motionWork())) {
                pause();
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            commandedHorizontalSpeed = horizontalSpeed();
            clearedLivingBlocker = clearLivingCorridor();
        }
    }

    private void serverPostTick() {
        BlockPos rail = enforceRailBoundary(railPosition());
        updateHeadingForRailTransition(rail);
        if (shouldBrake()) {
            if (preTickPosition != null) setPos(preTickPosition.x, preTickPosition.y, preTickPosition.z);
            setDeltaMovement(Vec3.ZERO);
        } else if (mode.moves()) {
            if (clearedLivingBlocker) restoreCommandedSpeed();
            shoveNearbyEntities();
        }
        if (mode == BeetleMode.DEPARTING && departureTicks > 0 && --departureTicks == 0) mode = BeetleMode.AUTO_BUILD;
        reconcileActiveProgress(rail);
        syncStatus();
    }

    private void normalizeState(@Nullable BlockPos rail) {
        if (mode == BeetleMode.NEUTRAL) {
            if (!previousProposals.isEmpty()) previousProposals = List.of();
            return;
        }
        if (rail == null) {
            clearPlanningAndProposals();
            clearActiveRoute();
            mode = BeetleMode.STOPPED;
            return;
        }
        if ((mode == BeetleMode.DEPARTING || mode == BeetleMode.AUTO_BUILD || mode == BeetleMode.PAUSED) && activeRoute == null) {
            mode = BeetleMode.STOPPED;
        }
        if (activeRoute == null && (mode == BeetleMode.STOPPED || mode == BeetleMode.COMPLETE)) {
            if (isForwardTerminus(rail)) beginPlanning(rail); else mode = BeetleMode.STOPPED;
        }
        if (mode == BeetleMode.PLANNING || mode == BeetleMode.READY) {
            if (planningOrigin != null && (!rail.equals(planningOrigin) || !isForwardTerminus(rail))) {
                clearPlanningAndProposals();
                mode = BeetleMode.STOPPED;
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
            mode = BeetleMode.STOPPED;
            return;
        }
        clearActiveRoute();
        clearPlanning();
        proposals = List.of();
        previousProposals = List.of();
        planningOrigin = origin.immutable();
        mode = BeetleMode.PLANNING;
        applyAutomaticBrake(true);
        nextRefreshTick = level().getGameTime();
        syncRoutes();
    }

    private void advancePlanning(BlockPos rail) {
        long now = level().getGameTime();
        if (planningSession == null) {
            if (mode == BeetleMode.READY && now < nextRefreshTick) return;
            nextPreviewTick = now + PLAN_REFRESH_TICKS;
            planningPublishedDepth = -1;
            planningOrigin = rail.immutable();
            int configured = RailBeetleConfig.ROUTE_RAIL_CAP.get();
            int routeCap = Math.min(configured, profile().routeCap());
            planningSession = TerrainRoutePlanner.begin(level(), rail, noseHeading, routeCap,
                    proposalGeneration + 1, profile().supportDepth(), profile().bridgeWidth());
        }
        // Surveying must draw power, but baseline route discovery should remain cheap enough
        // that a single piece of coal can comfortably cover a full 64-block search.
        if (!consumeWork(WorkAction.SURVEY, Math.max(1, PLAN_NODE_BUDGET / 256))) return;
        if (now >= nextPreviewTick) planningSession.requestPreview();
        boolean complete = planningSession.advance(PLAN_NODE_BUDGET);
        TerrainRoutePlanner.SearchSnapshot snapshot = planningSession.latestSnapshot();
        if (snapshot == null || snapshot.deepestCompletedLayer() <= planningPublishedDepth
                || (!complete && now < nextPreviewTick)) return;

        proposalGeneration++;
        List<RouteProposal> fresh = snapshot.proposals().stream()
                .map(route -> new RouteProposal(route.id(), proposalGeneration, route.origin(),
                        route.originHeading(), route.endpoint(), route.steps(), route.kind(), route.beaconTarget()))
                .toList();
        previousProposals = proposals;
        previousGenerationExpires = now + GENERATION_GRACE_TICKS;
        proposals = fresh;
        planningPublishedDepth = snapshot.deepestCompletedLayer();
        mode = BeetleMode.READY;
        nextPreviewTick = now + PLAN_REFRESH_TICKS;
        if (complete) {
            planningSession = null;
            nextRefreshTick = now + PLAN_REFRESH_TICKS;
        }
        syncRoutes();
    }

    private boolean prepareAutomaticMovement(@Nullable BlockPos rail) {
        if (activeRoute == null || rail == null) {
            recoverFromInvalidRoute();
            return false;
        }
        if (!builtSupportsIntact()) {
            pause();
            notifyTrackingPlayers("message.rail_beetle.support_failed");
            return false;
        }
        reconcileActiveProgress(rail);
        int safetyLookAhead = Math.max(4, (int) Math.ceil(stoppingDistance() + 2));
        int lookAhead = Math.min(activeRoute.steps().size(), activeStep + safetyLookAhead);
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
        double selected = mode == BeetleMode.DEPARTING ? BeetleSpeed.HALF.blocksPerTick() : selectedSpeed();
        return accelerateAlong(travel, Math.min(selected, stoppingSpeed(remainingRouteDistance())), next.getY() > rail.getY());
    }

    private boolean prepareManualMovement(@Nullable BlockPos rail) {
        if (rail == null) {
            pause();
            return false;
        }
        boolean reverse = mode == BeetleMode.MANUAL_REVERSE;
        Direction travelHeading = reverse ? noseHeading.getOpposite() : noseHeading;
        Direction exit = resolveExit(rail, travelHeading);
        if (exit != null) noseHeading = reverse ? exit.getOpposite() : exit;
        Direction movementHeading = exit == null ? travelHeading : exit;
        BlockPos next = exit == null ? null : connectedRail(rail, exit);
        if (next == null) {
            double toCenter = forwardDistance(position(), railCenter(rail), movementHeading);
            if (horizontalSpeed() < 0.035 || toCenter <= 0.08) {
                setDeltaMovement(Vec3.ZERO);
                if (!reverse && isForwardTerminus(rail)) beginPlanning(rail); else mode = BeetleMode.STOPPED;
                return false;
            }
            return accelerateAlong(movementHeading, stoppingSpeed(Math.max(0, toCenter)), false);
        }
        double requested = reverse ? REVERSE_SPEED : selectedSpeed();
        expectTransition(rail, next, !reverse);
        return accelerateAlong(exit, Math.min(requested, stoppingSpeed(connectedDistanceAhead(rail, exit, 12))),
                next.getY() > rail.getY());
    }

    private boolean accelerateAlong(Direction direction, double targetSpeed, boolean uphill) {
        commandedUphill = uphill;
        Vec3 motion = getDeltaMovement();
        Vec3 tangent = new Vec3(direction.getStepX(), 0, direction.getStepZ());
        double along = motion.x * tangent.x + motion.z * tangent.z;
        int trailers = CreateCompat.countTrailingCarts(this, profile().trailingCartLimit() + 1);
        if (uphill) targetSpeed *= profile().uphillSpeedMultiplier(trailers == 0);
        double difference = targetSpeed - along;
        double traction = ACCELERATION * profile().torqueMultiplier();
        if (engineKind() == EngineKind.FLUX && (trailers > 0 || targetSpeed > 4.0 / 20.0)) traction *= 1.30;
        double limit = difference >= 0 ? traction + (uphill ? getSlopeAdjustment() : 0)
                : serviceDeceleration();
        double change = Mth.clamp(difference, -limit, limit);
        double perpendicularX = motion.x - tangent.x * along;
        double perpendicularZ = motion.z - tangent.z * along;
        setCurrentCartSpeedCapOnRail((float) (profile().maxBlocksPerSecond() / 20.0));
        setDeltaMovement(tangent.x * (along + change) + perpendicularX * 0.5,
                motion.y, tangent.z * (along + change) + perpendicularZ * 0.5);
        CreateCompat.applyConsistTraction(this, direction, targetSpeed, traction,
                profile().trailingCartLimit());
        return Math.abs(along + change) > 1.0e-4;
    }

    private double stoppingSpeed(double distance) {
        return Math.min(profile().maxBlocksPerSecond() / 20.0,
                Math.sqrt(Math.max(0, 2.0 * serviceDeceleration() * distance)));
    }

    private double stoppingDistance() {
        double speed = horizontalSpeed();
        return speed * speed / (2.0 * serviceDeceleration());
    }

    private double selectedSpeed() {
        return Math.min(speedTier.blocksPerTick(), profile().maxBlocksPerSecond() / 20.0);
    }

    private int motionWork() {
        double blocksPerSecond = Math.max(0.5, selectedSpeed() * 20.0);
        double factor = Math.pow(blocksPerSecond / 4.0, 2.0);
        int trailers = CreateCompat.countTrailingCarts(this, profile().trailingCartLimit() + 1);
        double penalty = 0.15 * trailers + (commandedUphill ? 0.50 : 0.0);
        if (engineKind() == EngineKind.STEAM) penalty *= 0.70;
        factor *= 1.0 + penalty;
        if (engineKind() == EngineKind.LIFEFORCE && blocksPerSecond > 4.0) factor *= 0.70;
        return Math.max(1, (int) Math.ceil(factor));
    }

    private double serviceDeceleration() {
        double value = profile().serviceDeceleration();
        return engineKind() == EngineKind.PRESSURE ? value * 1.30 : value;
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
        mode = BeetleMode.COMPLETE;
        applyAutomaticBrake(true);
        syncRoutes();
    }

    private void recoverFromInvalidRoute() {
        clearActiveRoute();
        clearPlanningAndProposals();
        mode = BeetleMode.STOPPED;
        BlockPos rail = railPosition();
        if (rail != null && isForwardTerminus(rail)) beginPlanning(rail);
        else applyAutomaticBrake(true);
    }

    private boolean consumeWork(WorkAction action, int rawWork) {
        if (rawWork <= 0) return true;
        EngineKind kind = engineKind();
        int work = BeetlePower.adjustedWork(action, rawWork, profile(), kind);
        ItemStack engineStack = engine.getStackInSlot(0);
        if (!engineStack.isEmpty() && BeetlePower.consumeEngine(engineStack, inventory, work)) return true;
        while (fuelTicks < work) {
            int refill = BeetleSupplies.takeFuel(inventory);
            if (refill <= 0) return false;
            fuelTicks += refill;
        }
        fuelTicks -= work;
        return true;
    }

    private boolean ensureStepPlaced(int stepIndex) {
        if (activeRoute == null) return false;
        RouteStep step = activeRoute.steps().get(stepIndex);
        BlockState existing = level().getBlockState(step.railPos());
        if (existing.getBlock() instanceof BaseRailBlock) {
            RailShape actual = railShape(existing, step.railPos());
            if (actual == step.shape()) return supportsIntact(step)
                    && clearRouteObstruction(step.railPos().above());
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
        int supportCount = step.supportPositions().size();
        boolean needsSupport = supportCount > 0;
        if (BeetleSupplies.countRails(inventory) < 1 || BeetleSupplies.countSupports(inventory) < supportCount) {
            pause();
            return false;
        }
        BlockItem railItem = BeetleSupplies.takeRail(inventory, step.shape());
        List<BlockItem> supportItems = new ArrayList<>(supportCount);
        for (int index = 0; index < supportCount; index++) {
            BlockItem item = BeetleSupplies.takeSupport(inventory);
            if (item != null) supportItems.add(item);
        }
        if (railItem == null || supportItems.size() != supportCount) {
            if (railItem != null) returnItem(new ItemStack(railItem));
            supportItems.forEach(item -> returnItem(new ItemStack(item)));
            pause();
            return false;
        }
        if (!consumeWork(WorkAction.RAIL_PLACEMENT, 4)
                || (needsSupport && !consumeWork(WorkAction.SUPPORT_PLACEMENT, 2 * supportCount))) {
            returnItem(new ItemStack(railItem));
            supportItems.forEach(item -> returnItem(new ItemStack(item)));
            pause();
            return false;
        }
        if (!clearRouteObstruction(step.railPos()) || !clearRouteObstruction(step.railPos().above())) {
            returnItem(new ItemStack(railItem));
            supportItems.forEach(item -> returnItem(new ItemStack(item)));
            invalidateRoute();
            return false;
        }
        for (BlockPos support : step.supportPositions()) {
            if (!clearRouteObstruction(support)) {
                returnItem(new ItemStack(railItem));
                supportItems.forEach(item -> returnItem(new ItemStack(item)));
                invalidateRoute();
                return false;
            }
        }
        for (int index = 0; index < supportCount; index++) {
            if (!level().setBlock(step.supportPositions().get(index),
                    supportItems.get(index).getBlock().defaultBlockState(), 3)) {
                invalidateRoute();
                return false;
            }
        }
        BaseRailBlock railBlock = (BaseRailBlock) railItem.getBlock();
        BlockState railState = railBlock.defaultBlockState().setValue(railBlock.getShapeProperty(), step.shape());
        if (!level().setBlock(step.railPos(), railState, 3)) {
            returnItem(new ItemStack(railItem));
            invalidateRoute();
            return false;
        }
        placedRouteRails.add(step.railPos().immutable());
        return true;
    }

    private boolean builtSupportsIntact() {
        if (activeRoute == null) return true;
        for (RouteStep step : activeRoute.steps()) {
            if (placedRouteRails.contains(step.railPos()) && !supportsIntact(step)) return false;
        }
        return true;
    }

    private boolean supportsIntact(RouteStep step) {
        for (BlockPos support : step.supportPositions()) {
            BlockState state = level().getBlockState(support);
            if (state.isAir() || !state.isCollisionShapeFullBlock(level(), support)) return false;
        }
        return true;
    }

    private boolean clearRouteObstruction(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (state.isAir()) return true;
        if (!RouteObstructions.isClearable(state)) return false;
        if (!consumeWork(WorkAction.CLEARING, 4)) return false;
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
                mode = BeetleMode.PAUSED;
                setDeltaMovement(Vec3.ZERO);
                applyAutomaticBrake(true);
            } else {
                clearActiveRoute();
                clearPlanningAndProposals();
                mode = BeetleMode.STOPPED;
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
        if (forcedBrake || mode != BeetleMode.READY || activeRoute != null) return false;
        RouteProposal selected = findProposal(generation, routeId);
        BlockPos rail = railPosition();
        if (selected == null || rail == null || !rail.equals(selected.origin())
                || noseHeading != selected.originHeading() || !isForwardTerminus(rail)
                || !aimsAt(player, selected) || !TerrainRoutePlanner.isRouteStillValid(level(), selected)) {
            player.displayClientMessage(Component.translatable("message.rail_beetle.route_changed"), true);
            nextRefreshTick = level().getGameTime();
            return false;
        }
        activeRoute = selected;
        activeStep = 0;
        placedRouteRails.clear();
        proposals = List.of();
        previousProposals = List.of();
        clearPlanning();
        mode = BeetleMode.DEPARTING;
        departureTicks = DEPARTURE_TICKS;
        setDeltaMovement(Vec3.ZERO);
        applyAutomaticBrake(false);
        level().playSound(null, blockPosition(), RailBeetleRegistries.WHISTLE.get(), SoundSource.BLOCKS, 2.0f, 1.0f);
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

    public void control(Player player, BeetleControl action) {
        boolean remote = !canNearbyPlayerControl(player);
        if (remote && !canRemoteControl(player)) return;
        if (remote && action != BeetleControl.STOP && !consumeWork(WorkAction.REMOTE, 1)) return;
        switch (action) {
            case TOGGLE_HAND_BRAKE -> {
                forcedBrake = !forcedBrake;
                if (forcedBrake) {
                    if (mode == BeetleMode.NEUTRAL) mode = BeetleMode.STOPPED;
                    if (mode.moves()) mode = activeRoute == null ? BeetleMode.STOPPED : BeetleMode.PAUSED;
                    applyAutomaticBrake(true);
                }
            }
            case TOGGLE_NEUTRAL -> toggleNeutral();
            case STOP -> {
                mode = activeRoute == null ? BeetleMode.STOPPED : BeetleMode.PAUSED;
                setDeltaMovement(Vec3.ZERO);
                applyAutomaticBrake(true);
            }
            case REVERSE -> {
                if (forcedBrake) return;
                clearActiveRoute();
                clearPlanningAndProposals();
                mode = BeetleMode.MANUAL_REVERSE;
                applyAutomaticBrake(false);
            }
            case HALF_SPEED -> selectForwardSpeed(BeetleSpeed.HALF);
            case NORMAL_SPEED -> selectForwardSpeed(BeetleSpeed.NORMAL);
            case DOUBLE_SPEED -> selectForwardSpeed(BeetleSpeed.DOUBLE);
            case TRIPLE_SPEED -> selectForwardSpeed(BeetleSpeed.TRIPLE);
            case TOGGLE_SEARCHLIGHT -> toggleSearchlight();
        }
        syncRoutes();
        syncStatus();
    }

    private void selectForwardSpeed(BeetleSpeed selected) {
        if (selected == BeetleSpeed.DOUBLE && profile().governorTier() < 1) return;
        if (selected == BeetleSpeed.TRIPLE && profile().governorTier() < 2) return;
        speedTier = selected;
        if (forcedBrake) return;
        if (mode == BeetleMode.NEUTRAL) mode = BeetleMode.STOPPED;
        if (activeRoute != null) mode = BeetleMode.AUTO_BUILD;
        else {
            clearPlanningAndProposals();
            mode = BeetleMode.MANUAL_FORWARD;
        }
        applyAutomaticBrake(false);
    }

    private void toggleNeutral() {
        if (mode == BeetleMode.NEUTRAL) {
            mode = BeetleMode.STOPPED;
            applyAutomaticBrake(true);
            return;
        }
        forcedBrake = false;
        clearActiveRoute();
        clearPlanningAndProposals();
        mode = BeetleMode.NEUTRAL;
        setDeltaMovement(Vec3.ZERO);
        applyAutomaticBrake(false);
    }

    private boolean canContextPlayer(Player player) {
        return isAlive() && player.level() == level() && trackingPlayers.contains(player.getUUID());
    }

    private boolean canNearbyPlayerControl(Player player) {
        return isAlive() && player.level() == level() && player.distanceToSqr(this) <= 256.0;
    }

    public boolean canRemoteControl(Player player) {
        int range = profile().remoteRange();
        if (range <= 0 || player.level() != level() || !isAlive()) return false;
        boolean bound = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof RemoteControlItem && getUUID().equals(RemoteControlItem.target(held))) bound = true;
        }
        return bound && (range == Integer.MAX_VALUE || player.distanceToSqr(this) <= (double) range * range);
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
        } else setCurrentCartSpeedCapOnRail((float) (profile().maxBlocksPerSecond() / 20.0));
        CreateCompat.setExternallyStalled(this, brake);
        automaticBrakeApplied = brake;
    }

    private boolean shouldBrake() {
        return forcedBrake || (!mode.moves() && mode != BeetleMode.NEUTRAL);
    }

    @Nullable
    private BlockPos recoverNearbyRail(@Nullable BlockPos rail) {
        if (rail != null || mode == BeetleMode.NEUTRAL || lastRail == null) return rail;
        if (!(level().getBlockState(lastRail).getBlock() instanceof BaseRailBlock)) return null;
        Vec3 center = railCenter(lastRail);
        if (horizontalDistance(position(), center) > 1.25 || Math.abs(getY() - center.y) > 1.5) return null;
        setPos(center.x, getY(), center.z);
        setDeltaMovement(Vec3.ZERO);
        return lastRail;
    }

    @Nullable
    private BlockPos enforceRailBoundary(@Nullable BlockPos rail) {
        if (mode == BeetleMode.NEUTRAL || preTickRail == null) return rail;
        if (rail == null) {
            Vec3 anchor = shouldBrake() && preTickPosition != null ? preTickPosition : railCenter(preTickRail);
            setPos(anchor.x, anchor.y, anchor.z);
            preTickPosition = position();
            setDeltaMovement(Vec3.ZERO);
            handleTerminalArrival(preTickRail);
            return preTickRail;
        }
        if (!mode.moves() || !rail.equals(preTickRail)) return rail;
        Direction desired = mode == BeetleMode.MANUAL_REVERSE ? noseHeading.getOpposite() : noseHeading;
        Direction exit = resolveExit(rail, desired);
        if (exit == null || connectedRail(rail, exit) != null) return rail;
        Vec3 center = railCenter(rail);
        if (forwardDistance(center, position(), exit) <= 0) return rail;
        setPos(center.x, getY(), center.z);
        preTickPosition = position();
        setDeltaMovement(Vec3.ZERO);
        handleTerminalArrival(rail);
        return rail;
    }

    private void handleTerminalArrival(BlockPos rail) {
        if (activeRoute != null && rail.equals(activeRoute.endpoint())) {
            finishRoute();
        } else if (mode == BeetleMode.MANUAL_FORWARD && isForwardTerminus(rail)) {
            beginPlanning(rail);
        } else if (mode == BeetleMode.MANUAL_REVERSE || mode.moves()) {
            mode = BeetleMode.STOPPED;
            applyAutomaticBrake(true);
        }
    }

    private void shoveNearbyEntities() {
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(0.25, 0.1, 0.25), this::canShove)) {
            shoveEntity(entity);
        }
    }

    private boolean canShove(Entity entity) {
        return entity.isAlive() && (entity instanceof LivingEntity || entity.isPushable())
                && !entity.noPhysics && !entity.isSpectator()
                && !entity.isPassenger() && !hasPassenger(entity) && !CreateCompat.isInSameConsist(this, entity);
    }

    private boolean clearLivingCorridor() {
        Vec3 motion = getDeltaMovement();
        if (motion.horizontalDistanceSqr() < 1.0e-6) return false;
        AABB corridor = getBoundingBox().expandTowards(motion.x * 1.5, 0, motion.z * 1.5)
                .inflate(0.35, 0.1, 0.35);
        boolean cleared = false;
        for (Entity entity : level().getEntities(this, corridor,
                candidate -> candidate instanceof LivingEntity && canShove(candidate))) {
            moveLivingAside(entity);
            cleared = true;
        }
        return cleared;
    }

    private void moveLivingAside(Entity entity) {
        Direction travel = mode == BeetleMode.MANUAL_REVERSE ? noseHeading.getOpposite() : noseHeading;
        double tangentX = travel.getStepX();
        double tangentZ = travel.getStepZ();
        double lateralX = -tangentZ;
        double lateralZ = tangentX;
        double side = shoveSide(entity, lateralX, lateralZ);
        entity.move(MoverType.PISTON,
                new Vec3(lateralX * side * 0.72 + tangentX * 0.12, 0.05,
                        lateralZ * side * 0.72 + tangentZ * 0.12));
        entity.push(lateralX * side * LIVING_SHOVE + tangentX * 0.1, 0.05,
                lateralZ * side * LIVING_SHOVE + tangentZ * 0.1);
    }

    private void restoreCommandedSpeed() {
        if (commandedHorizontalSpeed <= 1.0e-5) return;
        Direction travel = mode == BeetleMode.MANUAL_REVERSE ? noseHeading.getOpposite() : noseHeading;
        Vec3 motion = getDeltaMovement();
        double along = motion.x * travel.getStepX() + motion.z * travel.getStepZ();
        if (along + 1.0e-5 >= commandedHorizontalSpeed) return;
        setDeltaMovement(travel.getStepX() * commandedHorizontalSpeed, motion.y,
                travel.getStepZ() * commandedHorizontalSpeed);
    }

    private void shoveEntity(Entity entity) {
        if (!canShove(entity)) return;
        Direction travel = mode == BeetleMode.MANUAL_REVERSE ? noseHeading.getOpposite() : noseHeading;
        double tangentX = travel.getStepX();
        double tangentZ = travel.getStepZ();
        if (entity instanceof AbstractMinecart) {
            entity.push(tangentX * CART_SHOVE, 0, tangentZ * CART_SHOVE);
            return;
        }
        double lateralX = -tangentZ;
        double lateralZ = tangentX;
        double side = shoveSide(entity, lateralX, lateralZ);
        entity.push(lateralX * side * LIVING_SHOVE + tangentX * 0.1, 0.05,
                lateralZ * side * LIVING_SHOVE + tangentZ * 0.1);
    }

    private double shoveSide(Entity entity, double lateralX, double lateralZ) {
        double lateral = (entity.getX() - getX()) * lateralX + (entity.getZ() - getZ()) * lateralZ;
        return Math.abs(lateral) > 0.05 ? Math.signum(lateral)
                : ((entity.getUUID().hashCode() & 1) == 0 ? 1 : -1);
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
        mode = activeRoute == null ? BeetleMode.STOPPED : BeetleMode.PAUSED;
        setDeltaMovement(Vec3.ZERO);
        applyAutomaticBrake(true);
    }

    private void clearActiveRoute() {
        activeRoute = null;
        activeStep = 0;
        departureTicks = 0;
        placedRouteRails.clear();
    }

    private void clearPlanning() {
        if (planningSession != null) planningSession.cancel();
        planningSession = null;
        planningOrigin = null;
        planningPublishedDepth = -1;
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
        entityData.set(DATA_BRAKE_APPLIED, automaticBrakeApplied);
        entityData.set(DATA_FUEL, fuelTicks);
        entityData.set(DATA_RAILS, BeetleSupplies.countRails(inventory));
        entityData.set(DATA_SUPPORTS, BeetleSupplies.countSupports(inventory));
        entityData.set(DATA_PROGRESS, activeStep);
        entityData.set(DATA_ROUTE_LENGTH, activeRoute == null ? 0 : activeRoute.steps().size());
        entityData.set(DATA_NOSE, noseHeading.get2DDataValue());
        entityData.set(DATA_SPEED, speedTier.ordinal());
        entityData.set(DATA_ENGINE, engineKind().ordinal());
        entityData.set(DATA_ENGINE_RESOURCE, engineResource());
        entityData.set(DATA_MODULES, moduleMask());
        if (searchlightOn() && level().getGameTime() % 20 == 0
                && !consumeWork(WorkAction.SEARCHLIGHT, 1)) {
            entityData.set(DATA_SEARCHLIGHT, false);
        }
    }

    private void notifyTrackingPlayers(String translationKey) {
        for (Player player : level().players()) {
            if (player.distanceToSqr(this) <= 128.0 * 128.0) {
                player.displayClientMessage(Component.translatable(translationKey), true);
            }
        }
    }

    private void syncRoutes() {
        if (!level().isClientSide) RailBeetleNetwork.syncRoutes(this, proposals, activeRoute);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        trackingPlayers.add(player.getUUID());
        RailBeetleNetwork.syncRoutesTo(player, this, proposals, activeRoute);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        trackingPlayers.remove(player.getUUID());
        lastContextAction.remove(player.getUUID());
        super.stopSeenByPlayer(player);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level().isClientSide && player.isShiftKeyDown() && held.isEmpty()
                && canConfigureMachinery() && engineKind() == EngineKind.SOUL && transferGoetySoul(player)) {
            return InteractionResult.CONSUME;
        }
        if (held.getItem() instanceof RemoteControlItem) {
            if (!level().isClientSide) {
                if (profile().remoteTier() <= 0) player.displayClientMessage(Component.translatable("message.rail_beetle.remote.no_receiver"), true);
                else {
                    RemoteControlItem.bind(held, this);
                    player.displayClientMessage(Component.translatable("message.rail_beetle.remote.bound"), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) NetworkHooks.openScreen(serverPlayer, this, buffer -> buffer.writeVarInt(getId()));
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private boolean transferGoetySoul(Player player) {
        ItemStack stack = engine.getStackInSlot(0);
        if (!(stack.getItem() instanceof EngineItem item) || item.kind() != EngineKind.SOUL) return false;
        int room = item.kind().capacity() - item.resource(stack);
        if (room <= 0) {
            player.displayClientMessage(Component.translatable("message.rail_beetle.engine.full"), true);
            return true;
        }
        if (!ModList.get().isLoaded("goety")) return false;
        int moved = GoetySoulIntegration.takeSouls(player, room);
        if (moved <= 0) return false;
        item.setResource(stack, item.resource(stack) + moved);
        player.displayClientMessage(Component.translatable("message.rail_beetle.engine.soul_transferred", moved), true);
        syncStatus();
        return true;
    }

    @Override public Component getDisplayName() { return Component.translatable("entity.rail_beetle.rail_beetle"); }

    @Override public boolean canBeRidden() { return false; }

    @Override public boolean isPoweredCart() { return mode() != BeetleMode.NEUTRAL; }

    @Override public boolean isPushable() { return mode() == BeetleMode.NEUTRAL; }

    @Override
    public void push(Entity entity) {
        if (mode() == BeetleMode.NEUTRAL) super.push(entity); else shoveEntity(entity);
    }

    @Override
    public void push(double x, double y, double z) {
        if (mode() == BeetleMode.NEUTRAL) super.push(x, y, z);
    }

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
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) { return new RailBeetleMenu(id, playerInventory, this); }

    public MenuProvider remoteMenuProvider() {
        return new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("screen.rail_beetle.remote.title"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new RemoteBeetleMenu(id, inventory, RailBeetleEntity.this);
            }
        };
    }

    public void writeRemoteOpenData(FriendlyByteBuf buffer) {
        buffer.writeVarInt(getId());
        buffer.writeEnum(engineKind());
        buffer.writeVarInt(engineResource());
        buffer.writeVarInt(fallbackFuel());
        buffer.writeEnum(mode());
        buffer.writeEnum(speedTier());
        buffer.writeLong(moduleMask());
        buffer.writeBoolean(searchlightOn());
    }

    @Override protected Item getDropItem() { return RailBeetleRegistries.RAIL_BEETLE_ITEM.get(); }
    @Override public ItemStack getPickResult() { return new ItemStack(RailBeetleRegistries.RAIL_BEETLE_ITEM.get()); }

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
        dropHandler(engine);
        dropHandler(modules);
    }

    private void dropHandler(ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) spawnAtLocation(stack.copy());
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RailBeetleDataVersion", SAVE_VERSION);
        tag.put("Inventory", inventory.serializeNBT());
        tag.put("Engine", engine.serializeNBT());
        tag.put("Modules", modules.serializeNBT());
        tag.putBoolean("ForcedBrake", forcedBrake);
        tag.putBoolean("Neutral", mode == BeetleMode.NEUTRAL);
        tag.putInt("FuelTicks", fuelTicks);
        tag.putLong("ProposalGeneration", proposalGeneration);
        tag.putInt("ActiveStep", activeStep);
        tag.putString("NoseHeading", noseHeading.getName());
        tag.putString("SpeedTier", speedTier.name());
        tag.putBoolean("Searchlight", entityData.get(DATA_SEARCHLIGHT));
        if (activeRoute != null) tag.put("ActiveRoute", writeRoute(activeRoute));
        long[] placed = placedRouteRails.stream().mapToLong(BlockPos::asLong).toArray();
        tag.putLongArray("PlacedRouteRails", placed);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        engine.deserializeNBT(tag.getCompound("Engine"));
        modules.deserializeNBT(tag.getCompound("Modules"));
        forcedBrake = tag.getBoolean("ForcedBrake");
        fuelTicks = Math.max(0, tag.getInt("FuelTicks"));
        proposalGeneration = tag.getLong("ProposalGeneration");
        speedTier = parseSpeed(tag.getString("SpeedTier"));
        entityData.set(DATA_SEARCHLIGHT, tag.getBoolean("Searchlight"));
        Direction savedHeading = Direction.byName(tag.getString("NoseHeading"));
        if (savedHeading != null && savedHeading.getAxis().isHorizontal()) {
            noseHeading = savedHeading;
            headingInitialized = true;
        }
        boolean hasRouteFormat = tag.getInt("RailBeetleDataVersion") >= 1;
        activeRoute = hasRouteFormat && tag.contains("ActiveRoute", Tag.TAG_COMPOUND) ? readRoute(tag.getCompound("ActiveRoute")) : null;
        activeStep = activeRoute == null ? 0 : Mth.clamp(tag.getInt("ActiveStep"), 0, activeRoute.steps().size());
        placedRouteRails.clear();
        if (activeRoute != null) {
            for (long packed : tag.getLongArray("PlacedRouteRails")) placedRouteRails.add(BlockPos.of(packed));
        }
        mode = tag.getBoolean("Neutral") ? BeetleMode.NEUTRAL : activeRoute == null ? BeetleMode.STOPPED : BeetleMode.PAUSED;
        if (mode == BeetleMode.NEUTRAL) {
            forcedBrake = false;
            clearActiveRoute();
        }
        proposals = List.of();
        previousProposals = List.of();
        clearPlanning();
    }

    private static BeetleSpeed parseSpeed(String name) {
        try { return BeetleSpeed.valueOf(name); }
        catch (IllegalArgumentException exception) { return BeetleSpeed.NORMAL; }
    }

    private static CompoundTag writeRoute(RouteProposal route) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", route.id());
        tag.putLong("Generation", route.generation());
        tag.putLong("Origin", route.origin().asLong());
        tag.putString("OriginHeading", route.originHeading().getName());
        tag.putLong("Endpoint", route.endpoint().asLong());
        tag.putString("Kind", route.kind().name());
        if (route.beaconTarget() != null) tag.putLong("BeaconTarget", route.beaconTarget().asLong());
        ListTag steps = new ListTag();
        for (RouteStep step : route.steps()) {
            CompoundTag stepTag = new CompoundTag();
            stepTag.putLong("Rail", step.railPos().asLong());
            stepTag.putString("Shape", step.shape().name());
            stepTag.putLongArray("Supports", step.supportPositions().stream().mapToLong(BlockPos::asLong).toArray());
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
            List<BlockPos> supports = java.util.Arrays.stream(stepTag.getLongArray("Supports"))
                    .mapToObj(BlockPos::of).toList();
            steps.add(new RouteStep(BlockPos.of(stepTag.getLong("Rail")), shape, supports));
        }
        Direction heading = Direction.byName(tag.getString("OriginHeading"));
        if (heading == null || !heading.getAxis().isHorizontal()) heading = Direction.NORTH;
        RouteKind kind;
        try { kind = RouteKind.valueOf(tag.getString("Kind")); }
        catch (IllegalArgumentException exception) { kind = RouteKind.SURVEY; }
        BlockPos beacon = kind == RouteKind.BEACON && tag.contains("BeaconTarget", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("BeaconTarget")) : null;
        if (kind == RouteKind.BEACON && beacon == null) kind = RouteKind.SURVEY;
        return new RouteProposal(tag.getInt("Id"), tag.getLong("Generation"), BlockPos.of(tag.getLong("Origin")),
                heading, BlockPos.of(tag.getLong("Endpoint")), steps, kind, beacon);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (canConfigureMachinery() && capability == ForgeCapabilities.ITEM_HANDLER) return inventoryCapability.cast();
        if (canConfigureMachinery() && engineKind() == EngineKind.FLUX
                && capability == ForgeCapabilities.ENERGY) return energyCapability.cast();
        if (canConfigureMachinery() && (engineKind() == EngineKind.STEAM || engineKind() == EngineKind.LIFEFORCE)
                && capability == ForgeCapabilities.FLUID_HANDLER) return fluidCapability.cast();
        return super.getCapability(capability, side);
    }

    private long moduleMask() {
        long mask = 0;
        for (int slot = 0; slot < modules.getSlots(); slot++) {
            if (modules.getStackInSlot(slot).getItem() instanceof ModuleItem item) mask |= 1L << item.kind().ordinal();
        }
        return mask;
    }

    private void machineryChanged() {
        if (level() == null || level().isClientSide) return;
        refreshDockingCapabilities();
        if (activeRoute != null || !proposals.isEmpty()) {
            clearActiveRoute();
            clearPlanningAndProposals();
            mode = BeetleMode.STOPPED;
        }
        syncStatus();
    }

    @Override
    public void invalidateCaps() {
        clearPlanning();
        super.invalidateCaps();
        inventoryCapability.invalidate();
        energyCapability.invalidate();
        fluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inventoryCapability = LazyOptional.of(() -> inventory);
        energyCapability = LazyOptional.of(EnergyDock::new);
        fluidCapability = LazyOptional.of(FluidDock::new);
    }

    private void refreshDockingCapabilities() {
        inventoryCapability.invalidate();
        energyCapability.invalidate();
        fluidCapability.invalidate();
        inventoryCapability = LazyOptional.of(() -> inventory);
        energyCapability = LazyOptional.of(EnergyDock::new);
        fluidCapability = LazyOptional.of(FluidDock::new);
    }

    private final class EnergyDock implements IEnergyStorage {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            ItemStack stack = engine.getStackInSlot(0);
            if (!canConfigureMachinery() || !(stack.getItem() instanceof EngineItem item)
                    || item.kind() != EngineKind.FLUX) return 0;
            int accepted = Math.min(Math.max(0, maxReceive), item.kind().capacity() - item.resource(stack));
            if (!simulate && accepted > 0) {
                item.setResource(stack, item.resource(stack) + accepted);
                syncStatus();
            }
            return accepted;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return engineResource(); }
        @Override public int getMaxEnergyStored() { return EngineKind.FLUX.capacity(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return canConfigureMachinery() && engineKind() == EngineKind.FLUX; }
    }

    private final class FluidDock implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) {
            if (tank != 0) return FluidStack.EMPTY;
            ResourceLocation id = engineKind() == EngineKind.STEAM
                    ? ResourceLocation.fromNamespaceAndPath("minecraft", "water")
                    : ResourceLocation.fromNamespaceAndPath("bloodmagic", "life_essence_fluid");
            net.minecraft.world.level.material.Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
            if (fluid == null) return FluidStack.EMPTY;
            ItemStack stack = engine.getStackInSlot(0);
            int amount = engineKind() == EngineKind.STEAM
                    ? stack.getOrCreateTag().getInt(EngineItem.STEAM_WATER_TAG) : engineResource();
            return new FluidStack(fluid, Math.max(0, amount));
        }
        @Override public int getTankCapacity(int tank) {
            return engineKind() == EngineKind.STEAM ? BeetlePower.STEAM_WATER_CAPACITY
                    : engineKind() == EngineKind.LIFEFORCE ? EngineKind.LIFEFORCE.capacity() : 0;
        }
        @Override public boolean isFluidValid(int tank, FluidStack resource) {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(resource.getFluid());
            return tank == 0 && id != null && (engineKind() == EngineKind.STEAM && id.toString().equals("minecraft:water")
                    || engineKind() == EngineKind.LIFEFORCE && id.toString().equals("bloodmagic:life_essence_fluid"));
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (!canConfigureMachinery() || !isFluidValid(0, resource)) return 0;
            ItemStack stack = engine.getStackInSlot(0);
            if (!(stack.getItem() instanceof EngineItem item)) return 0;
            int stored = engineKind() == EngineKind.STEAM
                    ? stack.getOrCreateTag().getInt(EngineItem.STEAM_WATER_TAG) : item.resource(stack);
            int accepted = Math.min(resource.getAmount(), getTankCapacity(0) - stored);
            if (action.execute() && accepted > 0) {
                if (engineKind() == EngineKind.STEAM) stack.getOrCreateTag().putInt(EngineItem.STEAM_WATER_TAG, stored + accepted);
                else item.setResource(stack, stored + accepted);
                syncStatus();
            }
            return accepted;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

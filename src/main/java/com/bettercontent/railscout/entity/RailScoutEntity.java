package com.bettercontent.railscout.entity;

import com.bettercontent.railscout.RailScoutConfig;
import com.bettercontent.railscout.RailScoutRegistries;
import com.bettercontent.railscout.menu.RailScoutMenu;
import com.bettercontent.railscout.navigation.RouteProposal;
import com.bettercontent.railscout.navigation.RouteStep;
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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class RailScoutEntity extends Minecart implements MenuProvider {
    public static final int INVENTORY_SIZE = 27;
    private static final double AUTO_SPEED = 2.0 / 20.0;
    private static final double REVERSE_SPEED = 0.5 / 20.0;
    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FORCED_BRAKE = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RAILS = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SUPPORTS = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROGRESS = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ROUTE_LENGTH = SynchedEntityData.defineId(RailScoutEntity.class, EntityDataSerializers.INT);

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE);
    private LazyOptional<ItemStackHandler> inventoryCapability = LazyOptional.of(() -> inventory);
    private ScoutMode mode = ScoutMode.PLANNING;
    private boolean forcedBrake;
    private int fuelTicks;
    private long proposalGeneration;
    private List<RouteProposal> proposals = List.of();
    @Nullable private TerrainRoutePlanner.Session planningSession;
    @Nullable private RouteProposal activeRoute;
    @Nullable private Direction manualHeading;
    private int activeStep;
    private Vec3 brakeAnchor = Vec3.ZERO;
    private boolean inventoryDropped;

    public RailScoutEntity(EntityType<? extends RailScoutEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MODE, ScoutMode.PLANNING.ordinal());
        entityData.define(DATA_FORCED_BRAKE, false);
        entityData.define(DATA_FUEL, 0);
        entityData.define(DATA_RAILS, 0);
        entityData.define(DATA_SUPPORTS, 0);
        entityData.define(DATA_PROGRESS, 0);
        entityData.define(DATA_ROUTE_LENGTH, 0);
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public ScoutMode mode() {
        if (!level().isClientSide) return mode;
        int ordinal = entityData.get(DATA_MODE);
        return ordinal >= 0 && ordinal < ScoutMode.values().length ? ScoutMode.values()[ordinal] : ScoutMode.PAUSED;
    }

    public boolean forcedBrake() {
        return level().isClientSide ? entityData.get(DATA_FORCED_BRAKE) : forcedBrake;
    }

    public int fuelTicks() {
        return level().isClientSide ? entityData.get(DATA_FUEL) : fuelTicks;
    }

    public int activeStep() {
        return level().isClientSide ? entityData.get(DATA_PROGRESS) : activeStep;
    }

    public int activeRouteLength() {
        return level().isClientSide ? entityData.get(DATA_ROUTE_LENGTH)
                : activeRoute == null ? 0 : activeRoute.steps().size();
    }

    public int railCount() {
        return level().isClientSide ? entityData.get(DATA_RAILS) : ScoutSupplies.countRails(inventory);
    }

    public int supportCount() {
        return level().isClientSide ? entityData.get(DATA_SUPPORTS) : ScoutSupplies.countSupports(inventory);
    }

    public List<RouteProposal> proposals() {
        return proposals;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            serverPreTick();
        }
        super.tick();
        if (!level().isClientSide) {
            serverPostTick();
        }
    }

    private void serverPreTick() {
        if (mode == ScoutMode.COMPLETE) {
            beginPlanning();
        }
        if (mode == ScoutMode.PLANNING) {
            advancePlanning();
        }
        if (forcedBrake || !mode.moves()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (!ensureFuel()) {
            pause("message.rail_scout.missing_fuel");
            return;
        }
        boolean commanded = mode == ScoutMode.AUTO_BUILD
                ? prepareAutomaticMovement()
                : prepareManualMovement();
        if (commanded) fuelTicks--;
    }

    private void serverPostTick() {
        if (forcedBrake || !mode.moves()) {
            if (brakeAnchor == Vec3.ZERO) brakeAnchor = position();
            setPos(brakeAnchor.x, brakeAnchor.y, brakeAnchor.z);
            setDeltaMovement(Vec3.ZERO);
            syncStatus();
            return;
        }
        brakeAnchor = position();
        if ((mode == ScoutMode.MANUAL_FORWARD || mode == ScoutMode.MANUAL_REVERSE)
                && getDeltaMovement().horizontalDistanceSqr() > 1.0e-5) {
            Vec3 motion = getDeltaMovement();
            manualHeading = Direction.getNearest(motion.x, 0, motion.z);
        }
        if (mode == ScoutMode.AUTO_BUILD && activeRoute != null && activeStep < activeRoute.steps().size()) {
            RouteStep step = activeRoute.steps().get(activeStep);
            Vec3 center = railCenter(step.railPos());
            double dx = getX() - center.x;
            double dz = getZ() - center.z;
            if (dx * dx + dz * dz < 0.16 && Math.abs(getY() - center.y) < 1.0) {
                setPos(center.x, center.y, center.z);
                activeStep++;
                if (activeStep >= activeRoute.steps().size()) {
                    mode = ScoutMode.COMPLETE;
                    activeRoute = null;
                    activeStep = 0;
                    setDeltaMovement(Vec3.ZERO);
                    brakeAnchor = position();
                }
            }
        }
        syncStatus();
    }

    private void syncStatus() {
        entityData.set(DATA_MODE, mode.ordinal());
        entityData.set(DATA_FORCED_BRAKE, forcedBrake);
        entityData.set(DATA_FUEL, fuelTicks);
        entityData.set(DATA_RAILS, ScoutSupplies.countRails(inventory));
        entityData.set(DATA_SUPPORTS, ScoutSupplies.countSupports(inventory));
        entityData.set(DATA_PROGRESS, activeStep);
        entityData.set(DATA_ROUTE_LENGTH, activeRoute == null ? 0 : activeRoute.steps().size());
    }

    private void advancePlanning() {
        BlockPos origin = railPosition();
        if (origin == null) {
            mode = ScoutMode.PAUSED;
            return;
        }
        if (planningSession == null) {
            proposalGeneration++;
            Direction facing = Direction.fromYRot(getYRot());
            planningSession = TerrainRoutePlanner.begin(
                    level(), origin, facing, RailScoutConfig.ROUTE_RAIL_CAP.get(), proposalGeneration);
        }
        if (planningSession.advance(1_024)) {
            proposals = planningSession.proposals();
            planningSession = null;
            mode = ScoutMode.READY;
            brakeAnchor = position();
            syncProposals();
        }
    }

    private boolean prepareAutomaticMovement() {
        if (activeRoute == null || activeStep >= activeRoute.steps().size()) {
            mode = ScoutMode.COMPLETE;
            return false;
        }
        RouteStep step = activeRoute.steps().get(activeStep);
        if (!ensureStepPlaced(step)) {
            return false;
        }
        Vec3 toward = railCenter(step.railPos()).subtract(position());
        if (toward.lengthSqr() < 1.0e-5) return false;
        setDeltaMovement(toward.normalize().scale(AUTO_SPEED));
        return true;
    }

    private boolean prepareManualMovement() {
        BlockPos rail = railPosition();
        if (rail == null) {
            pause("message.rail_scout.off_rail");
            return false;
        }
        if (manualHeading == null) manualHeading = Direction.fromYRot(getYRot());
        double speed = mode == ScoutMode.MANUAL_REVERSE ? REVERSE_SPEED : AUTO_SPEED;
        setDeltaMovement(new Vec3(manualHeading.getStepX() * speed, getDeltaMovement().y, manualHeading.getStepZ() * speed));
        return true;
    }

    private boolean ensureFuel() {
        if (fuelTicks > 0) return true;
        fuelTicks = ScoutSupplies.takeFuel(inventory);
        return fuelTicks > 0;
    }

    private boolean ensureStepPlaced(RouteStep step) {
        BlockState existing = level().getBlockState(step.railPos());
        if (existing.getBlock() instanceof BaseRailBlock) return true;
        if (!existing.isAir() || !level().getBlockState(step.railPos().above()).isAir()) {
            invalidateRoute("message.rail_scout.route_changed");
            return false;
        }
        boolean needsSupport = step.supportPos() != null && level().getBlockState(step.supportPos()).isAir();
        if (needsSupport) {
            BlockPos below = step.supportPos().below();
            if (!level().getBlockState(below).isFaceSturdy(level(), below, Direction.UP)) {
                invalidateRoute("message.rail_scout.route_changed");
                return false;
            }
        }
        if (ScoutSupplies.countRails(inventory) < 1) {
            pause("message.rail_scout.missing_rails");
            return false;
        }
        if (needsSupport && ScoutSupplies.countSupports(inventory) < 1) {
            pause("message.rail_scout.missing_supports");
            return false;
        }

        BlockItem railItem = ScoutSupplies.takeRail(inventory);
        BlockItem supportItem = needsSupport ? ScoutSupplies.takeSupport(inventory) : null;
        if (railItem == null || (needsSupport && supportItem == null)) {
            pause("message.rail_scout.missing_materials");
            return false;
        }
        if (needsSupport) {
            BlockPos supportPos = step.supportPos();
            if (!level().setBlock(supportPos, supportItem.getBlock().defaultBlockState(), 3)) {
                returnItem(new ItemStack(railItem));
                returnItem(new ItemStack(supportItem));
                invalidateRoute("message.rail_scout.route_changed");
                return false;
            }
        }

        BaseRailBlock railBlock = (BaseRailBlock) railItem.getBlock();
        BlockState railState = railBlock.defaultBlockState();
        if (railState.hasProperty(railBlock.getShapeProperty())) {
            railState = railState.setValue(railBlock.getShapeProperty(), step.shape());
        }
        if (!level().setBlock(step.railPos(), railState, 3)) {
            if (needsSupport) level().setBlock(step.supportPos(), Blocks.AIR.defaultBlockState(), 3);
            returnItem(new ItemStack(railItem));
            if (supportItem != null) returnItem(new ItemStack(supportItem));
            invalidateRoute("message.rail_scout.route_changed");
            return false;
        }
        return true;
    }

    public void selectRoute(Player player, long generation, int routeId) {
        if (!canPlayerControl(player) || forcedBrake) return;
        RouteProposal selected = proposals.stream()
                .filter(route -> route.generation() == generation && route.id() == routeId)
                .findFirst().orElse(null);
        if (selected == null || !rayHits(player, selected)) return;
        activeRoute = selected;
        manualHeading = null;
        activeStep = 0;
        proposals = List.of();
        syncProposals();
        mode = ScoutMode.AUTO_BUILD;
        brakeAnchor = position();
    }

    public void control(ServerPlayer player, ScoutControl action) {
        if (!canPlayerControl(player)) return;
        switch (action) {
            case TOGGLE_HAND_BRAKE -> {
                forcedBrake = !forcedBrake;
                mode = ScoutMode.PAUSED;
                brakeAnchor = position();
            }
            case STOP -> {
                if (activeRoute == null) beginPlanning(); else mode = ScoutMode.PAUSED;
                brakeAnchor = position();
            }
            case FORWARD -> {
                if (forcedBrake) return;
                mode = activeRoute != null ? ScoutMode.AUTO_BUILD : ScoutMode.MANUAL_FORWARD;
                if (mode == ScoutMode.MANUAL_FORWARD) manualHeading = Direction.fromYRot(getYRot());
            }
            case SLOW_REVERSE -> {
                if (forcedBrake) return;
                activeRoute = null;
                activeStep = 0;
                proposals = List.of();
                syncProposals();
                mode = ScoutMode.MANUAL_REVERSE;
                manualHeading = Direction.fromYRot(getYRot()).getOpposite();
            }
        }
    }

    private boolean canPlayerControl(Player player) {
        return isAlive() && player.level() == level() && player.distanceToSqr(this) <= 256.0;
    }

    private boolean rayHits(Player player, RouteProposal proposal) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(96.0));
        for (RouteStep step : proposal.steps()) {
            Vec3 center = railCenter(step.railPos());
            if (new AABB(center, center).inflate(0.8).clip(eye, end).isPresent()) return true;
        }
        return false;
    }

    private void beginPlanning() {
        activeRoute = null;
        activeStep = 0;
        proposals = List.of();
        planningSession = null;
        manualHeading = null;
        mode = ScoutMode.PLANNING;
        brakeAnchor = position();
        syncProposals();
    }

    private void invalidateRoute(String messageKey) {
        mode = ScoutMode.PLANNING;
        activeRoute = null;
        activeStep = 0;
        planningSession = null;
        proposals = List.of();
        setDeltaMovement(Vec3.ZERO);
        brakeAnchor = position();
        syncProposals();
    }

    private void pause(String messageKey) {
        mode = ScoutMode.PAUSED;
        setDeltaMovement(Vec3.ZERO);
        brakeAnchor = position();
    }

    private void returnItem(ItemStack stack) {
        ItemStack pending = stack;
        for (int slot = 0; slot < inventory.getSlots() && !pending.isEmpty(); slot++) {
            pending = inventory.insertItem(slot, pending, false);
        }
        if (!pending.isEmpty()) spawnAtLocation(pending);
    }

    @Nullable
    private BlockPos railPosition() {
        BlockPos at = blockPosition();
        if (level().getBlockState(at).getBlock() instanceof BaseRailBlock) return at;
        BlockPos below = at.below();
        return level().getBlockState(below).getBlock() instanceof BaseRailBlock ? below : null;
    }

    private static Vec3 railCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.0625, pos.getZ() + 0.5);
    }

    private void syncProposals() {
        if (!level().isClientSide) RailScoutNetwork.syncProposals(this, proposals);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        syncProposals();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, this, buffer -> buffer.writeVarInt(getId()));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.rail_scout.rail_scout");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new RailScoutMenu(id, playerInventory, this);
    }

    @Override
    protected Item getDropItem() {
        return RailScoutRegistries.RAIL_SCOUT_ITEM.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(RailScoutRegistries.RAIL_SCOUT_ITEM.get());
    }

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
        tag.put("Inventory", inventory.serializeNBT());
        tag.putString("Mode", mode.name());
        tag.putBoolean("ForcedBrake", forcedBrake);
        tag.putInt("FuelTicks", fuelTicks);
        tag.putLong("ProposalGeneration", proposalGeneration);
        tag.putInt("ActiveStep", activeStep);
        tag.putDouble("BrakeX", brakeAnchor.x);
        tag.putDouble("BrakeY", brakeAnchor.y);
        tag.putDouble("BrakeZ", brakeAnchor.z);
        if (activeRoute != null) tag.put("ActiveRoute", writeRoute(activeRoute));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        forcedBrake = tag.getBoolean("ForcedBrake");
        fuelTicks = Math.max(0, tag.getInt("FuelTicks"));
        proposalGeneration = tag.getLong("ProposalGeneration");
        activeStep = Math.max(0, tag.getInt("ActiveStep"));
        brakeAnchor = new Vec3(tag.getDouble("BrakeX"), tag.getDouble("BrakeY"), tag.getDouble("BrakeZ"));
        activeRoute = tag.contains("ActiveRoute", Tag.TAG_COMPOUND) ? readRoute(tag.getCompound("ActiveRoute")) : null;
        mode = ScoutMode.PAUSED;
        proposals = List.of();
        planningSession = null;
    }

    private static CompoundTag writeRoute(RouteProposal route) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", route.id());
        tag.putLong("Generation", route.generation());
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
            try {
                shape = RailShape.valueOf(stepTag.getString("Shape"));
            } catch (IllegalArgumentException exception) {
                shape = RailShape.NORTH_SOUTH;
            }
            BlockPos support = stepTag.contains("Support", Tag.TAG_LONG)
                    ? BlockPos.of(stepTag.getLong("Support")) : null;
            steps.add(new RouteStep(BlockPos.of(stepTag.getLong("Rail")), shape, support));
        }
        return new RouteProposal(tag.getInt("Id"), tag.getLong("Generation"),
                BlockPos.of(tag.getLong("Endpoint")), steps);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) return inventoryCapability.cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryCapability.invalidate();
    }

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

package com.bettercontent.railscout;

import com.bettercontent.railscout.entity.RailScoutEntity;
import com.bettercontent.railscout.entity.ScoutMode;
import com.bettercontent.railscout.navigation.RouteProposal;
import com.bettercontent.railscout.navigation.TerrainRoutePlanner;
import com.bettercontent.railscout.network.ScoutControl;
import com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController;
import com.simibubi.create.content.contraptions.minecart.CouplingHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(RailScoutMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RailScoutGameTests {
    private static final String TEMPLATE = "empty";

    private RailScoutGameTests() {}

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void caveIntegrationDependenciesAreLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create"), "Create must be loaded");
        helper.assertTrue(ModList.get().isLoaded("alexscaves"), "Alex's Caves 2.0.2 must be loaded");
        helper.assertTrue(ModList.get().isLoaded("bettercaves"), "YUNG's Better Caves 2.0.6 must be loaded");
        helper.assertTrue(ModList.get().isLoaded("yungsapi"), "YUNG's API 4.0.6 must be loaded");
        helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(ResourceLocation.fromNamespaceAndPath(RailScoutMod.MOD_ID, "whistle")),
                "Rail Scout whistle sound event must be registered");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void plannerNeverEntersWater(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareSingleExit(helper, origin);
        helper.getLevel().setBlockAndUpdate(origin.north(), Blocks.WATER.defaultBlockState());

        TerrainRoutePlanner.Session session = TerrainRoutePlanner.begin(helper.getLevel(), origin, Direction.NORTH, 8, 42L);
        while (!session.advance(4_096)) {
            // Bounded deterministic search; finish synchronously inside the test.
        }
        helper.assertTrue(session.proposals().isEmpty(), "planner must reject a fluid-filled rail space");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void plannerNeverBuildsBesideExistingRail(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareSingleExit(helper, origin);
        BlockPos candidate = origin.north();
        helper.getLevel().setBlockAndUpdate(candidate,
                Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(candidate.east(),
                Blocks.RAIL.defaultBlockState());

        TerrainRoutePlanner.Session session = TerrainRoutePlanner.begin(helper.getLevel(), origin, Direction.NORTH, 8, 43L);
        while (!session.advance(4_096)) {
            // Bounded deterministic search; finish synchronously inside the test.
        }
        helper.assertTrue(session.proposals().isEmpty(), "planner must reject rail beside an unrelated existing rail");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 200)
    public static void plannerReturnsThreeBoundedDiverseCaveFloorRoutes(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(12, 2, 12));
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                helper.getLevel().setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(origin.offset(x, 0, z), Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(origin.offset(x, 1, z), Blocks.AIR.defaultBlockState());
            }
        }
        helper.getLevel().setBlockAndUpdate(origin,
                Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.NORTH_SOUTH));

        TerrainRoutePlanner.Session session = TerrainRoutePlanner.begin(helper.getLevel(), origin, Direction.NORTH, 8, 41L);
        while (!session.advance(4_096)) {
            // Bounded deterministic search; finish synchronously inside the test.
        }
        List<RouteProposal> routes = session.proposals();
        helper.assertTrue(routes.size() == 3, "open cave floor must produce three proposals");
        helper.assertTrue(routes.stream().allMatch(route -> route.railCount() <= 8), "every route must honor its rail cap");
        helper.assertTrue(routes.stream().map(RouteProposal::endpoint).distinct().count() == 3,
                "proposal endpoints must be distinct");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void scoutIsCreateCouplingCapableAndBrakesWhilePlanning(GameTestHelper helper) {
        BlockPos rail = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(rail, Blocks.RAIL.defaultBlockState());
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(rail.getX() + 0.5, rail.getY() + 0.0625, rail.getZ() + 0.5);
        scout.setDeltaMovement(0.1, 0, 0);
        helper.getLevel().addFreshEntity(scout);
        Vec3Holder start = new Vec3Holder(scout.getX(), scout.getY(), scout.getZ());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(scout.getCapability(CapabilityMinecartController.MINECART_CONTROLLER_CAPABILITY).isPresent(),
                    "Create must attach its standard minecart coupling capability to Rail Scout");
            double moved = scout.position().distanceToSqr(start.x, start.y, start.z);
            helper.assertTrue(moved < 0.01, "automatic planning brake must prevent rolling");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void scoutRejectsRidersAndUsesPoweredCollisionPriority(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int x = 0; x <= 7; x++) placeEastWestRail(helper, start.offset(x, 0, 0));
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(start.getX() + 0.5, start.getY() + 0.0625, start.getZ() + 0.5);
        scout.setInitialHeading(Direction.EAST);
        scout.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        var pig = EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(pig != null, "test pig must create");
        pig.setPos(scout.getX() + 0.1, scout.getY(), scout.getZ());
        helper.getLevel().addFreshEntity(scout);
        helper.getLevel().addFreshEntity(pig);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(scout.getX(), scout.getY(), scout.getZ() + 1.0);

        helper.assertTrue(!scout.canBeRidden(), "Scout must not accept automatic mob passengers");
        helper.assertTrue(!scout.isPushable(), "Scout must reject ordinary entity shove impulses");
        helper.assertTrue(scout.isPoweredCart(), "Scout must receive furnace-cart collision priority");
        helper.assertTrue(ForgeRegistries.BLOCKS.getKey(scout.getDefaultDisplayBlockState().getBlock())
                        .equals(ResourceLocation.fromNamespaceAndPath("create", "brass_casing")),
                "Create installations must render a brass casing in the Scout");
        scout.control(player, ScoutControl.HALF_SPEED);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!scout.hasPassenger(pig) && pig.getVehicle() != scout,
                    "nearby mobs must never be collected as Scout passengers");
            helper.assertTrue(scout.noseHeading() == Direction.EAST,
                    "mob contact must not reverse the Scout's commanded heading");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void ordinaryMinecartImpactDoesNotReverseScout(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 8));
        for (int x = 0; x <= 12; x++) placeEastWestRail(helper, start.offset(x, 0, 0));
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(start.getX() + 3.5, start.getY() + 0.0625, start.getZ() + 0.5);
        scout.setInitialHeading(Direction.EAST);
        scout.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        Minecart incoming = new Minecart(helper.getLevel(),
                start.getX() + 7.5, start.getY() + 0.0625, start.getZ() + 0.5);
        incoming.setDeltaMovement(-0.2, 0, 0);
        helper.getLevel().addFreshEntity(scout);
        helper.getLevel().addFreshEntity(incoming);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(scout.getX(), scout.getY(), scout.getZ() + 1.0);
        double initialX = scout.getX();
        List<Double> samples = new ArrayList<>();
        scout.control(player, ScoutControl.NORMAL_SPEED);
        helper.onEachTick(() -> {
            if (helper.getTick() <= 70) samples.add(scout.getX());
            helper.assertTrue(scout.noseHeading() == Direction.EAST,
                    "ordinary minecart impact must not reverse Forward");
        });

        helper.runAfterDelay(75, () -> {
            helper.assertTrue(scout.getX() > initialX + 2.0,
                    "powered collision priority must let the Scout continue east; pos=" + scout.position());
            for (int index = 1; index < samples.size(); index++) {
                helper.assertTrue(samples.get(index) - samples.get(index - 1) > -0.08,
                        "ordinary minecart impact must not produce a reversing impulse");
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void repeatedForwardControlPreservesLampDirection(GameTestHelper helper) {
        BlockPos rail = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int x = -1; x <= 1; x++) {
            helper.getLevel().setBlockAndUpdate(rail.offset(x, -1, 0), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail.offset(x, 0, 0),
                    Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                            RailShape.EAST_WEST));
        }
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(rail.getX() + 0.5, rail.getY() + 0.0625, rail.getZ() + 0.5);
        scout.setInitialHeading(Direction.EAST);
        scout.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(scout);

        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(scout.getX(), scout.getY(), scout.getZ() + 1.0);
        scout.control(player, ScoutControl.NORMAL_SPEED);
        scout.control(player, ScoutControl.NORMAL_SPEED);
        helper.assertTrue(scout.mode() == ScoutMode.MANUAL_FORWARD,
                "repeated Forward controls must remain in forward mode");
        helper.assertTrue(scout.noseHeading() == Direction.EAST,
                "repeated Forward controls must not reverse the lamp-defined nose");
        helper.assertTrue(Math.abs(scout.speedTier().blocksPerTick() - 0.2) < 1.0e-9,
                "new 1x speed must be 4 blocks/second, twice the old base speed");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void scoutPlacedMidRailDoesNotPlan(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(6, 2, 6));
        for (int z = -2; z <= 2; z++) {
            BlockPos rail = center.offset(0, 0, z);
            helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail,
                    Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                            RailShape.NORTH_SOUTH));
        }
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(center.getX() + 0.5, center.getY() + 0.0625, center.getZ() + 0.5);
        scout.setInitialHeading(Direction.NORTH);
        helper.getLevel().addFreshEntity(scout);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(scout.mode() == ScoutMode.STOPPED,
                    "a Scout placed in the middle of track must remain stopped, not plan");
            helper.assertTrue(scout.proposals().isEmpty(),
                    "a Scout placed in the middle of track must not publish routes");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void coupledPassengerCartMovesWithoutScoutSnaps(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 12));
        for (int x = 0; x <= 15; x++) {
            BlockPos rail = start.offset(x, 0, 0);
            helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail,
                    Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                            RailShape.EAST_WEST));
        }
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(start.getX() + 2.5, start.getY() + 0.0625, start.getZ() + 0.5);
        scout.setInitialHeading(Direction.EAST);
        scout.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        Minecart passengerCart = new Minecart(helper.getLevel(),
                start.getX() + 0.5, start.getY() + 0.0625, start.getZ() + 0.5);
        helper.getLevel().addFreshEntity(scout);
        helper.getLevel().addFreshEntity(passengerCart);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(passengerCart.getX(), passengerCart.getY(), passengerCart.getZ());
        player.startRiding(passengerCart, true);
        List<Double> samples = new ArrayList<>();

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(CouplingHandler.tryToCoupleCarts(null, helper.getLevel(), scout.getId(), passengerCart.getId()),
                    "Create must couple the Rail Scout to the passenger cart");
            scout.control(player, ScoutControl.NORMAL_SPEED);
        });
        helper.onEachTick(() -> {
            if (helper.getTick() >= 3 && helper.getTick() <= 70) samples.add(scout.getX());
        });
        helper.runAfterDelay(75, () -> {
            var controller = scout.getCapability(CapabilityMinecartController.MINECART_CONTROLLER_CAPABILITY).orElse(null);
            helper.assertTrue(controller != null
                            && (controller.isLeadingCoupling() || controller.isConnectedToCoupling()),
                    "normal Scout traction must preserve the Create coupling");
            helper.assertTrue(player.getVehicle() == passengerCart,
                    "the test passenger must remain mounted in the coupled cart");
            helper.assertTrue(samples.size() > 20 && samples.get(samples.size() - 1) > samples.get(0) + 1.0,
                    "the coupled Scout must make forward progress");
            for (int index = 1; index < samples.size(); index++) {
                double delta = samples.get(index) - samples.get(index - 1);
                helper.assertTrue(delta > -0.08 && delta < 0.65,
                        "coupled movement must not contain backward impulses or position snaps; delta=" + delta);
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void forwardRecoversFromRollbackAndClimbsWithoutFlippingLamp(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(4, 2, 6));
        placeEastHill(helper, foot, 7);
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        BlockPos topStart = foot.offset(2, 1, 0);
        scout.setPos(topStart.getX() + 0.5, topStart.getY() + 0.0625, topStart.getZ() + 0.5);
        scout.setInitialHeading(Direction.EAST);
        scout.setDeltaMovement(-0.18, 0, 0);
        scout.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(scout);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(scout.getX(), scout.getY(), scout.getZ() + 1.0);
        AtomicBoolean crossedBackward = new AtomicBoolean();
        double startX = scout.getX();
        scout.control(player, ScoutControl.HALF_SPEED);

        helper.onEachTick(() -> {
            if (scout.getX() < startX - 0.55) crossedBackward.set(true);
            helper.assertTrue(scout.noseHeading() == Direction.EAST,
                    "rollback must not redefine Forward or flip the amber lamp; pos=" + scout.position());
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(crossedBackward.get(), "test impulse must carry the Scout across a rail boundary");
            helper.assertTrue(scout.getX() > startX + 1.0,
                    "Forward must recover from rollback and climb east; pos=" + scout.position());
            helper.assertTrue(scout.getY() > foot.getY() + 0.7,
                    "recovered Scout must remain on the upper railway; pos=" + scout.position());
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void coupledPassengerCartClimbsSlopeWithoutSnaps(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(6, 2, 16));
        for (int x = -2; x < 0; x++) placeEastWestRail(helper, foot.offset(x, 0, 0));
        placeEastHill(helper, foot, 7);
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(foot.getX() + 0.5, foot.getY() + 0.0625, foot.getZ() + 0.5);
        scout.setInitialHeading(Direction.EAST);
        scout.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        Minecart passengerCart = new Minecart(helper.getLevel(),
                foot.getX() - 1.5, foot.getY() + 0.0625, foot.getZ() + 0.5);
        helper.getLevel().addFreshEntity(scout);
        helper.getLevel().addFreshEntity(passengerCart);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(passengerCart.getX(), passengerCart.getY(), passengerCart.getZ());
        player.startRiding(passengerCart, true);
        List<Double> samples = new ArrayList<>();

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(CouplingHandler.tryToCoupleCarts(null, helper.getLevel(), scout.getId(), passengerCart.getId()),
                    "Create must couple the Rail Scout to the passenger cart");
            scout.control(player, ScoutControl.NORMAL_SPEED);
        });
        helper.onEachTick(() -> {
            if (helper.getTick() >= 3 && helper.getTick() <= 100) samples.add(scout.getX());
            helper.assertTrue(scout.noseHeading() == Direction.EAST,
                    "coupled hill travel must keep the commanded forward heading");
        });
        helper.runAfterDelay(105, () -> {
            var controller = scout.getCapability(CapabilityMinecartController.MINECART_CONTROLLER_CAPABILITY).orElse(null);
            helper.assertTrue(controller != null
                            && (controller.isLeadingCoupling() || controller.isConnectedToCoupling()),
                    "hill traction must preserve the Create coupling");
            helper.assertTrue(player.getVehicle() == passengerCart,
                    "the passenger must remain mounted throughout the climb");
            helper.assertTrue(scout.getX() > foot.getX() + 3.0 && scout.getY() > foot.getY() + 0.7,
                    "the coupled Scout must climb onto the upper railway; pos=" + scout.position());
            for (int index = 1; index < samples.size(); index++) {
                double delta = samples.get(index) - samples.get(index - 1);
                helper.assertTrue(delta > -0.08 && delta < 0.65,
                        "coupled hill movement must not snap or reverse; delta=" + delta);
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void doubleSpeedClimbsSlopeWithoutChangingForward(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(4, 2, 10));
        placeEastHill(helper, foot, 12);
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(foot.getX() + 0.5, foot.getY() + 0.0625, foot.getZ() + 0.5);
        scout.setInitialHeading(Direction.EAST);
        scout.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(scout);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(scout.getX(), scout.getY(), scout.getZ() + 1.0);
        scout.control(player, ScoutControl.DOUBLE_SPEED);

        helper.runAfterDelay(35, () -> {
            helper.assertTrue(scout.getX() > foot.getX() + 2.0 && scout.getY() > foot.getY() + 0.7,
                    "2x Forward must climb onto the upper railway; pos=" + scout.position());
            helper.assertTrue(scout.noseHeading() == Direction.EAST,
                    "2x hill travel must preserve the lamp-defined Forward direction");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void forwardAndReverseKeepSemanticHeadingThroughCorners(GameTestHelper helper) {
        BlockPos forwardCurve = helper.absolutePos(new BlockPos(8, 2, 8));
        placeNorthWestCorner(helper, forwardCurve);
        RailScoutEntity forward = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(forward != null, "forward Scout entity type must create");
        forward.setPos(forwardCurve.getX() - 1.5, forwardCurve.getY() + 0.0625, forwardCurve.getZ() + 0.5);
        forward.setInitialHeading(Direction.EAST);
        forward.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(forward);
        net.minecraft.world.entity.player.Player forwardPlayer = helper.makeMockPlayer();
        forwardPlayer.setPos(forward.getX(), forward.getY(), forward.getZ() + 1.0);
        forward.control(forwardPlayer, ScoutControl.NORMAL_SPEED);

        BlockPos reverseCurve = helper.absolutePos(new BlockPos(18, 2, 18));
        placeNorthWestCorner(helper, reverseCurve);
        RailScoutEntity reverse = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(reverse != null, "reverse Scout entity type must create");
        reverse.setPos(reverseCurve.getX() + 0.5, reverseCurve.getY() + 0.0625, reverseCurve.getZ() - 1.5);
        reverse.setInitialHeading(Direction.NORTH);
        reverse.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(reverse);
        net.minecraft.world.entity.player.Player reversePlayer = helper.makeMockPlayer();
        reversePlayer.setPos(reverse.getX(), reverse.getY(), reverse.getZ() - 1.0);
        reverse.control(reversePlayer, ScoutControl.REVERSE);
        AtomicBoolean forwardTurned = new AtomicBoolean();
        AtomicBoolean reverseTurned = new AtomicBoolean();

        helper.onEachTick(() -> {
            if (forward.noseHeading() == Direction.NORTH && forwardTurned.compareAndSet(false, true)) {
                forward.control(forwardPlayer, ScoutControl.STOP);
            }
            if (reverse.noseHeading() == Direction.EAST && reverseTurned.compareAndSet(false, true)) {
                reverse.control(reversePlayer, ScoutControl.STOP);
            }
        });

        helper.runAfterDelay(90, () -> {
            helper.assertTrue(forwardTurned.get(), "Forward must select the north exit through the corner");
            helper.assertTrue(forward.noseHeading() == Direction.NORTH,
                    "Forward nose must rotate to the intentionally selected north exit");
            helper.assertTrue(reverseTurned.get(), "Reverse must select the west exit through the corner");
            helper.assertTrue(reverse.noseHeading() == Direction.EAST,
                    "after reversing west, the nose must still show that Forward means east");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 400)
    public static void automaticRouteRewindsAndRecoversAfterRollback(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareSouthCorridor(helper, origin, 9);
        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(origin.getX() + 0.5, origin.getY() + 0.0625, origin.getZ() + 0.5);
        scout.setInitialHeading(Direction.SOUTH);
        scout.inventory().setStackInSlot(0, new ItemStack(Blocks.RAIL, 24));
        scout.inventory().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(scout);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        AtomicInteger progressBeforeRollback = new AtomicInteger();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!scout.proposals().isEmpty(),
                        "Scout must plan the straight rollback test corridor"))
                .thenExecute(() -> {
                    RouteProposal route = scout.proposals().get(0);
                    BlockPos first = route.steps().get(0).railPos();
                    player.setPos(scout.getX(), scout.getY() + 1.0, scout.getZ() - 2.0);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES,
                            new net.minecraft.world.phys.Vec3(first.getX() + 0.5, first.getY() + 0.2, first.getZ() + 0.5));
                    scout.selectRoute(player, route.generation(), route.id());
                    helper.assertTrue(scout.mode() == ScoutMode.DEPARTING,
                            "selected rollback route must enter its departure phase");
                })
                .thenWaitUntil(() -> helper.assertTrue(scout.activeStep() >= 3,
                        "Scout must advance far enough to exercise route rollback; progress=" + scout.activeStep()))
                .thenExecute(() -> {
                    progressBeforeRollback.set(scout.activeStep());
                    scout.setDeltaMovement(0, 0, -0.22);
                })
                .thenWaitUntil(() -> helper.assertTrue(scout.activeStep() < progressBeforeRollback.get(),
                        "route cursor must rewind when the Scout crosses onto an earlier route rail; progress="
                                + scout.activeStep() + ", before=" + progressBeforeRollback.get()))
                .thenWaitUntil(() -> helper.assertTrue(scout.activeStep() > progressBeforeRollback.get(),
                        "automatic Forward must recover and resume route progress; progress=" + scout.activeStep()))
                .thenExecute(() -> helper.assertTrue(scout.noseHeading() == Direction.SOUTH,
                        "automatic rollback must not flip the route-defined forward heading"))
                .thenSucceed();
    }

    @GameTest(templateNamespace = RailScoutMod.MOD_ID, template = TEMPLATE, timeoutTicks = 300)
    public static void scoutBuildsRailAndShallowSupportThroughCaveCorridor(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int z = 0; z <= 7; z++) {
            BlockPos railPos = origin.offset(0, 0, z);
            BlockPos floor = railPos.below();
            helper.getLevel().setBlockAndUpdate(floor, z == 3 ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(floor.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(railPos, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(railPos.above(), Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(railPos.east(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(railPos.west(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(railPos.east().above(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(railPos.west().above(), Blocks.STONE.defaultBlockState());
        }
        helper.getLevel().setBlockAndUpdate(origin,
                Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.NORTH_SOUTH));
        helper.getLevel().setBlockAndUpdate(origin.offset(0, 0, 7), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin.offset(0, 1, 7), Blocks.STONE.defaultBlockState());

        RailScoutEntity scout = RailScoutRegistries.RAIL_SCOUT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "Scout entity type must create");
        scout.setPos(origin.getX() + 0.5, origin.getY() + 0.0625, origin.getZ() + 0.5);
        scout.setInitialHeading(Direction.SOUTH);
        scout.inventory().setStackInSlot(0, new ItemStack(Blocks.RAIL, 16));
        scout.inventory().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        scout.inventory().setStackInSlot(2, new ItemStack(Blocks.COBBLESTONE, 16));
        helper.getLevel().addFreshEntity(scout);

        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        AtomicReference<RouteProposal> selectedRoute = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!scout.proposals().isEmpty(),
                        "Scout must finish planning the corridor; mode=" + scout.mode()
                                + ", pos=" + scout.blockPosition()
                                + ", block=" + helper.getLevel().getBlockState(scout.blockPosition())))
                .thenExecute(() -> {
                    RouteProposal route = scout.proposals().get(0);
                    helper.assertTrue(route.supportCount() == 1, "corridor route must plan exactly one shallow support");
                    selectedRoute.set(route);
                    Vec3Holder target = new Vec3Holder(
                            route.steps().get(0).railPos().getX() + 0.5,
                            route.steps().get(0).railPos().getY() + 0.2,
                            route.steps().get(0).railPos().getZ() + 0.5);
                    player.setPos(scout.getX(), scout.getY() + 1.0, scout.getZ() - 2.0);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES,
                            new net.minecraft.world.phys.Vec3(target.x, target.y, target.z));
                    scout.selectRoute(player, route.generation(), route.id());
                    helper.assertTrue(scout.mode() == ScoutMode.DEPARTING,
                            "crosshair-selected route must start its whistle/departure phase");
                })
                .thenWaitUntil(() -> {
                    RouteProposal route = selectedRoute.get();
                    var supportedStep = route.steps().stream().filter(step -> step.supportPos() != null).findFirst().orElseThrow();
                    BlockPos supportedRail = supportedStep.railPos();
                    helper.assertTrue(helper.getLevel().getBlockState(supportedRail).getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock,
                            "Scout must place rail over the shallow gap; mode=" + scout.mode()
                                    + ", pos=" + scout.position() + ", progress=" + scout.activeStep()
                                    + ", expected=" + supportedRail
                                    + ", z1=" + helper.getLevel().getBlockState(origin.offset(0, 0, 1))
                                    + ", z2=" + helper.getLevel().getBlockState(origin.offset(0, 0, 2)));
                    helper.assertTrue(helper.getLevel().getBlockState(supportedStep.supportPos()).is(Blocks.COBBLESTONE),
                            "Scout must consume and place a support below the rail");
                    helper.assertTrue(scout.inventory().getStackInSlot(0).getCount() < 16,
                            "Scout must consume rails from its ordinary multi-slot inventory");
                    helper.assertTrue(scout.fuelTicks() > 0, "Scout must load furnace fuel while moving");
                })
                .thenSucceed();
    }

    private static void prepareSingleExit(GameTestHelper helper, BlockPos origin) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = origin.relative(direction);
            helper.getLevel().setBlockAndUpdate(candidate.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(candidate, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(candidate.above(), Blocks.AIR.defaultBlockState());
        }
        helper.getLevel().setBlockAndUpdate(origin.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin,
                Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                        RailShape.NORTH_SOUTH));
        helper.getLevel().setBlockAndUpdate(origin.east(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin.west(), Blocks.STONE.defaultBlockState());
    }

    private static void placeEastHill(GameTestHelper helper, BlockPos foot, int length) {
        placeEastWestRail(helper, foot);
        BlockPos slope = foot.east();
        helper.getLevel().setBlockAndUpdate(slope.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(slope,
                Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                        RailShape.ASCENDING_EAST));
        for (int x = 2; x <= length; x++) placeEastWestRail(helper, foot.offset(x, 1, 0));
    }

    private static void placeEastWestRail(GameTestHelper helper, BlockPos rail) {
        helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(rail,
                Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                        RailShape.EAST_WEST));
    }

    private static void placeNorthWestCorner(GameTestHelper helper, BlockPos curve) {
        for (int x = 1; x <= 3; x++) placeEastWestRail(helper, curve.west(x));
        helper.getLevel().setBlockAndUpdate(curve.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(curve,
                Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                        RailShape.NORTH_WEST));
        for (int z = 1; z <= 3; z++) {
            BlockPos rail = curve.north(z);
            helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail,
                    Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                            RailShape.NORTH_SOUTH));
        }
    }

    private static void prepareSouthCorridor(GameTestHelper helper, BlockPos origin, int length) {
        for (int z = 0; z <= length; z++) {
            BlockPos rail = origin.south(z);
            helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail.above(), Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail.east(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail.west(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail.east().above(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail.west().above(), Blocks.STONE.defaultBlockState());
        }
        helper.getLevel().setBlockAndUpdate(origin,
                Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                        RailShape.NORTH_SOUTH));
        helper.getLevel().setBlockAndUpdate(origin.south(length), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin.south(length).above(), Blocks.STONE.defaultBlockState());
    }

    private record Vec3Holder(double x, double y, double z) {}
}

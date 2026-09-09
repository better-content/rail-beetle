package com.bettercontent.railbeetle;

import com.bettercontent.railbeetle.entity.RailBeetleEntity;
import com.bettercontent.railbeetle.entity.BeetleMode;
import com.bettercontent.railbeetle.entity.BeetleSupplies;
import com.bettercontent.railbeetle.navigation.RouteProposal;
import com.bettercontent.railbeetle.navigation.RouteKind;
import com.bettercontent.railbeetle.navigation.RouteObstructions;
import com.bettercontent.railbeetle.navigation.RouteStep;
import com.bettercontent.railbeetle.navigation.TerrainRoutePlanner;
import com.bettercontent.railbeetle.network.BeetleControl;
import com.bettercontent.railbeetle.upgrade.ModuleKind;
import com.simibubi.create.content.contraptions.minecart.capability.CapabilityMinecartController;
import com.simibubi.create.content.contraptions.minecart.CouplingHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.ItemStackHandler;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(RailBeetleMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RailBeetleGameTests {
    private static final String TEMPLATE = "empty";

    private RailBeetleGameTests() {}

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void caveIntegrationDependenciesAreLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create"), "Create must be loaded");
        helper.assertTrue(ModList.get().isLoaded("alexscaves"), "Alex's Caves 2.0.2 must be loaded");
        helper.assertTrue(ModList.get().isLoaded("bettercaves"), "YUNG's Better Caves 2.0.6 must be loaded");
        helper.assertTrue(ModList.get().isLoaded("yungsapi"), "YUNG's API 4.0.6 must be loaded");
        helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(ResourceLocation.fromNamespaceAndPath(RailBeetleMod.MOD_ID, "whistle")),
                "Rail Beetle whistle sound event must be registered");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void suppliesPreserveValuableFuelAndReportShortages(GameTestHelper helper) {
        ItemStackHandler supports = new ItemStackHandler(3);
        supports.setStackInSlot(0, new ItemStack(Blocks.COAL_BLOCK));
        supports.setStackInSlot(1, new ItemStack(Blocks.OAK_PLANKS));
        supports.setStackInSlot(2, new ItemStack(Blocks.COBBLESTONE));
        helper.assertTrue(BeetleSupplies.takeSupport(supports).getBlock() == Blocks.COBBLESTONE,
                "non-fuel supports must be consumed first");
        helper.assertTrue(BeetleSupplies.takeSupport(supports).getBlock() == Blocks.OAK_PLANKS,
                "lower-value fuel supports must precede coal blocks");
        helper.assertTrue(BeetleSupplies.takeSupport(supports).getBlock() == Blocks.COAL_BLOCK,
                "coal blocks must be preserved until no cheaper support remains");

        ItemStackHandler inventory = new ItemStackHandler(3);
        inventory.setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL));
        inventory.setStackInSlot(1, new ItemStack(Blocks.RAIL, 2));
        inventory.setStackInSlot(2, new ItemStack(Blocks.COBBLESTONE));
        RouteProposal route = new RouteProposal(0, 1L, BlockPos.ZERO, Direction.EAST,
                new BlockPos(3, 0, 0), List.of(
                new RouteStep(new BlockPos(1, 0, 0), RailShape.EAST_WEST, List.of()),
                new RouteStep(new BlockPos(2, 0, 0), RailShape.EAST_WEST, new BlockPos(2, -1, 0)),
                new RouteStep(new BlockPos(3, 0, 0), RailShape.EAST_WEST, List.of())));
        var status = BeetleSupplies.supplyStatus(inventory, 0, route);
        helper.assertTrue(status.missingRails() == 1 && status.missingSupports() == 0 && !status.missingFuel(),
                "route warning must report exact simulated shortages");
        helper.assertTrue(inventory.getStackInSlot(0).getCount() == 1,
                "shortage simulation must not mutate the Beetle inventory");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
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

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
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

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void plannerAddsShortestRouteBesideBeacon(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareSouthCorridor(helper, origin, 7);
        BlockPos beacon = origin.south(7);
        helper.getLevel().setBlockAndUpdate(beacon, RailBeetleRegistries.ROUTE_BEACON.get().defaultBlockState());

        TerrainRoutePlanner.Session session = TerrainRoutePlanner.begin(
                helper.getLevel(), origin, Direction.SOUTH, 8, 44L);
        while (!session.advance(4_096)) {
            // Complete the bounded mesh/search synchronously.
        }
        RouteProposal route = session.proposals().stream()
                .filter(proposal -> proposal.kind() == RouteKind.BEACON)
                .findFirst().orElse(null);
        helper.assertTrue(route != null, "a reachable Route Beacon must add a targeted proposal");
        helper.assertTrue(route.beaconTarget().equals(beacon), "beacon proposal must retain its target");
        helper.assertTrue(route.endpoint().equals(beacon.north()),
                "beacon proposal must end on the shortest legal adjacent rail position");
        helper.assertTrue(TerrainRoutePlanner.isRouteStillValid(helper.getLevel(), route),
                "beacon proposal must pass ordinary route validation while its target exists");
        helper.getLevel().removeBlock(beacon, false);
        helper.assertTrue(!TerrainRoutePlanner.isRouteStillValid(helper.getLevel(), route),
                "removing a beacon before selection must invalidate its proposal");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 200)
    public static void plannerReturnsSevenBoundedDiverseCaveFloorRoutes(GameTestHelper helper) {
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
        helper.assertTrue(routes.size() == 7, "open cave floor must produce seven quality proposals");
        helper.assertTrue(routes.stream().allMatch(route -> route.railCount() <= 8), "every route must honor its rail cap");
        helper.assertTrue(routes.stream().map(RouteProposal::endpoint).distinct().count() == 7,
                "proposal endpoints must be distinct");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 300)
    public static void plannerReachesSixtyFourRailCapDeterministically(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareLongSnakeCorridor(helper, origin);

        TerrainRoutePlanner.Session first = TerrainRoutePlanner.begin(
                helper.getLevel(), origin, Direction.SOUTH, 64, 501L);
        while (!first.advance(4_096)) {
            Thread.onSpinWait();
        }
        TerrainRoutePlanner.Session second = TerrainRoutePlanner.beginSingleThreadedReference(
                helper.getLevel(), origin, Direction.SOUTH, 64, 502L);
        while (!second.advance(4_096)) {
            Thread.onSpinWait();
        }
        TerrainRoutePlanner.Session repeated = TerrainRoutePlanner.begin(
                helper.getLevel(), origin, Direction.SOUTH, 64, 503L);
        while (!repeated.advance(4_096)) {
            Thread.onSpinWait();
        }

        List<RouteProposal> firstRoutes = first.proposals();
        List<RouteProposal> secondRoutes = second.proposals();
        List<RouteProposal> repeatedRoutes = repeated.proposals();
        helper.assertTrue(!firstRoutes.isEmpty(), "64-rail corridor must produce a route");
        helper.assertTrue(firstRoutes.get(0).railCount() == 64,
                "planner must reach the configured cap instead of stopping near the old 32k ceiling");
        helper.assertTrue(firstRoutes.stream().map(RouteProposal::steps).toList()
                        .equals(secondRoutes.stream().map(RouteProposal::steps).toList()),
                "parallel planning must exactly match the single-thread reference BFS");
        helper.assertTrue(firstRoutes.stream().map(RouteProposal::steps).toList()
                        .equals(repeatedRoutes.stream().map(RouteProposal::steps).toList()),
                "parallel planning must be deterministic across identical searches");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void routeRevalidationRejectsSingleColumnSlopeValley(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 3, 4));
        BlockPos lowerGap = origin.south().below();
        BlockPos landing = origin.south(2);
        helper.getLevel().setBlockAndUpdate(origin.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(origin,
                Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                        RailShape.NORTH_SOUTH));
        helper.getLevel().setBlockAndUpdate(lowerGap.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(lowerGap, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(lowerGap.above(), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(landing.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(landing, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(landing.above(), Blocks.AIR.defaultBlockState());

        List<RouteStep> valley = List.of(
                new RouteStep(lowerGap, TerrainRoutePlanner.shapeFor(origin, lowerGap, landing), List.of()),
                new RouteStep(landing, TerrainRoutePlanner.shapeFor(lowerGap, landing, null), List.of()));
        RouteProposal proposal = new RouteProposal(0, 1L, origin, Direction.SOUTH, landing, valley);

        helper.assertTrue(!TerrainRoutePlanner.isRouteStillValid(helper.getLevel(), proposal),
                "route revalidation must reject a down/up slope through a paveable one-column gap");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void beetleIsCreateCouplingCapableAndBrakesWhilePlanning(GameTestHelper helper) {
        BlockPos rail = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(rail, Blocks.RAIL.defaultBlockState());
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(rail.getX() + 0.5, rail.getY() + 0.0625, rail.getZ() + 0.5);
        beetle.setDeltaMovement(0.1, 0, 0);
        helper.getLevel().addFreshEntity(beetle);
        Vec3Holder start = new Vec3Holder(beetle.getX(), beetle.getY(), beetle.getZ());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(beetle.getCapability(CapabilityMinecartController.MINECART_CONTROLLER_CAPABILITY).isPresent(),
                    "Create must attach its standard minecart coupling capability to Rail Beetle");
            double moved = beetle.position().distanceToSqr(start.x, start.y, start.z);
            helper.assertTrue(moved < 0.01, "automatic planning brake must prevent rolling");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void beetleRejectsRidersAndUsesPoweredCollisionPriority(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int x = 0; x <= 7; x++) placeEastWestRail(helper, start.offset(x, 0, 0));
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(start.getX() + 0.5, start.getY() + 0.0625, start.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        beetle.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        var pig = EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(pig != null, "test pig must create");
        pig.setPos(beetle.getX() + 0.1, beetle.getY(), beetle.getZ());
        double pigStartX = pig.getX();
        double pigStartZ = pig.getZ();
        float pigHealth = pig.getHealth();
        helper.getLevel().addFreshEntity(beetle);
        helper.getLevel().addFreshEntity(pig);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(beetle.getX(), beetle.getY(), beetle.getZ() + 1.0);
        net.minecraft.world.entity.player.Player blocker = helper.makeMockPlayer();
        blocker.setPos(beetle.getX() + 0.6, beetle.getY(), beetle.getZ());
        float blockerHealth = blocker.getHealth();
        List<Double> samples = new ArrayList<>();

        helper.assertTrue(!beetle.canBeRidden(), "Beetle must not accept automatic mob passengers");
        helper.assertTrue(!beetle.isPushable(), "Beetle must reject ordinary entity shove impulses");
        helper.assertTrue(beetle.isPoweredCart(), "Beetle must receive furnace-cart collision priority");
        helper.assertTrue(ForgeRegistries.BLOCKS.getKey(beetle.getDefaultDisplayBlockState().getBlock())
                        .equals(ResourceLocation.fromNamespaceAndPath("create", "brass_casing")),
                "Create installations must render a brass casing in the Beetle");
        beetle.control(player, BeetleControl.HALF_SPEED);
        blocker.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        beetle.push(blocker);
        helper.assertTrue(blocker.getDeltaMovement().horizontalDistanceSqr() > 0.1,
                "powered Beetle must apply a strong clearing impulse to players");
        helper.onEachTick(() -> {
            if (helper.getTick() >= 2 && helper.getTick() <= 12) {
                blocker.setPos(beetle.getX() + 0.28, beetle.getY(), beetle.getZ());
                blocker.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                samples.add(beetle.getX());
            }
        });

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!beetle.hasPassenger(pig) && pig.getVehicle() != beetle,
                    "nearby mobs must never be collected as Beetle passengers");
            helper.assertTrue(beetle.noseHeading() == Direction.EAST,
                    "mob contact must not reverse the Beetle's commanded heading");
            helper.assertTrue(Math.hypot(pig.getX() - pigStartX, pig.getZ() - pigStartZ) > 0.3,
                    "powered Beetle must shove mobs out of its path");
            helper.assertTrue(pig.getHealth() == pigHealth && blocker.getHealth() == blockerHealth,
                    "Beetle shoves must never damage mobs or players");
            for (int index = 1; index < samples.size(); index++) {
                helper.assertTrue(samples.get(index) - samples.get(index - 1) > 0.07,
                        "living-entity collisions must not slow the powered Beetle");
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void createCartCanTowNeutralBeetle(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 6));
        for (int x = 0; x <= 12; x++) placeEastWestRail(helper, start.east(x));
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(start.getX() + 3.5, start.getY() + 0.0625, start.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        Minecart towCart = new Minecart(helper.getLevel(),
                start.getX() + 1.5, start.getY() + 0.0625, start.getZ() + 0.5);
        helper.getLevel().addFreshEntity(beetle);
        helper.getLevel().addFreshEntity(towCart);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(beetle.getX(), beetle.getY(), beetle.getZ() + 1.0);
        double initialX = beetle.getX();

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(CouplingHandler.tryToCoupleCarts(null, helper.getLevel(), beetle.getId(), towCart.getId()),
                    "Create must couple a tow cart to the Beetle");
            beetle.control(player, BeetleControl.TOGGLE_NEUTRAL);
        });
        helper.onEachTick(() -> {
            if (helper.getTick() >= 3 && helper.getTick() <= 35) towCart.setDeltaMovement(0.25, 0, 0);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(beetle.mode() == BeetleMode.NEUTRAL,
                    "towing must not silently leave Neutral");
            helper.assertTrue(beetle.getX() > initialX + 0.5,
                    "a Create-coupled cart must be able to tow the neutral Beetle; pos=" + beetle.position());
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void ordinaryMinecartImpactDoesNotReverseBeetle(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 8));
        for (int x = 0; x <= 12; x++) placeEastWestRail(helper, start.offset(x, 0, 0));
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(start.getX() + 3.5, start.getY() + 0.0625, start.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        beetle.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        Minecart incoming = new Minecart(helper.getLevel(),
                start.getX() + 7.5, start.getY() + 0.0625, start.getZ() + 0.5);
        incoming.setDeltaMovement(-0.2, 0, 0);
        helper.getLevel().addFreshEntity(beetle);
        helper.getLevel().addFreshEntity(incoming);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(beetle.getX(), beetle.getY(), beetle.getZ() + 1.0);
        double initialX = beetle.getX();
        List<Double> samples = new ArrayList<>();
        beetle.control(player, BeetleControl.NORMAL_SPEED);
        helper.onEachTick(() -> {
            if (helper.getTick() <= 70) samples.add(beetle.getX());
            helper.assertTrue(beetle.noseHeading() == Direction.EAST,
                    "ordinary minecart impact must not reverse Forward");
        });

        helper.runAfterDelay(75, () -> {
            helper.assertTrue(beetle.getX() > initialX + 2.0,
                    "powered collision priority must let the Beetle continue east; pos=" + beetle.position());
            for (int index = 1; index < samples.size(); index++) {
                helper.assertTrue(samples.get(index) - samples.get(index - 1) > -0.08,
                        "ordinary minecart impact must not produce a reversing impulse");
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void repeatedForwardControlPreservesLampDirection(GameTestHelper helper) {
        BlockPos rail = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int x = -1; x <= 1; x++) {
            helper.getLevel().setBlockAndUpdate(rail.offset(x, -1, 0), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail.offset(x, 0, 0),
                    Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                            RailShape.EAST_WEST));
        }
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(rail.getX() + 0.5, rail.getY() + 0.0625, rail.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        beetle.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(beetle);

        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(beetle.getX(), beetle.getY(), beetle.getZ() + 1.0);
        beetle.control(player, BeetleControl.NORMAL_SPEED);
        beetle.control(player, BeetleControl.NORMAL_SPEED);
        helper.assertTrue(beetle.mode() == BeetleMode.MANUAL_FORWARD,
                "repeated Forward controls must remain in forward mode");
        helper.assertTrue(beetle.noseHeading() == Direction.EAST,
                "repeated Forward controls must not reverse the lamp-defined nose");
        helper.assertTrue(Math.abs(beetle.speedTier().blocksPerTick() - 0.2) < 1.0e-9,
                "new 1x speed must be 4 blocks/second, twice the old base speed");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void beetlePlacedMidRailDoesNotPlan(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(6, 2, 6));
        for (int z = -2; z <= 2; z++) {
            BlockPos rail = center.offset(0, 0, z);
            helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail,
                    Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                            RailShape.NORTH_SOUTH));
        }
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(center.getX() + 0.5, center.getY() + 0.0625, center.getZ() + 0.5);
        beetle.setInitialHeading(Direction.NORTH);
        helper.getLevel().addFreshEntity(beetle);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(beetle.mode() == BeetleMode.STOPPED,
                    "a Beetle placed in the middle of track must remain stopped, not plan");
            helper.assertTrue(beetle.proposals().isEmpty(),
                    "a Beetle placed in the middle of track must not publish routes");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void coupledPassengerCartMovesWithoutBeetleSnaps(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 12));
        for (int x = 0; x <= 15; x++) {
            BlockPos rail = start.offset(x, 0, 0);
            helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(rail,
                    Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                            RailShape.EAST_WEST));
        }
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(start.getX() + 2.5, start.getY() + 0.0625, start.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        beetle.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        Minecart passengerCart = new Minecart(helper.getLevel(),
                start.getX() + 0.5, start.getY() + 0.0625, start.getZ() + 0.5);
        helper.getLevel().addFreshEntity(beetle);
        helper.getLevel().addFreshEntity(passengerCart);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(passengerCart.getX(), passengerCart.getY(), passengerCart.getZ());
        player.startRiding(passengerCart, true);
        List<Double> samples = new ArrayList<>();

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(CouplingHandler.tryToCoupleCarts(null, helper.getLevel(), beetle.getId(), passengerCart.getId()),
                    "Create must couple the Rail Beetle to the passenger cart");
            beetle.control(player, BeetleControl.NORMAL_SPEED);
        });
        helper.onEachTick(() -> {
            if (helper.getTick() >= 3 && helper.getTick() <= 70) samples.add(beetle.getX());
        });
        helper.runAfterDelay(75, () -> {
            var controller = beetle.getCapability(CapabilityMinecartController.MINECART_CONTROLLER_CAPABILITY).orElse(null);
            helper.assertTrue(controller != null
                            && (controller.isLeadingCoupling() || controller.isConnectedToCoupling()),
                    "normal Beetle traction must preserve the Create coupling");
            helper.assertTrue(player.getVehicle() == passengerCart,
                    "the test passenger must remain mounted in the coupled cart");
            helper.assertTrue(samples.size() > 20 && samples.get(samples.size() - 1) > samples.get(0) + 1.0,
                    "the coupled Beetle must make forward progress");
            for (int index = 1; index < samples.size(); index++) {
                double delta = samples.get(index) - samples.get(index - 1);
                helper.assertTrue(delta > -0.08 && delta < 0.65,
                        "coupled movement must not contain backward impulses or position snaps; delta=" + delta);
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void forwardRejectsRollbackAndClimbsWithoutFlippingLamp(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(4, 2, 6));
        placeEastHill(helper, foot, 7);
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        BlockPos topStart = foot.offset(2, 1, 0);
        beetle.setPos(topStart.getX() + 0.5, topStart.getY() + 0.0625, topStart.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        beetle.setDeltaMovement(-0.18, 0, 0);
        beetle.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(beetle);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(beetle.getX(), beetle.getY(), beetle.getZ() + 1.0);
        AtomicBoolean crossedBackward = new AtomicBoolean();
        double startX = beetle.getX();
        beetle.control(player, BeetleControl.HALF_SPEED);

        helper.onEachTick(() -> {
            if (beetle.getX() < startX - 0.55) crossedBackward.set(true);
            helper.assertTrue(beetle.noseHeading() == Direction.EAST,
                    "rollback must not redefine Forward or flip the amber lamp; pos=" + beetle.position());
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(!crossedBackward.get(), "0.4 acceleration must reject the rollback before a rail boundary");
            helper.assertTrue(beetle.getX() > startX + 1.0,
                    "Forward must recover from rollback and climb east; pos=" + beetle.position());
            helper.assertTrue(beetle.getY() > foot.getY() + 0.7,
                    "recovered Beetle must remain on the upper railway; pos=" + beetle.position());
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void coupledCartsClimbSustainedSlopeWithoutRollback(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(6, 2, 16));
        for (int x = -4; x < 0; x++) placeEastWestRail(helper, foot.offset(x, 0, 0));
        placeSustainedEastHill(helper, foot, 3, 9);
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(foot.getX() + 0.5, foot.getY() + 0.0625, foot.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        beetle.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        installModule(beetle, 0, ModuleKind.HIGH_SPEED_GOVERNOR_I);
        installModule(beetle, 1, ModuleKind.ADHESION_SANDERS_II);
        Minecart passengerCart = new Minecart(helper.getLevel(),
                foot.getX() - 1.5, foot.getY() + 0.0625, foot.getZ() + 0.5);
        Minecart cargoCart = new Minecart(helper.getLevel(),
                foot.getX() - 3.5, foot.getY() + 0.0625, foot.getZ() + 0.5);
        helper.getLevel().addFreshEntity(beetle);
        helper.getLevel().addFreshEntity(passengerCart);
        helper.getLevel().addFreshEntity(cargoCart);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(passengerCart.getX(), passengerCart.getY(), passengerCart.getZ());
        player.startRiding(passengerCart, true);
        List<Double> samples = new ArrayList<>();

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(CouplingHandler.tryToCoupleCarts(null, helper.getLevel(), beetle.getId(), passengerCart.getId()),
                    "Create must couple the Rail Beetle to the passenger cart");
            helper.assertTrue(CouplingHandler.tryToCoupleCarts(null, helper.getLevel(), passengerCart.getId(), cargoCart.getId()),
                    "Create must couple the trailing cargo cart");
            beetle.control(player, BeetleControl.DOUBLE_SPEED);
        });
        helper.onEachTick(() -> {
            if (helper.getTick() >= 3 && helper.getTick() <= 100) samples.add(beetle.getX());
            helper.assertTrue(beetle.noseHeading() == Direction.EAST,
                    "coupled hill travel must keep the commanded forward heading");
        });
        helper.runAfterDelay(105, () -> {
            var controller = beetle.getCapability(CapabilityMinecartController.MINECART_CONTROLLER_CAPABILITY).orElse(null);
            helper.assertTrue(controller != null
                            && (controller.isLeadingCoupling() || controller.isConnectedToCoupling()),
                    "hill traction must preserve the Create coupling");
            helper.assertTrue(player.getVehicle() == passengerCart,
                    "the passenger must remain mounted throughout the climb");
            helper.assertTrue(beetle.getX() > foot.getX() + 5.0 && beetle.getY() > foot.getY() + 2.7,
                    "the coupled Beetle must climb the sustained grade; pos=" + beetle.position());
            for (int index = 1; index < samples.size(); index++) {
                double delta = samples.get(index) - samples.get(index - 1);
                helper.assertTrue(delta > -0.005 && delta < 0.65,
                        "coupled hill movement must not roll backward or snap; delta=" + delta);
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void doubleSpeedClimbsSlopeWithoutChangingForward(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(4, 2, 10));
        placeEastHill(helper, foot, 12);
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(foot.getX() + 0.5, foot.getY() + 0.0625, foot.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        beetle.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        installModule(beetle, 0, ModuleKind.HIGH_SPEED_GOVERNOR_I);
        helper.getLevel().addFreshEntity(beetle);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(beetle.getX(), beetle.getY(), beetle.getZ() + 1.0);
        beetle.control(player, BeetleControl.DOUBLE_SPEED);

        helper.runAfterDelay(35, () -> {
            helper.assertTrue(beetle.getX() > foot.getX() + 2.0 && beetle.getY() > foot.getY() + 0.7,
                    "2x Forward must climb onto the upper railway; pos=" + beetle.position());
            helper.assertTrue(beetle.noseHeading() == Direction.EAST,
                    "2x hill travel must preserve the lamp-defined Forward direction");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void forwardAndReverseKeepSemanticHeadingThroughCorners(GameTestHelper helper) {
        BlockPos forwardCurve = helper.absolutePos(new BlockPos(8, 2, 8));
        placeNorthWestCorner(helper, forwardCurve);
        RailBeetleEntity forward = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(forward != null, "forward Beetle entity type must create");
        forward.setPos(forwardCurve.getX() - 1.5, forwardCurve.getY() + 0.0625, forwardCurve.getZ() + 0.5);
        forward.setInitialHeading(Direction.EAST);
        forward.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(forward);
        net.minecraft.world.entity.player.Player forwardPlayer = helper.makeMockPlayer();
        forwardPlayer.setPos(forward.getX(), forward.getY(), forward.getZ() + 1.0);
        forward.control(forwardPlayer, BeetleControl.NORMAL_SPEED);

        BlockPos reverseCurve = helper.absolutePos(new BlockPos(18, 2, 18));
        placeNorthWestCorner(helper, reverseCurve);
        RailBeetleEntity reverse = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(reverse != null, "reverse Beetle entity type must create");
        reverse.setPos(reverseCurve.getX() + 0.5, reverseCurve.getY() + 0.0625, reverseCurve.getZ() - 1.5);
        reverse.setInitialHeading(Direction.NORTH);
        reverse.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(reverse);
        net.minecraft.world.entity.player.Player reversePlayer = helper.makeMockPlayer();
        reversePlayer.setPos(reverse.getX(), reverse.getY(), reverse.getZ() - 1.0);
        reverse.control(reversePlayer, BeetleControl.REVERSE);
        AtomicBoolean forwardTurned = new AtomicBoolean();
        AtomicBoolean reverseTurned = new AtomicBoolean();

        helper.onEachTick(() -> {
            if (forward.noseHeading() == Direction.NORTH && forwardTurned.compareAndSet(false, true)) {
                forward.control(forwardPlayer, BeetleControl.STOP);
            }
            if (reverse.noseHeading() == Direction.EAST && reverseTurned.compareAndSet(false, true)) {
                reverse.control(reversePlayer, BeetleControl.STOP);
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

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 400)
    public static void automaticRouteRewindsAndRecoversAfterRollback(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareSouthCorridor(helper, origin, 9);
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(origin.getX() + 0.5, origin.getY() + 0.0625, origin.getZ() + 0.5);
        beetle.setInitialHeading(Direction.SOUTH);
        beetle.inventory().setStackInSlot(0, new ItemStack(Blocks.RAIL, 24));
        beetle.inventory().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.COAL, 4));
        helper.getLevel().addFreshEntity(beetle);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        AtomicInteger progressBeforeRollback = new AtomicInteger();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!beetle.proposals().isEmpty(),
                        "Beetle must plan the straight rollback test corridor; mode=" + beetle.mode()
                                + ", fuel=" + beetle.fuelTicks() + ", pos=" + beetle.position()))
                .thenExecute(() -> {
                    RouteProposal route = beetle.proposals().get(0);
                    BlockPos first = route.steps().get(0).railPos();
                    player.setPos(beetle.getX(), beetle.getY() + 1.0, beetle.getZ() - 2.0);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES,
                            new net.minecraft.world.phys.Vec3(first.getX() + 0.5, first.getY() + 0.2, first.getZ() + 0.5));
                    beetle.selectRoute(player, route.generation(), route.id());
                    helper.assertTrue(beetle.mode() == BeetleMode.DEPARTING,
                            "selected rollback route must enter its departure phase");
                })
                .thenWaitUntil(() -> helper.assertTrue(beetle.activeStep() >= 3,
                        "Beetle must advance far enough to exercise route rollback; progress=" + beetle.activeStep()))
                .thenExecute(() -> {
                    progressBeforeRollback.set(beetle.activeStep());
                    RouteProposal route = beetle.activeRoute();
                    helper.assertTrue(route != null, "rollback test requires an active route");
                    int rewindStep = Math.max(0, progressBeforeRollback.get() - 2);
                    BlockPos previous = route.steps().get(rewindStep).railPos();
                    beetle.setPos(previous.getX() + 0.5, previous.getY() + 0.0625, previous.getZ() + 0.5);
                    beetle.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                })
                .thenWaitUntil(() -> helper.assertTrue(beetle.activeStep() < progressBeforeRollback.get(),
                        "route cursor must rewind when the Beetle crosses onto an earlier route rail; progress="
                                + beetle.activeStep() + ", before=" + progressBeforeRollback.get()))
                .thenWaitUntil(() -> helper.assertTrue(beetle.activeStep() > progressBeforeRollback.get(),
                        "automatic Forward must recover and resume route progress; progress=" + beetle.activeStep()))
                .thenExecute(() -> helper.assertTrue(beetle.noseHeading() == Direction.SOUTH,
                        "automatic rollback must not flip the route-defined forward heading"))
                .thenSucceed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 300)
    public static void beetleBuildsRailAndShallowSupportThroughCaveCorridor(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int z = 0; z <= 7; z++) {
            BlockPos railPos = origin.offset(0, 0, z);
            BlockPos floor = railPos.below();
            helper.getLevel().setBlockAndUpdate(floor, z == 3 ? Blocks.SNOW.defaultBlockState() : Blocks.STONE.defaultBlockState());
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

        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(origin.getX() + 0.5, origin.getY() + 0.0625, origin.getZ() + 0.5);
        beetle.setInitialHeading(Direction.SOUTH);
        beetle.inventory().setStackInSlot(0, new ItemStack(Blocks.RAIL, 16));
        beetle.inventory().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        beetle.inventory().setStackInSlot(2, new ItemStack(Blocks.COBBLESTONE, 16));
        helper.getLevel().addFreshEntity(beetle);

        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        AtomicReference<RouteProposal> selectedRoute = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!beetle.proposals().isEmpty(),
                        "Beetle must finish planning the corridor; mode=" + beetle.mode()
                                + ", pos=" + beetle.blockPosition()
                                + ", block=" + helper.getLevel().getBlockState(beetle.blockPosition())))
                .thenExecute(() -> {
                    RouteProposal route = beetle.proposals().get(0);
                    selectedRoute.set(route);
                    helper.assertTrue(route.supportCount() == 1, "corridor route must plan exactly one shallow support");
                    for (RouteProposal proposal : beetle.proposals()) {
                        if (proposal.steps().stream().anyMatch(step -> step.railPos().getZ() > origin.getZ() + 3)) {
                            RouteStep bridge = proposal.steps().stream()
                                    .filter(step -> step.railPos().equals(origin.south(3)))
                                    .findFirst().orElseThrow();
                            helper.assertTrue(bridge.supportPos() != null
                                            && bridge.railPos().getY() == origin.getY()
                                            && bridge.shape() == RailShape.NORTH_SOUTH,
                                    "every route crossing a clearable one-column gap must pave it level");
                        }
                    }
                    Vec3Holder target = new Vec3Holder(
                            route.steps().get(0).railPos().getX() + 0.5,
                            route.steps().get(0).railPos().getY() + 0.2,
                            route.steps().get(0).railPos().getZ() + 0.5);
                    player.setPos(beetle.getX(), beetle.getY() + 1.0, beetle.getZ() - 2.0);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES,
                            new net.minecraft.world.phys.Vec3(target.x, target.y, target.z));
                    beetle.selectRoute(player, route.generation(), route.id());
                    helper.assertTrue(beetle.mode() == BeetleMode.DEPARTING,
                            "crosshair-selected route must start its whistle/departure phase");
                })
                .thenWaitUntil(() -> {
                    RouteProposal route = selectedRoute.get();
                    helper.assertTrue(route != null, "a shallow-gap route must be selected before placement checks");
                    var supportedStep = route.steps().stream().filter(step -> step.supportPos() != null).findFirst().orElseThrow();
                    BlockPos supportedRail = supportedStep.railPos();
                    helper.assertTrue(supportedRail.getY() == origin.getY()
                                    && supportedStep.shape() == RailShape.NORTH_SOUTH,
                            "one-column depression must be paved level instead of using slopes");
                    helper.assertTrue(helper.getLevel().getBlockState(supportedRail).getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock,
                            "Beetle must place rail over the shallow gap; mode=" + beetle.mode()
                                    + ", pos=" + beetle.position() + ", progress=" + beetle.activeStep()
                                    + ", expected=" + supportedRail
                                    + ", z1=" + helper.getLevel().getBlockState(origin.offset(0, 0, 1))
                                    + ", z2=" + helper.getLevel().getBlockState(origin.offset(0, 0, 2)));
                    helper.assertTrue(helper.getLevel().getBlockState(supportedStep.supportPos()).is(Blocks.COBBLESTONE),
                            "Beetle must consume and place a support below the rail");
                    helper.assertTrue(beetle.inventory().getStackInSlot(0).getCount() < 16,
                            "Beetle must consume rails from its ordinary multi-slot inventory");
                    helper.assertTrue(beetle.fuelTicks() > 0, "Beetle must load furnace fuel while moving");
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 300)
    public static void beetleBuildsBottomUpTrestleOverDeepSingleColumnGap(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 4, 4));
        prepareSouthCorridor(helper, origin, 7);
        BlockPos gapRail = origin.south(3);
        helper.getLevel().setBlockAndUpdate(gapRail.below(), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(gapRail.below(2), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(gapRail.below(3), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(gapRail.below(4), Blocks.STONE.defaultBlockState());

        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(origin.getX() + 0.5, origin.getY() + 0.0625, origin.getZ() + 0.5);
        beetle.setInitialHeading(Direction.SOUTH);
        beetle.inventory().setStackInSlot(0, new ItemStack(Blocks.RAIL, 16));
        beetle.inventory().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        beetle.inventory().setStackInSlot(2, new ItemStack(Blocks.COBBLESTONE, 16));
        helper.getLevel().addFreshEntity(beetle);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        AtomicReference<RouteProposal> selectedRoute = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!beetle.proposals().isEmpty(),
                        "Beetle must plan through a deep single-column gap"))
                .thenExecute(() -> {
                    RouteProposal route = beetle.proposals().get(0);
                    for (RouteProposal proposal : beetle.proposals()) {
                        if (proposal.steps().stream().anyMatch(step -> step.railPos().getZ() > gapRail.getZ())) {
                            RouteStep bridge = proposal.steps().stream()
                                    .filter(step -> step.railPos().equals(gapRail))
                                    .findFirst().orElseThrow();
                            helper.assertTrue(bridge.supportPos() != null
                                            && bridge.railPos().getY() == origin.getY()
                                            && bridge.shape() == RailShape.NORTH_SOUTH,
                                    "every route crossing a deep one-column gap must pave it level");
                        }
                    }
                    RouteStep supported = route.steps().stream()
                            .filter(step -> step.supportPos() != null)
                            .findFirst().orElseThrow();
                    helper.assertTrue(supported.railPos().equals(gapRail),
                            "deep gap must use its missing column as the supported bridge step");
                    helper.assertTrue(supported.supportPositions().equals(List.of(
                                    gapRail.below(3), gapRail.below(2), gapRail.below())),
                            "deep bridge must describe a bottom-up support column to solid ground");
                    helper.assertTrue(supported.shape() == RailShape.NORTH_SOUTH,
                            "deep single-column bridge must remain level and straight");
                    selectedRoute.set(route);
                    player.setPos(beetle.getX(), beetle.getY() + 1.0, beetle.getZ() - 2.0);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES,
                            new net.minecraft.world.phys.Vec3(route.steps().get(0).railPos().getX() + 0.5,
                                    route.steps().get(0).railPos().getY() + 0.2,
                                    route.steps().get(0).railPos().getZ() + 0.5));
                    beetle.selectRoute(player, route.generation(), route.id());
                })
                .thenWaitUntil(() -> {
                    RouteStep supported = selectedRoute.get().steps().stream()
                            .filter(step -> step.supportPos() != null)
                            .findFirst().orElseThrow();
                    helper.assertTrue(BaseRailBlock.isRail(helper.getLevel().getBlockState(supported.railPos())),
                            "Beetle must place rail over the deep gap");
                    helper.assertTrue(supported.supportPositions().stream().allMatch(
                                    pos -> helper.getLevel().getBlockState(pos).is(Blocks.COBBLESTONE)),
                            "Beetle must build every deep support from solid ground upward");
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void plannerDoesNotChainSupportsAcrossTwoColumnGap(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 4, 4));
        prepareSouthCorridor(helper, origin, 7);
        for (int z = 3; z <= 4; z++) {
            BlockPos gapRail = origin.south(z);
            helper.getLevel().setBlockAndUpdate(gapRail.below(), Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(gapRail.below(2), Blocks.AIR.defaultBlockState());
        }

        TerrainRoutePlanner.Session session = TerrainRoutePlanner.begin(helper.getLevel(), origin, Direction.SOUTH, 7, 99L);
        while (!session.advance(4_096)) {
            // Bounded deterministic search; finish synchronously inside the test.
        }
        helper.assertTrue(!session.proposals().isEmpty(), "planner must retain reachable routes before the gap");
        for (RouteProposal route : session.proposals()) {
            helper.assertTrue(route.steps().stream().noneMatch(step -> step.railPos().getZ() >= origin.getZ() + 3),
                    "planner must not cross a two-column gap with chained floating supports");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 300)
    public static void beetleClearsSoftRouteObstructionsWithDrops(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 3, 4));
        prepareSouthCorridor(helper, origin, 8);
        BlockPos grass = origin.south(1);
        BlockPos snow = origin.south(2);
        BlockPos leaves = origin.south(3);
        BlockPos headLeaves = origin.south(4).above();
        BlockPos flowerRelative = new BlockPos(4, 3, 9);
        BlockPos flower = helper.absolutePos(flowerRelative);
        helper.getLevel().setBlockAndUpdate(grass.below(), Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(grass, Blocks.GRASS.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(snow, Blocks.SNOW.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(leaves, Blocks.OAK_LEAVES.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(headLeaves, Blocks.OAK_LEAVES.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(flower.below(), Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(flower, Blocks.DANDELION.defaultBlockState());

        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(origin.getX() + 0.5, origin.getY() + 0.0625, origin.getZ() + 0.5);
        beetle.setInitialHeading(Direction.SOUTH);
        beetle.inventory().setStackInSlot(0, new ItemStack(Blocks.RAIL, 20));
        beetle.inventory().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(beetle);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!beetle.proposals().isEmpty(),
                        "planner must route through tagged dry soft obstructions"))
                .thenExecute(() -> {
                    RouteProposal route = beetle.proposals().get(0);
                    helper.assertTrue(route.steps().stream().anyMatch(step -> step.railPos().equals(flower)),
                            "soft-obstruction route must continue beyond all test vegetation");
                    BlockPos first = route.steps().get(0).railPos();
                    player.setPos(beetle.getX(), beetle.getY() + 1.0, beetle.getZ() - 2.0);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES,
                            new net.minecraft.world.phys.Vec3(first.getX() + 0.5, first.getY() + 0.2, first.getZ() + 0.5));
                    beetle.selectRoute(player, route.generation(), route.id());
                    helper.assertTrue(beetle.mode() == BeetleMode.DEPARTING,
                            "valid soft-obstruction route must begin departure");
                })
                .thenWaitUntil(() -> {
                    for (BlockPos rail : List.of(grass, snow, leaves, flower)) {
                        helper.assertTrue(BaseRailBlock.isRail(helper.getLevel().getBlockState(rail)),
                                "Beetle must clear the obstruction and place rail at " + rail);
                    }
                    helper.assertTrue(helper.getLevel().getBlockState(headLeaves).isAir(),
                            "Beetle must clear tagged vegetation from head space");
                    helper.assertItemEntityPresent(net.minecraft.world.item.Items.DANDELION, flowerRelative, 3.0);
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 160)
    public static void staleSoftRouteDoesNotBreakProtectedReplacement(GameTestHelper helper) {
        helper.assertTrue(!RouteObstructions.isClearable(Blocks.WHEAT.defaultBlockState()),
                "crops must remain protected");
        helper.assertTrue(!RouteObstructions.isClearable(Blocks.OAK_LOG.defaultBlockState()),
                "logs must remain protected");
        helper.assertTrue(!RouteObstructions.isClearable(Blocks.STONE.defaultBlockState()),
                "solid terrain must remain protected");
        helper.assertTrue(!RouteObstructions.isClearable(Blocks.CHEST.defaultBlockState()),
                "block entities must remain protected");
        helper.assertTrue(!RouteObstructions.isClearable(Blocks.WATER.defaultBlockState()),
                "fluid blocks must remain protected");

        BlockPos origin = helper.absolutePos(new BlockPos(4, 3, 4));
        prepareSouthCorridor(helper, origin, 7);
        BlockPos obstruction = origin.south(3);
        helper.getLevel().setBlockAndUpdate(obstruction.below(), Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.GRASS.defaultBlockState());
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(origin.getX() + 0.5, origin.getY() + 0.0625, origin.getZ() + 0.5);
        beetle.setInitialHeading(Direction.SOUTH);
        beetle.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        helper.getLevel().addFreshEntity(beetle);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!beetle.proposals().isEmpty(),
                        "Beetle must initially plan through grass; mode=" + beetle.mode()
                                + ", fuel=" + beetle.fuelTicks()
                                + ", coal=" + beetle.inventory().getStackInSlot(0)
                                + ", pos=" + beetle.position()))
                .thenExecute(() -> {
                    RouteProposal route = beetle.proposals().get(0);
                    helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
                    BlockPos first = route.steps().get(0).railPos();
                    player.setPos(beetle.getX(), beetle.getY() + 1.0, beetle.getZ() - 2.0);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES,
                            new net.minecraft.world.phys.Vec3(first.getX() + 0.5, first.getY() + 0.2, first.getZ() + 0.5));
                    beetle.selectRoute(player, route.generation(), route.id());
                    helper.assertTrue(beetle.mode() == BeetleMode.READY,
                            "route must be rejected when soft vegetation becomes a hard block");
                    helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.STONE),
                            "stale-route rejection must not damage the protected replacement");
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void neutralRestoresPushPhysicsAndInterlocksWithHandbrake(GameTestHelper helper) {
        BlockPos rail = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int x = -2; x <= 2; x++) placeEastWestRail(helper, rail.offset(x, 0, 0));
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(rail.getX() + 0.5, rail.getY() + 0.0625, rail.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        helper.getLevel().addFreshEntity(beetle);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(beetle.getX(), beetle.getY(), beetle.getZ() + 1.0);

        beetle.control(player, BeetleControl.TOGGLE_NEUTRAL);
        helper.assertTrue(beetle.mode() == BeetleMode.NEUTRAL, "Neutral control must enter Neutral");
        helper.assertTrue(beetle.isPushable() && !beetle.isPoweredCart(),
                "Neutral Beetle must use ordinary pushable, non-powered cart physics");
        helper.assertTrue(!beetle.brakeApplied() && !beetle.forcedBrake(),
                "Neutral must release both automatic and forced handbrakes");
        beetle.push(0.2, 0, 0);
        helper.assertTrue(beetle.getDeltaMovement().x > 0.15, "Neutral must accept an external push impulse");

        CompoundTag saved = new CompoundTag();
        beetle.saveWithoutId(saved);
        RailBeetleEntity loaded = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(loaded != null, "loaded Beetle entity type must create");
        loaded.load(saved);
        helper.assertTrue(loaded.mode() == BeetleMode.NEUTRAL,
                "Neutral switch state must survive an entity save/load cycle");

        beetle.control(player, BeetleControl.TOGGLE_HAND_BRAKE);
        helper.assertTrue(beetle.mode() != BeetleMode.NEUTRAL && beetle.forcedBrake() && beetle.brakeApplied(),
                "locking the handbrake must exit Neutral and brake immediately");
        helper.assertTrue(!beetle.isPushable() && beetle.isPoweredCart(),
                "a braked Beetle must return to authoritative powered-cart physics");
        helper.succeed();
    }

    @GameTest(templateNamespace = RailBeetleMod.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void forwardTerminalCannotOvershootAndAutomaticallyPlans(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 8));
        for (int x = 0; x <= 3; x++) placeEastWestRail(helper, start.east(x));
        BlockPos endpoint = start.east(3);
        for (int x = 4; x <= 12; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos candidate = start.offset(x, 0, z);
                helper.getLevel().setBlockAndUpdate(candidate.below(), Blocks.STONE.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(candidate, Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(candidate.above(), Blocks.AIR.defaultBlockState());
            }
        }
        RailBeetleEntity beetle = RailBeetleRegistries.RAIL_BEETLE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(beetle != null, "Beetle entity type must create");
        beetle.setPos(start.getX() + 0.5, start.getY() + 0.0625, start.getZ() + 0.5);
        beetle.setInitialHeading(Direction.EAST);
        beetle.inventory().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL, 1));
        installModule(beetle, 0, ModuleKind.HIGH_SPEED_GOVERNOR_I);
        helper.getLevel().addFreshEntity(beetle);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setPos(beetle.getX(), beetle.getY(), beetle.getZ() + 1.0);
        beetle.control(player, BeetleControl.DOUBLE_SPEED);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(beetle.mode() == BeetleMode.PLANNING || beetle.mode() == BeetleMode.READY,
                        "forward terminal arrival must automatically enter route planning"))
                .thenExecute(() -> {
                    double endpointX = endpoint.getX() + 0.5;
                    helper.assertTrue(Math.abs(beetle.getX() - endpointX) < 0.01,
                            "terminal guard must clamp the Beetle to the final rail center; pos=" + beetle.position());
                    helper.assertTrue(beetle.brakeApplied(), "planning at the terminal must apply the handbrake immediately");
                    for (int count = 0; count < 8; count++) beetle.push(0.8, 0, 0);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(Math.abs(beetle.getX() - (endpoint.getX() + 0.5)) < 0.01,
                        "external pushes must never move a non-Neutral Beetle off its terminal rail"))
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

    private static void placeSustainedEastHill(GameTestHelper helper, BlockPos foot, int rises, int length) {
        for (int x = 0; x < rises; x++) {
            BlockPos slope = foot.offset(x, x, 0);
            helper.getLevel().setBlockAndUpdate(slope.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(slope,
                    Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                            RailShape.ASCENDING_EAST));
        }
        for (int x = rises; x <= length; x++) placeEastWestRail(helper, foot.offset(x, rises, 0));
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

    private static void installModule(RailBeetleEntity beetle, int slot, ModuleKind kind) {
        beetle.moduleInventory().setStackInSlot(slot,
                new ItemStack(RailBeetleRegistries.MODULE_ITEMS.get(kind).get()));
    }

    private static void prepareLongSnakeCorridor(GameTestHelper helper, BlockPos origin) {
        Set<BlockPos> path = new HashSet<>();
        BlockPos cursor = origin;
        path.add(cursor);
        Direction[] directions = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.EAST, Direction.SOUTH};
        int[] lengths = {10, 3, 10, 3, 10, 3, 10, 3, 12};
        for (int segment = 0; segment < directions.length; segment++) {
            for (int step = 0; step < lengths[segment]; step++) {
                cursor = cursor.relative(directions[segment]);
                path.add(cursor);
            }
        }
        for (int x = -1; x <= 13; x++) {
            for (int z = -1; z <= 13; z++) {
                BlockPos rail = origin.offset(x, 0, z);
                helper.getLevel().setBlockAndUpdate(rail.below(), Blocks.STONE.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(rail,
                        path.contains(rail) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(rail.above(),
                        path.contains(rail) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
            }
        }
        helper.getLevel().setBlockAndUpdate(origin,
                Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                        RailShape.NORTH_SOUTH));
    }

    private record Vec3Holder(double x, double y, double z) {}
}

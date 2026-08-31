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

    private record Vec3Holder(double x, double y, double z) {}
}

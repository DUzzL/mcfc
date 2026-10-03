package dev.epicpix.minecraftfunctioncompiler.mod.test;

import dev.epicpix.minecraftfunctioncompiler.mod.FunctionCompilerMain;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class AdvancementRegressionTests {
    private static final Identifier ID = Identifier.parse("mcfc-test:advancement_regressions");
    private static final Identifier FUNCTION = Identifier.parse("mcfc-test:leashed_boat");
    private static final Identifier ADVANCEMENT = Identifier.parse("blazeandcave:nether/lead_zeppelin");

    private AdvancementRegressionTests() {}

    public static void registerFunction() {
        Registry.register(BuiltInRegistries.TEST_FUNCTION, ID, AdvancementRegressionTests::awaitCompiler);
    }

    public static void registerInstance(Registry<GameTestInstance> instances, Registry<TestEnvironmentDefinition<?>> environments) {
        Registry.register(environments, ID, new TestEnvironmentDefinition.AllOf());
        var environment = environments.getOrThrow(ResourceKey.create(Registries.TEST_ENVIRONMENT, ID));
        Registry.register(instances, ID, new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, ID),
                new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 1200, 1, true)));
    }

    private static void awaitCompiler(GameTestHelper helper) {
        var loader = FunctionCompilerMain.functionClassLoader;
        if (loader != null && loader.getState() == dev.epicpix.minecraftfunctioncompiler.loader.FunctionCompilerClassLoader.State.READY_OFF) {
            loader.tryOn();
        }
        if (loader == null || !loader.isUseReady()) {
            helper.runAfterDelay(1, () -> awaitCompiler(helper));
            return;
        }
        helper.assertTrue(loader.hasFunctionLoaded(FUNCTION), "Boat polling function must exercise compiled code");
        runLeadZeppelin(helper);
    }

    private static void runLeadZeppelin(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var loader = FunctionCompilerMain.functionClassLoader;
        var player = helper.makeMockServerPlayerInLevel();
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        var position = helper.absoluteVec(new Vec3(1, 2, 1));
        player.setPos(position);
        player.setShiftKeyDown(true);
        player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        // BACAP 26.3 checks `type_specific/player.input.sneak`, i.e. the last client input.
        player.setLastClientInput(new net.minecraft.world.entity.player.Input(false, false, false, false, false, true, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        var ghast = helper.spawnWithNoFreeWill(EntityTypes.HAPPY_GHAST, new Vec3(2, 2, 1));
        var boat = helper.spawn(EntityTypes.OAK_BOAT, new Vec3(1, 2, 2));
        var leashable = (Leashable) boat;
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.addObjective("bac_leashing_boat", ObjectiveCriteria.DUMMY,
                Component.literal("Boat leash"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        var advancement = server.getAdvancements().get(ADVANCEMENT);
        helper.assertTrue(advancement != null, "Lead Zeppelin criterion fixture must load");
        var function = server.getFunctions().get(FUNCTION).orElseThrow();
        try {
            for (boolean compiled : new boolean[]{false, true}) {
                if (compiled) loader.tryOn(); else loader.tryOff();
                for (var criterion : advancement.value().criteria().keySet()) {
                    player.getAdvancements().revoke(advancement, criterion);
                }
                scoreboard.getOrCreatePlayerScore(player, objective).set(0);
                leashable.setLeashedTo(player, false);
                server.getFunctions().execute(function, server.createCommandSourceStack());
                var score = scoreboard.getPlayerScoreInfo(player, objective);
                int value = score == null ? -1 : score.value();
                player.connection.handleInteract(new ServerboundInteractPacket(
                        ghast.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, true));
                helper.assertTrue(leashable.getLeashHolder() == ghast, "Interaction must transfer the boat leash to the happy ghast");
                boolean done = player.getAdvancements().getOrStartProgress(advancement).isDone();
                helper.assertTrue(done, (compiled ? "Compiled" : "Vanilla")
                        + " Lead Zeppelin must award after leashing a boat to a happy ghast; boat score=" + value);
            }
        } finally {
            loader.tryOn();
            scoreboard.removeObjective(objective);
            boat.discard();
            ghast.discard();
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }
}

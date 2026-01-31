package dev.epicpix.minecraftfunctioncompiler.mod.test;

import net.minecraft.gametest.framework.GameTestHelper;

public class McfcTestHelper {

    public static void setupRun(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds());
    }

}

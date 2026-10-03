package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import dev.epicpix.minecraftfunctioncompiler.mod.ScoreRef;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScores;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Invalidates the compiled-code score caches ({@link ScoreRef}) when scores or objectives leave the scoreboard. */
@Mixin(Scoreboard.class)
public class ScoreboardMixin {
    @Shadow @Final private Map<String, PlayerScores> playerScores;

    @Inject(method = "resetAllPlayerScores", at = @At("HEAD"))
    private void mcfc$invalidateOnResetAll(ScoreHolder player, CallbackInfo ci) {
        if(playerScores.containsKey(player.getScoreboardName())) ScoreRef.invalidateAll();
    }

    @Inject(method = "removeObjective", at = @At("HEAD"))
    private void mcfc$invalidateOnRemoveObjective(Objective objective, CallbackInfo ci) {
        ScoreRef.invalidateAll();
    }
}

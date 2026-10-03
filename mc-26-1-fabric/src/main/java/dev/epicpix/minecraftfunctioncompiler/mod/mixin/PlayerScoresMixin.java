package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import dev.epicpix.minecraftfunctioncompiler.mod.ScoreRef;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScores;
import net.minecraft.world.scores.Score;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Invalidates the compiled-code score caches ({@link ScoreRef}) when a Score is removed or replaced. */
@Mixin(PlayerScores.class)
public class PlayerScoresMixin {
    @Inject(method = "remove", at = @At("RETURN"))
    private void mcfc$invalidateOnRemove(Objective objective, CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValueZ()) ScoreRef.invalidateAll();
    }

    @Inject(method = "setScore", at = @At("HEAD"))
    private void mcfc$invalidateOnSet(Objective objective, Score score, CallbackInfo ci) {
        ScoreRef.invalidateAll();
    }
}

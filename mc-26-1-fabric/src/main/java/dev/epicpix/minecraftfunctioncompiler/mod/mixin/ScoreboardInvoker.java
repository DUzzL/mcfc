package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The protected change hooks the vanilla ScoreAccess calls (ClientboundSetScorePacket broadcast, setDirty). */
@Mixin(Scoreboard.class)
public interface ScoreboardInvoker {
    @Invoker("onScoreChanged")
    void mcfc$onScoreChanged(ScoreHolder owner, Objective objective, Score score);

    @Invoker("onScoreLockChanged")
    void mcfc$onScoreLockChanged(ScoreHolder owner, Objective objective);
}

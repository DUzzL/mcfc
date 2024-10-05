package dev.epicpix.minecraftfunctioncompiler.data;

public record ScoreAccessorArgument(
    String objective,
    ScoreHolderArgument scoreHolder
) {
    public ScoreAccessorArgument selectSingle() {
        return new ScoreAccessorArgument(objective, scoreHolder.selectSingle());
    }
}

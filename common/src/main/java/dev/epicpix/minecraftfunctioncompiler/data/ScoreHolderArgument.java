package dev.epicpix.minecraftfunctioncompiler.data;

import dev.epicpix.minecraftfunctioncompiler.commands.macros.MacroTemplate;

public record ScoreHolderArgument(
        boolean all,
        MacroTemplate<String> name,
        SelectorArgument selector,
        boolean single
) {

    public static ScoreHolderArgument selectAll() {
        return new ScoreHolderArgument(true, null, null, false);
    }

    public static ScoreHolderArgument selectNamed(MacroTemplate<String> name) {
        return new ScoreHolderArgument(false, name, null, false);
    }

    public static ScoreHolderArgument selectSelector(SelectorArgument selector) {
        return new ScoreHolderArgument(false, null, selector, false);
    }

    public ScoreHolderArgument selectSingle() {
        return new ScoreHolderArgument(all, name, selector, true);
    }

}

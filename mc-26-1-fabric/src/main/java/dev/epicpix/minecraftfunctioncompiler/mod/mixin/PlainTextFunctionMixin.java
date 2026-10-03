package dev.epicpix.minecraftfunctioncompiler.mod.mixin;

import dev.epicpix.minecraftfunctioncompiler.loader.CompiledFunction;
import dev.epicpix.minecraftfunctioncompiler.mod.CompiledDispatchCache;
import dev.epicpix.minecraftfunctioncompiler.mod.FunctionClassLoader;
import net.minecraft.commands.functions.PlainTextFunction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlainTextFunction.class)
public class PlainTextFunctionMixin implements CompiledDispatchCache {

    // Server thread only (all function execution happens there); plain fields are enough. Keyed by the
    // loader's generation number rather than the loader itself: macro instantiations (PlainTextFunctions
    // cached inside MacroFunction, not in the library) would otherwise pin an old loader forever.
    @Unique private int mcfc$generation;
    @Unique private CompiledFunction.@Nullable Entry mcfc$entry;

    @Override
    public CompiledFunction.Entry mcfc$entry(FunctionClassLoader loader) {
        var entry = mcfc$entry;
        if(mcfc$generation != loader.generation || entry == null) {
            entry = loader.getEntry(((PlainTextFunction<?>) (Object) this).id());
            if(entry == null) entry = NOT_COMPILED;
            mcfc$entry = entry;
            mcfc$generation = loader.generation;
        }
        return entry;
    }

    @Override
    public void mcfc$resetDispatchCache() {
        mcfc$entry = null;
        mcfc$generation = 0;
    }
}

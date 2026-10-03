package dev.epicpix.minecraftfunctioncompiler.mod;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Implemented on vanilla's ServerFunctionLibrary (see ServerFunctionLibraryMixin): the exact source lines vanilla
 * parsed into each function of this library, captured at {@code CommandFunction.fromLines}. The compiler compiles
 * and hashes these lines rather than re-reading the pack, so compiled code always matches the text vanilla loaded
 * (folder datapacks can change on disk between /reload and a compile or /mcfc recompile).
 *
 * Only functions vanilla parsed successfully are present. A new library is built on every reload, so the map
 * lives exactly as long as the functions it describes.
 */
public interface FunctionSourceCapture {
    Map<Identifier, List<String>> mcfc$capturedSources();
}

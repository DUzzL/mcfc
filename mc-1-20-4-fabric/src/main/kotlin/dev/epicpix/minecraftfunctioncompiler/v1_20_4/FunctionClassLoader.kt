package dev.epicpix.minecraftfunctioncompiler.v1_20_4

import com.google.gson.JsonObject
import dev.epicpix.minecraftfunctioncompiler.FunctionCompilerClassLoader
import dev.epicpix.minecraftfunctioncompiler.commands.Command
import dev.epicpix.minecraftfunctioncompiler.commands.CommandParser
import dev.epicpix.minecraftfunctioncompiler.commands.IArgumentLookup
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.data.TaggableLocation
import dev.epicpix.minecraftfunctioncompiler.emitter.callMap_get
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.compiler.compile
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.data.RegistryMapping
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.emitter.loadServerFunctionLibrary_functions
import dev.epicpix.minecraftfunctioncompiler.v1_20_4.emitter.loadServerFunctionManager_library
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.execution.tasks.BuildContexts
import net.minecraft.commands.functions.CommandFunction
import net.minecraft.commands.functions.PlainTextFunction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerFunctionManager
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle

class FunctionClassLoader(parent: ClassLoader?, private val functions: Map<ResourceLocation, CommandFunction<CommandSourceStack>>, private val minecraftServer: MinecraftServer) : FunctionCompilerClassLoader(parent, FabricLoader.getInstance().gameDir.resolve("generated"), CommandSourceStack::class.java, MinecraftServer::class.java, ServerFunctionManager::class.java, minecraftServer, LOGGER::info, LOGGER::warn, LOGGER::error) {
    fun loadFunctions(serverFunctionManager: ServerFunctionManager) {
        if(functions.isEmpty()) return
        RegistryMapping.updateRegistryMaps()
        resetHandles()
        val lookup = object : IArgumentLookup {
            override fun lookupFunctions(functions: TaggableLocation): List<DataLocation> {
                return if(functions.isTag) {
                    serverFunctionManager.getTag(ResourceLocation(functions.location.toString())).toList().map { it.id().toDataLocation() }
                }else if(serverFunctionManager.library.functions.containsKey(ResourceLocation(functions.location.toString()))) {
                    listOf(ResourceLocation(functions.location.toString()).toDataLocation())
                }else {
                    listOf()
                }
            }
        }

        val definedFunctions = mutableMapOf<DataLocation, List<Command>>()

        val startParse = System.nanoTime()
        funcLoop@
        for ((key, value) in functions) {
            try {
                if(value is PlainTextFunction) {
                    val parsed = mutableListOf<Command>()
                    for (entry in value.entries) {
                        if (entry !is BuildContexts.Unbound) {
                            continue@funcLoop
                        }
                        parsed.add(CommandParser.parseCommand(entry.commandInput, lookup))
                    }
                    definedFunctions[key.toDataLocation()] = parsed
                }
            } catch (e: UnsupportedOperationException) {
                logWarn.accept("Unable to parse function $key / ${e.message}")
            } catch (e: Throwable) {
                logError.accept("Unable to parse function $key")
                e.printStackTrace()
                FunctionCompilerReporter.reportException("parse_command", e, JsonObject())
            }
        }
        val parseTime = (System.nanoTime()-startParse)/1000000000.0
        loadFunctionClasses(functions.size, parseTime, definedFunctions, createCodeFieldStorage(), serverFunctionManager) { compile(it) }
    }

    fun hasFunctionLoaded(location: ResourceLocation): Boolean {
        return handles.containsKey(location)
    }

    override fun setHandle(key: DataLocation, handle: MethodHandle) {
        handles[ResourceLocation(key.namespace, key.path)] = handle;
    }

    fun runFunction(location: ResourceLocation, stack: CommandSourceStack?) {
        try {
            handles[location]!!.invoke(stack)
        } catch (e: Throwable) {
            logError.accept("Failed to run function $location")
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    private fun createCodeFieldStorage(): CodeFieldStorage {
        return CodeFieldStorage(
            ResourceLocation::class.java,
            CommandFunction::class.java,
            ServerFunctionManager::class.java,
            MinecraftServer::class.java,
        ) { cf, cw, value ->
            cw.callMap_get {
                cw.loadServerFunctionLibrary_functions {
                    cw.loadServerFunctionManager_library {
                        cf.loadServerFunctionManager(cw)
                    }
                }
                cf.loadResourceLocation(cw, value)
            }
            cw.castType(CommandFunction::class.java)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler")
    }
}

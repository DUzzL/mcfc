package dev.epicpix.minecraftfunctioncompiler.v1_20_1

import com.google.gson.JsonObject
import dev.epicpix.minecraftfunctioncompiler.FunctionCompilerClassLoader
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation
import dev.epicpix.minecraftfunctioncompiler.emitter.callMap_get
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage
import dev.epicpix.minecraftfunctioncompiler.parser.Command
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.commands.CommandParser
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.compiler.compile
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.data.updateRegistryMaps
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.emitter.loadServerFunctionLibrary_functions
import dev.epicpix.minecraftfunctioncompiler.v1_20_1.emitter.loadServerFunctionManager_library
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandFunction.CommandEntry
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerFunctionManager
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class FunctionClassLoader(parent: ClassLoader?, private val functions: Map<ResourceLocation, CommandFunction>, private val minecraftServer: MinecraftServer) : FunctionCompilerClassLoader(parent, FabricLoader.getInstance().gameDir.resolve("generated"), CommandSourceStack::class.java, MinecraftServer::class.java, ServerFunctionManager::class.java, minecraftServer, LOGGER::info, LOGGER::warn, LOGGER::error) {
    private val handles: MutableMap<ResourceLocation, MethodHandle> = HashMap()
    fun loadFunctions(serverFunctionManager: ServerFunctionManager) {
        if(functions.isEmpty()) return
        updateRegistryMaps()
        resetHandles()

        val definedFunctions = mutableMapOf<DataLocation, List<Command>>()

        val startParse = System.nanoTime()
        funcLoop@
        for ((key, value) in functions) {
            try {
                for (entry in value.entries) {
                    if (entry !is CommandEntry) {
                        continue@funcLoop
                    }
                }
                definedFunctions[key.toDataLocation()] = CommandParser.parseFunctionCommands(value, minecraftServer, serverFunctionManager)
            } catch (e: UnsupportedOperationException) {
                logWarn("Unable to parse function $key / ${e.message}")
            } catch (e: Throwable) {
                logError("Unable to parse function $key")
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

    fun runFunction(location: ResourceLocation, stack: CommandSourceStack?): Int {
        return try {
            handles[location]!!.invoke(stack) as Int
        } catch (e: Throwable) {
            logError("Failed to run function $location")
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    override fun registerFunction(clazz: Class<*>, key: DataLocation) {
        val handle = MethodHandles.lookup().findStatic(clazz, "run", MethodType.methodType(Int::class.javaPrimitiveType, CommandSourceStack::class.java))
        handles[ResourceLocation(key.namespace, key.path)] = handle
    }

    override fun resetHandles() = handles.clear()

    private fun createCodeFieldStorage(): CodeFieldStorage {
        return CodeFieldStorage(
            resourceLocationClass = ResourceLocation::class.java,
            commandFunctionClass = CommandFunction::class.java,
            serverFunctionManagerClass = ServerFunctionManager::class.java,
            minecraftServerClass = MinecraftServer::class.java,
            mapFunction = { cw, value ->
                cw.callMap_get {
                    cw.loadServerFunctionLibrary_functions {
                        cw.loadServerFunctionManager_library {
                            loadServerFunctionManager(cw)
                        }
                    }
                    loadResourceLocation(cw, value)
                }
                cw.castType(CommandFunction::class.java)
            },
        )
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler")
    }
}

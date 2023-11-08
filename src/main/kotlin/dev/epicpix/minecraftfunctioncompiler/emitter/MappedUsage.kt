package dev.epicpix.minecraftfunctioncompiler.emitter

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandFunction
import net.minecraft.commands.CommandSourceStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerFunctionLibrary
import net.minecraft.server.ServerFunctionManager
import net.minecraft.server.ServerFunctionManager.TraceCallbacks
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.players.PlayerList
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Score
import net.minecraft.world.scores.Scoreboard
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.IOException
import java.nio.file.Files
import java.util.*

@Suppress("RemoveExplicitTypeArguments", "unused", "ReplaceGetOrSet", "HasPlatformType", "FunctionName")
object MappedUsageDefinitions {

    fun MinecraftServer_getScoreboard() = type<MinecraftServer>().scoreboard
    fun MinecraftServer_getPlayerList() = type<MinecraftServer>().playerList
    fun MinecraftServer_getAllLevels() = type<MinecraftServer>().allLevels
    fun ServerLevel_getAllEntities() = type<ServerLevel>().allEntities

    fun PlayerList_getPlayers() = type<PlayerList>().players
    fun PlayerList_getPlayerByName() = type<PlayerList>().getPlayerByName(type<String>())

    fun Entity_getTags() = type<Entity>().tags
    fun Entity_addTag() = type<Entity>().addTag(type<String>())
    fun Entity_removeTag() = type<Entity>().removeTag(type<String>())
    fun Entity_getScoreboardName() = type<Entity>().scoreboardName
    fun Entity_kill() = type<Entity>().kill()
    fun Entity_level() = type<Entity>().level()
    fun Entity_position() = type<Entity>().position()
    fun Entity_getRotationVector() = type<Entity>().rotationVector

    fun ServerLevel_getEntity() = type<ServerLevel>().getEntity(type<UUID>())
    fun ServerFunctionManager_getLibrary() = type<ServerFunctionManager>().library
    fun ServerFunctionManager_execute() = type<ServerFunctionManager>().execute(type<CommandFunction>(), type<CommandSourceStack>(), type<TraceCallbacks>(), type<CompoundTag>())
    fun ServerFunctionLibrary_getFunctions() = type<ServerFunctionLibrary>().functions

    fun CommandSourceStack_getEntity() = type<CommandSourceStack>().entity
    fun CommandSourceStack_getServer() = type<CommandSourceStack>().server
    fun CommandSourceStack_withEntity() = type<CommandSourceStack>().withEntity(type<Entity>())
    fun CommandSourceStack_withLevel() = type<CommandSourceStack>().withLevel(type<ServerLevel>())
    fun CommandSourceStack_withPosition() = type<CommandSourceStack>().withPosition(type<Vec3>())
    fun CommandSourceStack_withRotation() = type<CommandSourceStack>().withRotation(type<Vec2>())

    fun Scoreboard_playerScores() = type<Scoreboard>().playerScores
    fun Scoreboard_getObjective() = type<Scoreboard>().getObjective(type<String>())
    fun Scoreboard_getOrCreatePlayerScore() = type<Scoreboard>().getOrCreatePlayerScore(type<String>(), type<Objective>())
    fun Scoreboard_resetPlayerScore() = type<Scoreboard>().resetPlayerScore(type<String>(), type<Objective>())
    fun Score_setScore() = run { type<Score>().score = type<Int>() }
    fun Score_getScore() = type<Score>().score

    fun Vec3_x() = type<Vec3>().x
    fun Vec3_y() = type<Vec3>().y
    fun Vec3_z() = type<Vec3>().z

    fun ArrayList_size() = type<ArrayList<*>>().size
    fun ArrayList_get() = type<ArrayList<*>>().get(type<Int>())
    fun List_size() = type<List<*>>().size
    fun List_get() = type<List<*>>().get(type<Int>())
    fun Set_size() = type<Set<*>>().size
    fun Set_contains() = type<Set<*>>().contains(type<Any>())
    fun Map_keys() = type<Map<*, *>>().keys
    fun Map_get() = type<Map<*, *>>().get(type<Any>())
    fun Iterator_next() = type<Iterator<*>>().next()
    fun Iterator_hasNext() = type<Iterator<*>>().hasNext()
    fun Iterable_iterator() = type<Iterable<*>>().iterator()

    fun Math_floorDiv() = Math.floorDiv(type<Int>(), type<Int>())
    fun Math_floorMod() = Math.floorMod(type<Int>(), type<Int>())
    fun Math_min() = Math.min(type<Int>(), type<Int>())
    fun Math_max() = Math.max(type<Int>(), type<Int>())

    private fun <T> type(): T = null!!

}

private var isInitialized: Boolean = false

private val mappedUsageDefinitionType: String = MappedUsageDefinitions::class.java.descriptorString().run { substring(1, length - 1) }

fun initDefinitions() {
    if(isInitialized) return
    isInitialized = true
    val mod = FabricLoader.getInstance().getModContainer("minecraftfunctioncompiler").get()
    val thisPath = if(FabricLoader.getInstance().isDevelopmentEnvironment) {
        mod.rootPaths[0].resolve("../../classes/kotlin/main/$mappedUsageDefinitionType.class").toAbsolutePath()
    } else {
        mod.findPath("$mappedUsageDefinitionType.class").get()
    }
    val bytes: ByteArray
    try {
        bytes = Files.readAllBytes(thisPath)
    } catch(e: IOException) {
        throw RuntimeException(e)
    }
    val reader = ClassReader(bytes)
    val visitor: ClassVisitor = MappedFunctionsClassVisitor(Opcodes.ASM9)
    reader.accept(visitor, ClassReader.SKIP_DEBUG)
}

private class MappedFunctionsClassVisitor(api: Int) : ClassVisitor(api) {
    override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
        val field = runCatching { MappedData::class.java.getDeclaredField(name) }
        if(field.isSuccess) {
            val f = field.getOrThrow()
            if(f.type == MappedData::class.java) {
                f.trySetAccessible()
                return MappedUsageMethodVisitor(api, f.get(null) as MappedData)
            }
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }
}

private class MappedUsageMethodVisitor(api: Int, val data: MappedData) : MethodVisitor(api) {
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if(owner == mappedUsageDefinitionType) return
        data.generator = { it.methodVisitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface) }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        if(owner == mappedUsageDefinitionType) return
        data.generator = { it.methodVisitor.visitFieldInsn(opcode, owner, name, descriptor) }
        super.visitFieldInsn(opcode, owner, name, descriptor)
    }
}
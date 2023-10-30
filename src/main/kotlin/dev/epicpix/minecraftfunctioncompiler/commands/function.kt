package dev.epicpix.minecraftfunctioncompiler.commands

import dev.epicpix.minecraftfunctioncompiler.*
import net.minecraft.resources.ResourceLocation
import org.objectweb.asm.Opcodes

fun generate_function(command: Command, context: GeneratorCommandContext, cw: CodeWriter, definedFunctions: Map<ResourceLocation, List<CommandLike>>) {
    for(func in command.get<List<ResourceLocation>>("commands")) {
        if(definedFunctions.containsKey(func)) {
            cw.visitGetCommandSourceStack()
            if(!context.originalEntity) {
                context.getEntity(cw)
                cw.callMethod("net/minecraft/commands/CommandSourceStack", "withEntity", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/commands/CommandSourceStack;")
            }
            cw.callFunction("function/${func.namespace}/${func.path}", "run", "(Lnet/minecraft/commands/CommandSourceStack;)I")
            cw.methodVisitor.visitInsn(Opcodes.POP)
        }else {
            cw.visitGetServer()
            cw.callMethod("net/minecraft/server/MinecraftServer", "getFunctions", "()Lnet/minecraft/server/ServerFunctionManager;")
            cw.methodVisitor.visitInsn(Opcodes.DUP)
            cw.getObjectField("net/minecraft/server/ServerFunctionManager", "library", "Lnet/minecraft/server/ServerFunctionLibrary;")
            cw.getObjectField("net/minecraft/server/ServerFunctionLibrary", "functions", "Ljava/util/Map;")
            cw.writeResourceLocation(func)
            cw.callInterfaceMethod("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
            cw.castType("net/minecraft/commands/CommandFunction")
            cw.visitGetCommandSourceStack()
            if(!context.originalEntity) {
                context.getEntity(cw)
                cw.callMethod("net/minecraft/commands/CommandSourceStack", "withEntity", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/commands/CommandSourceStack;")
            }
            cw.writeNull()
            cw.writeNull()
            cw.callMethod("net/minecraft/server/ServerFunctionManager", "execute", "(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/ServerFunctionManager\$TraceCallbacks;Lnet/minecraft/nbt/CompoundTag;)I")
            cw.methodVisitor.visitInsn(Opcodes.POP)
        }
    }
}
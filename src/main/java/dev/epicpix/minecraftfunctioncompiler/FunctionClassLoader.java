package dev.epicpix.minecraftfunctioncompiler;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class FunctionClassLoader extends ClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraftfunctioncompiler");

    private final Map<ResourceLocation, CommandFunction> functions;
    private final Map<ResourceLocation, MethodHandle> handles = new HashMap<>();

    public FunctionClassLoader(ClassLoader parent, Map<ResourceLocation, CommandFunction> functions) {
        super(parent);
        this.functions = functions;
    }

    public void loadFunctions() {
        handles.clear();
        for(var function : functions.entrySet()) {
            try {
                var clz = loadClass("function." + function.getKey().getNamespace() + "." + function.getKey().getPath().replace('/', '.'));
                var handle = MethodHandles.lookup().findStatic(clz, "run", MethodType.methodType(int.class, CommandSourceStack.class));
                handles.put(function.getKey(), handle);
            } catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException | UnsupportedOperationException e) {
                if(e instanceof UnsupportedOperationException) {
                    LOGGER.warn("Unable to compile function " + function.getKey() + " / " + e.getMessage());
                }else {
                    LOGGER.warn("Unable to compile function " + function.getKey(), e);
                }
            }
        }
    }

    public boolean hasFunctionLoaded(ResourceLocation location) {
        return handles.containsKey(location);
    }

    public int runFunction(ResourceLocation location, CommandSourceStack stack) {
        try {
            return (int) handles.get(location).invoke(stack);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if(!name.startsWith("function.")) {
            return super.findClass(name);
        }
        String n = name.substring("function.".length()).replace('.', '/');
        String[] parts = n.split("/", 2);
        var location = new ResourceLocation(parts[0], parts[1]);

        CommandFunction function = null;
        for(var entry : functions.entrySet()) {
            if(entry.getKey().equals(location)) {
                function = entry.getValue();
            }
        }
        if(function == null) {
            return super.findClass(name);
        }
        for(var entry : function.getEntries()) {
            if(!(entry instanceof CommandFunction.CommandEntry)) {
                return super.findClass(name);
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run",
                "(Lnet/minecraft/commands/CommandSourceStack;)I", null, null);
        mv.visitCode();

        for(var entry : function.getEntries()) {
            if(entry instanceof CommandFunction.CommandEntry cmd) {
                var context = cmd.parse.getContext();
                String commandName = context.getNodes().get(0).getNode().getName();
                CodeGenerator.generateCommand(commandName, cmd.parse.getReader(), context, mv);
            }
        }

        mv.visitLdcInsn(function.getEntries().length);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();

        byte[] data = cw.toByteArray();
        try {
            Files.write(FabricLoader.getInstance().getGameDir().resolve("generated").resolve(name + ".class"), data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return defineClass(name, data, 0, data.length);
    }
}

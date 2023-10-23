package dev.epicpix.minecraftfunctioncompiler;

import net.minecraft.commands.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

public class FunctionClassLoader extends ClassLoader {

    private final Map<ResourceLocation, CommandFunction> functions;

    public FunctionClassLoader(ClassLoader parent, Map<ResourceLocation, CommandFunction> functions) {
        super(parent);
        this.functions = functions;
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String n = name.replace('.', '/');
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
        System.out.println(function);

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, n, null, "java/lang/Object", null);

        cw.visitEnd();

        byte[] data = cw.toByteArray();
        return defineClass(name, data, 0, data.length);
    }
}

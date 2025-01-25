package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack.JavaStackCompiler;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack.StackCompiler;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.List;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;

public class ClassGenerator {

    private final String className;
    private final ClassWriter writer;
    private boolean methodHandlesLookupGenerated = false;

    private ClassGenerator(String className, ClassWriter writer) {
        this.className = className;
        this.writer = writer;
    }

    public String getClassName() {
        return className;
    }

    public byte[] finish() {
        writer.visitEnd();
        return writer.toByteArray();
    }

    // TODO: all of the BytecodeInstructions get generated at once, instead of trying to split them into multiple functions when there's too much code in one function
    // ^^^^ note that even after having functions split, the class might still be too big, so there would have to be a thing which splits instructions into different classes as well
    public void addFunction(String name, List<BytecodeInstruction> instructions, Type returnType, Type... params) {
        var stackInstructions = StackCompiler.compileInstructions(instructions);
        var mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, Type.getMethodDescriptor(returnType, params), null, null);
        JavaStackCompiler.compile(mv, stackInstructions);
        mv.visitEnd();

        if(!methodHandlesLookupGenerated) {
            for (var instr : instructions) {
                if (instr instanceof BytecodeInstruction.StringConcat) {
                    methodHandlesLookupGenerated = true;
                    writer.visitInnerClass("java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandles", "Lookup", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
                    break;
                }
            }
        }
    }

    public void addField(String name, Type type, Object defaultValue) {
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, type.getDescriptor(), null, defaultValue);
    }

    public void addFinalField(String name, Type type) {
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, name, type.getDescriptor(), null, null);
    }

    public static ClassGenerator createClassGenerator(String className) {
        return createClassGenerator(className, false);
    }

    public static ClassGenerator createClassGenerator(String className, boolean useOldJavaVersion) {
        return createClassGenerator(className, useOldJavaVersion ? Opcodes.V1_8 : Opcodes.V16);
    }

    private static ClassGenerator createClassGenerator(String className, int javaVersion) {
        var writer = createClassWriter();
        writer.visit(javaVersion, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, className, null, "java/lang/Object", null);
        return new ClassGenerator(className, writer);
    }

    private static ClassWriter createClassWriter() {
        return new ClassWriter(COMPUTE_FRAMES) {
            public String getCommonSuperClass(String type1, String type2) {
                Class<?> c;
                Class<?> d;
                try {
                    c = Class.forName(type1.replace('/', '.'), false, getClassLoader());
                    d = Class.forName(type2.replace('/', '.'), false, getClassLoader());
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
                if(c.isAssignableFrom(d)) return type1;
                if(d.isAssignableFrom(c)) return type2;
                if(c.isInterface() || d.isInterface()) return "java/lang/Object";
                do {
                    c = c.getSuperclass();
                } while (!c.isAssignableFrom(d));
                return c.getName().replace('.', '/');
            }
        };
    }

}

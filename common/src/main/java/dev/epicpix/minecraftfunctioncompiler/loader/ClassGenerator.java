package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack.JavaStackCompiler;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack.StackCompiler;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ClassGenerator {

    private final String className;
    private final ClassBuilder builder;
    private boolean methodHandlesLookupGenerated = false;

    private ClassGenerator(ClassDesc className, ClassBuilder builder) {
        this.className = className.displayName();
        this.builder = builder;
    }

    public String getClassName() {
        return className;
    }

    // TODO: all of the BytecodeInstructions get generated at once, instead of trying to split them into multiple functions when there's too much code in one function
    // ^^^^ note that even after having functions split, the class might still be too big, so there would have to be a thing which splits instructions into different classes as well
    public void addFunction(String name, List<BytecodeInstruction> instructions, MethodTypeDesc methodType) {
        var stackInstructions = StackCompiler.compileInstructions(instructions);
        builder.withMethodBody(name, methodType, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, codeBuilder -> {
            JavaStackCompiler.compile(codeBuilder, stackInstructions);
        });

        if(!methodHandlesLookupGenerated) {
            for (var instr : instructions) {
                if (instr instanceof BytecodeInstruction.StringConcat) {
                    methodHandlesLookupGenerated = true;
                    builder.with(InnerClassesAttribute.of(InnerClassInfo.of(ConstantDescs.CD_MethodHandles_Lookup, Optional.of(ConstantDescs.CD_MethodHandles), Optional.of("Lookup"), AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL)));
                    break;
                }
            }
        }
    }

    public void addField(String name, ClassDesc type, @Nullable ConstantDesc defaultValue) {
        builder.withField(name, type, fieldBuilder -> {
            fieldBuilder.withFlags(AccessFlag.PUBLIC, AccessFlag.STATIC);
            if(defaultValue != null) {
                fieldBuilder.with(ConstantValueAttribute.of(defaultValue));
            }
        });
    }

    public void addFinalField(String name, ClassDesc type) {
        builder.withField(name, type, fieldBuilder -> fieldBuilder.withFlags(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL));
    }

    public static byte[] createClassGenerator(ClassDesc className, Consumer<ClassGenerator> handler) {
        return createClassGenerator(className, false, handler);
    }

    public static byte[] createClassGenerator(ClassDesc className, boolean useOldJavaVersion, Consumer<ClassGenerator> handler) {
        return createClassGenerator(className, useOldJavaVersion ? ClassFile.JAVA_8_VERSION : ClassFile.JAVA_16_VERSION, handler);
    }

    private static byte[] createClassGenerator(ClassDesc className, int javaVersion, Consumer<ClassGenerator> handler) {
        return createEmptyClassFile().build(className, builder -> {
            builder.withVersion(javaVersion, 0);
            builder.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL);
            handler.accept(new ClassGenerator(className, builder));
        });
    }

    private static ClassFile createEmptyClassFile() {
        return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(ClassGenerator.class.getClassLoader())));
    }

}

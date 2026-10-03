package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeInstruction;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack.JavaStackCompiler;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ClassGenerator {

    /** One ClassFile context for building AND verifying generated classes, so stack-map
     *  generation and {@link #verify} resolve the class hierarchy identically. The
     *  resolver is cached (thread-safe map: batches build in parallel) because every
     *  batch asks about the same few hundred Minecraft types. */
    private static final ClassFile CLASS_FILE = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
        ClassHierarchyResolver.ofClassLoading(ClassGenerator.class.getClassLoader()).cached(java.util.concurrent.ConcurrentHashMap::new)));

    /** The shared build/verify context, for generated classes that don't go through {@link ClassGenerator}. */
    static ClassFile classFile() {
        return CLASS_FILE;
    }

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

    public void addFunction(String name, List<BytecodeInstruction> instructions, MethodTypeDesc methodType) {
        addMethod(MethodEmission.of(name, instructions, methodType, MethodEmission.NO_CHUNK));
    }

    /** Adds a method whose stack instructions were already compiled (see {@link MethodEmission#of}). */
    public void addMethod(MethodEmission method) {
        builder.withMethodBody(method.name(), method.type(), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, codeBuilder -> {
            JavaStackCompiler.compile(codeBuilder, method.code());
        });
        if(method.usesStringConcat()) addLookupInnerClass();
    }

    private void addLookupInnerClass() {
        if(methodHandlesLookupGenerated) return;
        methodHandlesLookupGenerated = true;
        builder.with(InnerClassesAttribute.of(InnerClassInfo.of(ConstantDescs.CD_MethodHandles_Lookup, Optional.of(ConstantDescs.CD_MethodHandles), Optional.of("Lookup"), AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL)));
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
        return CLASS_FILE.build(className, builder -> {
            builder.withVersion(javaVersion, 0);
            builder.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL);
            handler.accept(new ClassGenerator(className, builder));
        });
    }

    /** Builds a batch-style class (public final, Java 16) holding exactly {@code methods}. */
    public static byte[] createClass(ClassDesc className, List<MethodEmission> methods) {
        return createClassGenerator(className, gen -> {
            for(var method : methods) gen.addMethod(method);
        });
    }

    /** Runs the JDK verifier (the same checks HotSpot performs at link time) over
     *  {@code bytes}, resolving the hierarchy exactly like stack-map generation did. */
    public static List<VerifyError> verify(byte[] bytes) {
        return CLASS_FILE.verify(bytes);
    }

    public static ClassModel parse(byte[] bytes) {
        return CLASS_FILE.parse(bytes);
    }

    /** Length of the method's Code attribute in bytes, or 0 for abstract/native methods. */
    public static int codeLength(MethodModel method) {
        return method.findAttribute(Attributes.code()).map(code -> code.codeLength()).orElse(0);
    }

    /**
     * Copies {@code source}, dropping every method matched by {@code dropMethod} and
     * appending {@code extraMethods}. Kept methods are copied verbatim: the new class
     * shares {@code source}'s constant pool, so their code is not re-encoded.
     */
    public static byte[] rewriteClass(ClassModel source, Predicate<MethodModel> dropMethod, List<MethodEmission> extraMethods) {
        var className = source.thisClass().asSymbol();
        return CLASS_FILE.build(source.thisClass(), ConstantPoolBuilder.of(source), builder -> {
            for(var element : source) {
                if(element instanceof MethodModel method && dropMethod.test(method)) continue;
                builder.with(element);
            }
            var gen = new ClassGenerator(className, builder);
            // The source may already carry the Lookup inner-class attribute.
            gen.methodHandlesLookupGenerated = source.findAttribute(Attributes.innerClasses()).isPresent();
            for(var method : extraMethods) gen.addMethod(method);
        });
    }

}

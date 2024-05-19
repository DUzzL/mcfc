package dev.epicpix.minecraftfunctioncompiler;

import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.GraphGenerator;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.optimizer.OptimizationStatistics;
import dev.epicpix.minecraftfunctioncompiler.parser.Command;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstantsKt.DATA_CLASS;
import static dev.epicpix.minecraftfunctioncompiler.CommonConstantsKt.DEBUG;
import static dev.epicpix.minecraftfunctioncompiler.il.InstructionsKt.printInstructionList;
import static dev.epicpix.minecraftfunctioncompiler.optimizer.OptimizerKt.optimize;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;

public abstract class FunctionCompilerClassLoader extends ClassLoader {
    protected final HashMap<Object, MethodHandle> handles = new HashMap<>();

    public final ClassLoader parent;
    private final Path generatedDir;
    private final Class<?> commandSourceStackClass;
    private final Class<?> minecraftServerClass;
    private final Class<?> serverFunctionManagerClass;
    private final Object minecraftServer;
    public final Consumer<String> logInfo;
    public final Consumer<String> logWarn;
    public final Consumer<String> logError;

    public FunctionCompilerClassLoader(ClassLoader parent, Path generatedDir, Class<?> commandSourceStackClass, Class<?> minecraftServerClass, Class<?> serverFunctionManagerClass, Object minecraftServer, Consumer<String> logInfo, Consumer<String> logWarn, Consumer<String> logError) {
        super(parent);
        this.parent = parent;
        this.generatedDir = generatedDir;
        this.commandSourceStackClass = commandSourceStackClass;
        this.minecraftServerClass = minecraftServerClass;
        this.serverFunctionManagerClass = serverFunctionManagerClass;
        this.minecraftServer = minecraftServer;
        this.logInfo = logInfo;
        this.logWarn = logWarn;
        this.logError = logError;
    }

    protected abstract void setHandle(DataLocation key, MethodHandle handle);

    public void registerFunction(Class<?> clazz, DataLocation key) {
        try {
            setHandle(key, MethodHandles.lookup().findStatic(clazz, "run", MethodType.methodType(int.class, commandSourceStackClass)));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void resetHandles() {
        handles.clear();
    }

    public void loadFunctionClasses(
            int foundFunctionCount,
            double parseTime,
            Map<DataLocation, List<Command>> definedFunctions,
            CodeFieldStorage fieldStorage,
            Object serverFunctionManager,
            Function<List<Command>, List<Instruction>> compile
        ) {
        HashMap<DataLocation, List<Instruction>> compiledFunctions = new HashMap<>();
        ArrayList<List<OptimizationStatistics>> optimizerStatistics = new ArrayList<>();
        var startCompile = System.nanoTime();
        for(var func : definedFunctions.entrySet()) {
            var key = func.getKey();
            var value = func.getValue();
            try {
                var compiled = compile.apply(value);
                ArrayList<OptimizationStatistics> stats = new ArrayList<>();
                var optimized = optimize(compiled, stats);
                optimizerStatistics.add(stats);
                compiledFunctions.put(key, List.of(Instructions2.INSTANCE.addStaticRequirements(optimized)));
                if(DEBUG) {
                    logInfo.accept(" --- " + key + " ---");
                    printInstructionList(compiledFunctions.get(key), logInfo, 0);
                }
            }catch(UnsupportedOperationException e) {
                logWarn.accept("Failed to compile function " + key + " / " + e.getMessage());
            }catch(Throwable e) {
                logError.accept("Failed to compile function " + key);
                e.printStackTrace();
                FunctionCompilerReporter.reportException("compile_function", e, new JsonObject());
            }
        }
        var compileTime = (System.nanoTime()-startCompile)/1000000000d;
        var compiledFunctionSet = new HashSet<>(compiledFunctions.keySet());
        var loadedFunctions = new HashMap<DataLocation, Class<?>>();
        var startLoader = System.nanoTime();
        for(var key : compiledFunctions.keySet()) {
            try {
                loadedFunctions.put(key, defineFunction(key, fieldStorage, compiledFunctions.get(key), definedFunctions.get(key).size(), compiledFunctionSet));
            }catch(Throwable e) {
                logError.accept("Failed to load function (" + key + "), skipping function compilation");
                e.printStackTrace();
                FunctionCompilerReporter.reportExceptionWithInstructions("define_function", e, compiledFunctions.get(key));
                FunctionCompilerReporter.sendReports();
                return;
            }
        }

        try {
            loadData(fieldStorage, serverFunctionManager);
        }catch(Throwable e) {
            logError.accept("Failed to load data, clearing data");
            e.printStackTrace();
            FunctionCompilerReporter.reportException("define_data_class", e, new JsonObject());
            FunctionCompilerReporter.sendReports();
            return;
        }
        var classLoadTime = (System.nanoTime()-startLoader)/1000000000d;
        if(DEBUG) {
            try { Files.createDirectory(generatedDir); } catch(Exception ignored) {}
            var startGraph = System.nanoTime();
            try {
                GraphGenerator.generateGraphs(compiledFunctions, generatedDir);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            logInfo.accept("Generated graphs in " + ((System.nanoTime()-startGraph)/1000000) + "ms");
        }

        var startHandles = System.nanoTime();
        for(var key : compiledFunctions.keySet()) {
            try {
                registerFunction(loadedFunctions.get(key), key);
            }catch(Throwable e) {
                logError.accept("Failed to create function handle ($key), clearing data");
                e.printStackTrace();
                FunctionCompilerReporter.reportExceptionWithInstructions("load_function_class", e, compiledFunctions.get(key));
                FunctionCompilerReporter.sendReports();
                resetHandles();
                logInfo.accept("Instruction dump:");
                printInstructionList(compiledFunctions.get(key), logInfo, 0);
                break;
            }
        }
        var handleLoadTime = (System.nanoTime()-startHandles)/1000000000d;
        FunctionCompilerReporter.reportCompilationStatistics(foundFunctionCount, definedFunctions.size(), compiledFunctions.size(), parseTime, compileTime, classLoadTime, handleLoadTime, optimizerStatistics);

        FunctionCompilerReporter.sendReports();
    }

    private Class<?> defineFunction(DataLocation funcId, CodeFieldStorage fieldStorage, List<Instruction> instructions, int commandCount, Set<DataLocation> compiledFunctions) {
        var className = ("function/" + funcId.namespace() + "/" + funcId.path()).replace('.', '$');

        var cw = createClassWriter();
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, className, null, "java/lang/Object", null);
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(" + commandSourceStackClass.descriptorString() + ")I", null, null);
        mv.visitCode();
        var codeWriter = new CodeWriter(mv);
        codeWriter.defineLocal("static$CommandSourceStack", commandSourceStackClass, () -> {
            new EmitterContext(fieldStorage, codeWriter, compiledFunctions, new HashMap<>(), new HashMap<>(), Instructions2.INSTANCE.root().withChildren(new ArrayList<>(instructions)), new HashMap<>(), new HashMap<>()).run();
        });

        mv.visitLdcInsn(commandCount);
        mv.visitInsn(Opcodes.IRETURN);
        codeWriter.writeLocalVariables();
        try {
            mv.visitMaxs(0, 0);
        } catch(Exception e) {
            throw new IllegalStateException("Failed to visit maxs", e);
        }
        mv.visitEnd();

        cw.visitEnd();
        return defineClass(className.replace('/', '.'), cw.toByteArray());
    }

    private Class<?> loadData(CodeFieldStorage fieldStorage, Object serverFunctionManager) {
        var cw = createClassWriter();
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, DATA_CLASS, null, "java/lang/Object", null);
        fieldStorage.generate(cw);
        cw.visitEnd();

        var defined = defineClass(DATA_CLASS.replace('/', '.'), cw.toByteArray());
        try {
            defined.getDeclaredMethod("load", minecraftServerClass, serverFunctionManagerClass).invoke(null, minecraftServer, serverFunctionManager);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return defined;
    }

    private ClassWriter createClassWriter() {
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

    private Class<?> defineClass(String name, byte[] data) {
        if(CommonConstantsKt.DEBUG) {
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                Files.write(generatedDir.resolve(name + ".class"), data);
            }catch(Exception ignored) {}
        }
        return defineClass(name, data, 0, data.length);
    }
}

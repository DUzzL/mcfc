package dev.epicpix.minecraftfunctioncompiler;

import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.commands.*;
import dev.epicpix.minecraftfunctioncompiler.compiler.CommandCompiler;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.emitter.EmitterContext;
import dev.epicpix.minecraftfunctioncompiler.emitter.GraphGenerator;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeFieldStorage;
import dev.epicpix.minecraftfunctioncompiler.emitter.impl.CodeWriter;
import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.Instructions2;
import dev.epicpix.minecraftfunctioncompiler.il.optimizer.OptimizationStatistics;
import dev.epicpix.minecraftfunctioncompiler.il.optimizer.Optimizer;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCompilerReporter;
import dev.epicpix.minecraftfunctioncompiler.reporter.FunctionCountsData;
import dev.epicpix.minecraftfunctioncompiler.reporter.TimingData;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static dev.epicpix.minecraftfunctioncompiler.CommonConstants.*;
import static dev.epicpix.minecraftfunctioncompiler.il.Instruction.printInstructionList;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;

public abstract class FunctionCompilerClassLoader extends ClassLoader {

    public interface SourceFileProvider {
        Map<DataLocation, List<String>> mcfc$getSourceFiles();
    }

    protected final HashMap<Object, MethodHandle> handles = new HashMap<>();

    public final ClassLoader parent;
    private final Path generatedDir;
    private final Class<?> commandSourceStackClass;
    private final Class<?> minecraftServerClass;
    private final Class<?> serverFunctionManagerClass;
    private final Object minecraftServer;
    public final Consumer<String> logInfo;
    public final Consumer<String> logWarn;
    public final BiConsumer<String, Throwable> logError;
    private final HashMap<DataLocation, List<String>> sourceFiles;

    private State state = State.NOTHING_LOADED;

    public enum State {
        FAILED,
        NOTHING_LOADED,
        PARSING,
        COMPILING,
        LOADING,
        READY_OFF,
        READY_ON,
    }

    public FunctionCompilerClassLoader(ClassLoader parent, Path generatedDir, Class<?> commandSourceStackClass, Class<?> minecraftServerClass, Class<?> serverFunctionManagerClass, Object minecraftServer, Consumer<String> logInfo, Consumer<String> logWarn, BiConsumer<String, Throwable> logError, Map<DataLocation, List<String>> sourceFiles) {
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
        this.sourceFiles = new HashMap<>(sourceFiles);
    }

    protected abstract void setHandle(DataLocation key, MethodHandle handle);

    public void registerFunction(Class<?> clazz, DataLocation key) {
        try {
            setHandle(key, MethodHandles.lookup().findStatic(clazz, "run", MethodType.methodType(long.class, commandSourceStackClass)));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void resetHandles() {
        handles.clear();
    }

    public final boolean isUseReady() {
        return state == State.READY_ON;
    }

    public final State getState() {
        return state;
    }

    private void setState(State newState) {
        state = newState;
    }

    public void tryOff() {
        if(state == State.READY_ON) {
            setState(State.READY_OFF);
        }
    }

    public void tryOn() {
        if(state == State.READY_OFF) {
            setState(State.READY_ON);
        }
    }

    public int loadedFunctionCount() {
        return handles.size();
    }

    public int totalFunctionCount() {
        return sourceFiles.size();
    }

    private Thread backgroundThread;

    protected boolean compileInBackground(Runnable run) {
        if(backgroundThread == Thread.currentThread()) return false;
        if(state != State.NOTHING_LOADED) return true;
        setState(State.PARSING);
        backgroundThread = new Thread(run);
        backgroundThread.setName("Function Compiler");
        backgroundThread.start();
        return true;
    }

    public void loadFunctionClasses(
            IArgumentLookup lookup,
            CodeFieldStorage fieldStorage,
            Object serverFunctionManager
        ) {
        HashMap<DataLocation, List<Command>> definedFunctions = new HashMap<>();
        ArrayList<CommandError> commandErrors = new ArrayList<>();

        var startParse = System.nanoTime();
        for(var entry : sourceFiles.entrySet()) {
            var key = entry.getKey();
            var values = entry.getValue();
            String currentCommand = null;
            try {
                ArrayList<Command> parsed = new ArrayList<>();
                for(int i = 0; i<values.size(); i++) {
                    StringBuilder value = new StringBuilder(values.get(i).trim());
                    if(MinecraftVersion.isCurrentVersionInRange(MinecraftVersion.MC_1_20_2, null)) {
                        while(value.toString().endsWith("\\")) {
                            String next = values.get(++i).trim();
                            value.deleteCharAt(value.length() - 1);
                            value.append(next);
                        }
                    }
                    currentCommand = value.toString();
                    if(currentCommand.isEmpty() || currentCommand.startsWith("#")) continue;
                    if(MinecraftVersion.isCurrentVersionInRange(MinecraftVersion.MC_1_20_2, null) && currentCommand.startsWith("$")) {
                        throw new CommandErrorException(new CommandError(currentCommand, "Macros are not yet supported"));
                    }
                    parsed.add(CommandParser.parseCommand(currentCommand, lookup));
                }
                definedFunctions.put(key, parsed);
            } catch (CommandErrorException e) {
                logWarn.accept("Unable to parse function " + key + " / " + e.getMessage());
                commandErrors.add(e.error);
            } catch (StringCommandReaderException e) {
                logWarn.accept("Unable to parse function " + key + " / " + e.getMessage() + " / " + currentCommand);
                commandErrors.add(new CommandError(currentCommand, e.getMessage()));
            } catch (Throwable e) {
                logError.accept("Unable to parse function " + key, e);
                commandErrors.add(new CommandError(currentCommand, e.getMessage(), CommandError.Step.PARSE, e));
            }
        }
        var parseTime = (System.nanoTime()-startParse)/1000000000d;
        setState(State.COMPILING);
        logInfo.accept("Compiling functions");
        HashMap<DataLocation, List<Instruction>> compiledFunctions = new HashMap<>();
        ArrayList<List<OptimizationStatistics>> optimizerStatistics = new ArrayList<>();
        var startCompile = System.nanoTime();
        for(var func : definedFunctions.entrySet()) {
            var key = func.getKey();
            var value = func.getValue();
            try {
                var compiled = Instructions2.root().withChildren(new ArrayList<>(CommandCompiler.compile(value)));
                ArrayList<OptimizationStatistics> stats = new ArrayList<>();
                var optimized = Optimizer.optimize(compiled, stats);
                optimizerStatistics.add(stats);
                compiledFunctions.put(key, List.of(Instructions2.addStaticRequirements(optimized)));
            }catch(UnsupportedOperationException e) {
                logWarn.accept("Failed to compile function " + key + " / " + e.getMessage());
                commandErrors.add(new CommandError(e.getMessage()));
            }catch(Throwable e) {
                logError.accept("Failed to compile function " + key, e);
                commandErrors.add(new CommandError(null, e.getMessage(), CommandError.Step.COMPILE, e));
            }
        }
        var compileTime = (System.nanoTime()-startCompile)/1000000000d;
        if(DEBUG) {
            StringBuilder instructionDump = new StringBuilder();
            var entries = new ArrayList<>(compiledFunctions.entrySet());
            entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            for(var entry : entries) {
                instructionDump.append("┌──\n");
                instructionDump.append("│ function ").append(entry.getKey()).append("\n");
                var commandCount = definedFunctions.get(entry.getKey()).size();
                instructionDump.append("│ ┣━ ").append(commandCount).append(" command").append(commandCount != 1 ? "s" : "").append("\n");
                var instructionCount = Instruction.countInstructions(compiledFunctions.get(entry.getKey()));
                instructionDump.append("│ ┗━ ").append(instructionCount).append(" instruction").append(instructionCount != 1 ? "s" : "").append(" (around ").append(String.format("%.02f", commandCount == 0 ? 0d : (double)instructionCount / commandCount)).append(" instructions per command)").append("\n");
                instructionDump.append("│\n");
                printInstructionList(entry.getValue(), line -> instructionDump.append("│ ").append(line).append("\n"));
                instructionDump.append("└──\n");
                instructionDump.append("\n");
            }
            try {
                Files.createDirectory(generatedDir);
            }catch(Exception ignored) {}
            try {
                String instructionDumpStr = instructionDump.toString();
                Files.writeString(generatedDir.resolve("instruction_dump.txt"), instructionDumpStr);
                logInfo.accept("Wrote " + instructionDumpStr.lines().count() + " lines into instruction_dump.txt");
            }catch(Exception ignored) {}
        }
        var compiledFunctionSet = new HashSet<>(compiledFunctions.keySet());
        var loadedFunctions = new HashMap<DataLocation, Class<?>>();
        setState(State.LOADING);
        var startLoader = System.nanoTime();
        for(var key : compiledFunctions.keySet()) {
            try {
                loadedFunctions.put(key, defineFunction(key, fieldStorage, compiledFunctions.get(key), definedFunctions.get(key).size(), compiledFunctionSet));
            }catch(Throwable e) {
                logError.accept("Failed to load function (" + key + "), skipping function compilation", e);
                setState(State.FAILED);
                FunctionCompilerReporter.reportExceptionWithInstructions("define_function", e, compiledFunctions.get(key));
                FunctionCompilerReporter.sendReports();
                return;
            }
        }

        try {
            loadData(fieldStorage, serverFunctionManager);
        }catch(Throwable e) {
            logError.accept("Failed to load data", e);
            setState(State.FAILED);
            FunctionCompilerReporter.reportException("define_data_class", e, new JsonObject());
            FunctionCompilerReporter.sendReports();
            return;
        }
        var classLoadTime = (System.nanoTime()-startLoader)/1000000000d;
        if(DEBUG_GENERATE_GRAPH) {
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
                logError.accept("Failed to create function handle (" + key + "), clearing data", e);
                setState(State.FAILED);
                FunctionCompilerReporter.reportExceptionWithInstructions("load_function_class", e, compiledFunctions.get(key));
                FunctionCompilerReporter.sendReports();
                resetHandles();
                if(DEBUG) {
                    logInfo.accept("Instruction dump:");
                    printInstructionList(compiledFunctions.get(key), logInfo);
                }
                return;
            }
        }
        var handleLoadTime = (System.nanoTime()-startHandles)/1000000000d;

        var functionCountsData = new FunctionCountsData(sourceFiles.size(), definedFunctions.size(), compiledFunctions.size());
        var timingData = new TimingData(parseTime, compileTime, classLoadTime, handleLoadTime);
        FunctionCompilerReporter.reportCompilationStatistics(functionCountsData, timingData, optimizerStatistics, commandErrors);

        FunctionCompilerReporter.sendReports();
        logInfo.accept("Switched over to running compiled functions");
        setState(State.READY_ON);
    }

    private Class<?> defineFunction(DataLocation funcId, CodeFieldStorage fieldStorage, List<Instruction> instructions, int commandCount, Set<DataLocation> compiledFunctions) {
        var className = ("function/" + funcId.namespace() + "/" + funcId.path()).replace('.', '$');

        var cw = createClassWriter();
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, className, null, "java/lang/Object", null);
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(" + commandSourceStackClass.descriptorString() + ")J", null, null);
        mv.visitCode();
        var codeWriter = new CodeWriter(mv);


        var localValue = codeWriter.createLocal("static$CommandSourceStack", commandSourceStackClass.descriptorString());
        new EmitterContext(fieldStorage, codeWriter, compiledFunctions, new HashMap<>(), new HashMap<>(), Instructions2.root().withChildren(new ArrayList<>(instructions)), new HashMap<>(), new HashMap<>()).run();
        codeWriter.destroyLocal(localValue);

        codeWriter.writeLong(commandCount | (0x2L << 32));
        mv.visitInsn(Opcodes.LRETURN);
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

    private Class<?> loadData(CodeFieldStorage fieldStorage, Object serverFunctionManager) throws Throwable {
        var cw = createClassWriter();
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, DATA_CLASS, null, "java/lang/Object", null);
        fieldStorage.generate(cw);
        cw.visitEnd();

        var defined = defineClass(DATA_CLASS.replace('/', '.'), cw.toByteArray());
        defined.getDeclaredMethod("load", minecraftServerClass, serverFunctionManagerClass).invoke(null, minecraftServer, serverFunctionManager);
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
        if(CommonConstants.DEBUG) {
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

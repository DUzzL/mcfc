package dev.epicpix.minecraftfunctioncompiler.loader;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeRegister;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.BytecodeValue;
import dev.epicpix.minecraftfunctioncompiler.emitter.bytecode.stack.StackInstruction;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stack charge of compiled call sites, for the platform's recursion guard.
 *
 * A call site that grows the Java stack (a compiled->compiled invokestatic, or a hand-off to vanilla that may
 * re-enter compiled code) is emitted with a read of {@link #PLACEHOLDER}: "the stack, in words, that this
 * function occupies at this call site". Frame sizes are only known once a method is stack-compiled, so the
 * placeholder is replaced here, per function, by a constant: the estimated interpreted frame size of the
 * method containing the call plus every method of the same function that can sit beneath it (entry ->
 * dispatch tree -> chunk -> ...). Charging the whole function chain per call keeps the guard's budget an
 * upper bound of the real stack whatever the frame sizes, so the guard always fires before the Java stack
 * overflows.
 *
 * The interpreted frame is the worst case (the first deep recursion after a reload runs interpreted): locals,
 * operand stack and the interpreter's fixed frame. Generated code keeps one register per local slot, so
 * max_locals is read off the instructions; the operand stack of generated code stays small.
 */
public final class FrameCost {
    private FrameCost() {}

    /** Static int read standing for the call site's charge; never present in an assembled class. */
    public static final InstructionTarget.FieldTarget PLACEHOLDER = new InstructionTarget.FieldTarget(
        ClassDesc.of("mcfc.FrameCostPlaceholder"), "callerStackWords", ConstantDescs.CD_int, true, false);

    /** Interpreter frame header plus an operand-stack allowance, in words. */
    static final int FRAME_OVERHEAD_WORDS = 40;

    /** Interpreted frame of {@code method}, in words. */
    static int frameWords(MethodEmission method) {
        int maxLocals = 0;
        for(var param : method.type().parameterList()) maxLocals += slots(param);
        for(var instr : method.code()) {
            switch(instr) {
                case StackInstruction.Load(BytecodeRegister(int reg), ClassDesc type) -> maxLocals = Math.max(maxLocals, reg + slots(type));
                case StackInstruction.Load(BytecodeValue.Parameter p, ClassDesc type) -> maxLocals = Math.max(maxLocals, p.index() + slots(type));
                case StackInstruction.Store(BytecodeRegister(int reg)) -> maxLocals = Math.max(maxLocals, reg + 2);
                default -> {}
            }
        }
        return maxLocals + FRAME_OVERHEAD_WORDS;
    }

    private static int slots(ClassDesc type) {
        return type.equals(ConstantDescs.CD_long) || type.equals(ConstantDescs.CD_double) ? 2 : 1;
    }

    /**
     * Replaces every {@link #PLACEHOLDER} read in {@code function}'s methods. {@code owner} is the batch class;
     * calls to {@code entryName} are calls of the function itself (recursion), not part of its chain.
     */
    static FunctionMethods apply(FunctionMethods function, ClassDesc owner, String entryName) {
        var methods = function.methods();
        boolean any = false;
        for(var method : methods) {
            if(containsPlaceholder(method)) {
                any = true;
                break;
            }
        }
        if(!any) return function;

        var byName = new HashMap<String, MethodEmission>();
        for(var method : methods) byName.put(method.name(), method);
        var callers = new HashMap<String, List<String>>();
        for(var method : methods) {
            for(var instr : method.code()) {
                if(instr instanceof StackInstruction.Call(InstructionTarget.FunctionTarget t) && t.isStatic()
                    && t.classType().equals(owner) && !t.name().equals(entryName) && byName.containsKey(t.name())) {
                    callers.computeIfAbsent(t.name(), k -> new ArrayList<>()).add(method.name());
                }
            }
        }
        var chain = new HashMap<String, Integer>();
        var patched = new ArrayList<MethodEmission>(methods.size());
        for(var method : methods) {
            if(!containsPlaceholder(method)) {
                patched.add(method);
                continue;
            }
            var cost = new StackInstruction.Load(new BytecodeValue.IntegerValue(chainWords(method.name(), byName, callers, chain, new HashSet<>())), ConstantDescs.CD_int);
            var code = new ArrayList<StackInstruction>(method.code().size());
            for(var instr : method.code()) code.add(isPlaceholder(instr) ? cost : instr);
            patched.add(new MethodEmission(method.name(), method.type(), List.copyOf(code), method.usesStringConcat(), method.chunkIndex()));
        }
        return new FunctionMethods(function.id(), function.hasMacros(), function.stub(), List.copyOf(patched), function.chunks());
    }

    /** Frame of {@code name} plus the deepest chain of same-function callers beneath it. */
    private static int chainWords(String name, Map<String, MethodEmission> byName, Map<String, List<String>> callers, Map<String, Integer> memo, Set<String> visiting) {
        var known = memo.get(name);
        if(known != null) return known;
        if(!visiting.add(name)) return 0; // not produced by codegen; never loop
        int below = 0;
        for(var caller : callers.getOrDefault(name, List.of())) below = Math.max(below, chainWords(caller, byName, callers, memo, visiting));
        visiting.remove(name);
        int words = frameWords(byName.get(name)) + below;
        memo.put(name, words);
        return words;
    }

    private static boolean containsPlaceholder(MethodEmission method) {
        for(var instr : method.code()) if(isPlaceholder(instr)) return true;
        return false;
    }

    private static boolean isPlaceholder(StackInstruction instr) {
        return instr instanceof StackInstruction.Call(InstructionTarget target) && target.equals(PLACEHOLDER);
    }
}

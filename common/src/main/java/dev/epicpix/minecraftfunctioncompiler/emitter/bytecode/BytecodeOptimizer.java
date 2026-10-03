package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class BytecodeOptimizer {

    /** Registers read by {@code instruction}. */
    private static void forEachRead(BytecodeInstruction instruction, java.util.function.IntConsumer consumer) {
        java.util.function.Consumer<BytecodeValue> read = v -> {
            if(v instanceof BytecodeRegister(int reg)) consumer.accept(reg);
        };
        switch(instruction) {
            case BytecodeInstruction.RegisterValue v -> read.accept(v.source());
            case BytecodeInstruction.RegisterValueValue v -> {
                read.accept(v.sourceA());
                read.accept(v.sourceB());
            }
            case BytecodeInstruction.CreateArray v -> read.accept(v.length());
            case BytecodeInstruction.SetArrayValue v -> {
                read.accept(v.array());
                read.accept(v.index());
                read.accept(v.value());
            }
            case BytecodeInstruction.InstanceCheckJump v -> read.accept(v.a());
            case BytecodeInstruction.ConditionalJump v -> {
                read.accept(v.a());
                read.accept(v.b());
            }
            case BytecodeInstruction.CallTarget v -> v.arguments().forEach(read);
            case BytecodeInstruction.New v -> v.arguments().forEach(read);
            case BytecodeInstruction.Cast v -> read.accept(v.source());
            case BytecodeInstruction.StringConcat v -> v.values().forEach(read);
            case BytecodeInstruction.Return v -> read.accept(v.value());
            case BytecodeInstruction.Jump _, BytecodeInstruction.Label _, BytecodeInstruction.ReturnVoid _ -> {}
        }
    }

    /** Register written by {@code instruction}, or -1. */
    private static int writtenRegister(BytecodeInstruction instruction) {
        return switch(instruction) {
            case BytecodeInstruction.RegisterValue v -> v.result().reg();
            case BytecodeInstruction.RegisterValueValue v -> v.result().reg();
            case BytecodeInstruction.CreateArray v -> v.result().reg();
            case BytecodeInstruction.CallTarget v -> v.result().reg();
            case BytecodeInstruction.New v -> v.result().reg();
            case BytecodeInstruction.Cast v -> v.result().reg();
            case BytecodeInstruction.StringConcat v -> v.result().reg();
            default -> -1;
        };
    }

    private static BytecodeValue remapValue(Int2ObjectMap<@Nullable BytecodeValue> registerDefinitionTracker, BytecodeValue value) {
        if(value instanceof BytecodeRegister baseReg) {
            BytecodeValue prevReg = baseReg;
            while(true) {
                if(prevReg instanceof BytecodeRegister(int reg)) {
                    var nreg = registerDefinitionTracker.get(reg);
                    if(nreg == null || nreg == prevReg) {
                        return prevReg;
                    }
                    prevReg = nreg;
                }else {
                    return prevReg;
                }
            }
        }else {
            return value;
        }
    }

    /**
     * Copy propagation: {@code r := s} is removed and reads of {@code r} read {@code s} directly. {@code r} must be
     * written only by the copy, and every read of {@code r} must see the value {@code s} had at the copy (see
     * {@link #dropUnsafeCopies}).
     */
    private static List<BytecodeInstruction> cleanUpRegisterValues(List<BytecodeInstruction> instructions) {
        var writeCounts = new Int2IntOpenHashMap();
        var registerDefinitionTracker = new Int2ObjectOpenHashMap<BytecodeValue>();
        for(BytecodeInstruction instruction : instructions) {
            int written = writtenRegister(instruction);
            if(written >= 0) writeCounts.addTo(written, 1);
            if(instruction instanceof BytecodeInstruction.RegisterValue v) {
                registerDefinitionTracker.put(v.result().reg(), v.source());
            }else if(written >= 0) {
                registerDefinitionTracker.remove(written);
            }
        }

        for(var entry : writeCounts.int2IntEntrySet()) {
            if(entry.getIntValue() > 1) {
                registerDefinitionTracker.remove(entry.getIntKey());
            }
        }
        if(!registerDefinitionTracker.isEmpty()) dropUnsafeCopies(instructions, registerDefinitionTracker);

        ArrayList<BytecodeInstruction> newInstructions = new ArrayList<>();

        for(var instruction : instructions) {
            switch(instruction) {
                case BytecodeInstruction.RegisterValue(InstructionRVType type, BytecodeRegister result, BytecodeValue source) -> {
                    if(registerDefinitionTracker.containsKey(result.reg())) {
                        continue;
                    }
                    newInstructions.add(new BytecodeInstruction.RegisterValue(type, result, remapValue(registerDefinitionTracker, source)));
                }
                case BytecodeInstruction.RegisterValueValue(InstructionRVVType type, BytecodeRegister result, BytecodeValue sourceA, BytecodeValue sourceB) -> newInstructions.add(new BytecodeInstruction.RegisterValueValue(type, result, remapValue(registerDefinitionTracker, sourceA), remapValue(registerDefinitionTracker, sourceB)));
                case BytecodeInstruction.CreateArray(BytecodeRegister result, BytecodeValue length, ClassDesc contentType) -> newInstructions.add(new BytecodeInstruction.CreateArray(result, remapValue(registerDefinitionTracker, length), contentType));
                case BytecodeInstruction.SetArrayValue(BytecodeValue array, BytecodeValue index, BytecodeValue value) -> newInstructions.add(new BytecodeInstruction.SetArrayValue(remapValue(registerDefinitionTracker, array), remapValue(registerDefinitionTracker, index), remapValue(registerDefinitionTracker, value)));
                case BytecodeInstruction.InstanceCheckJump(boolean ifIsInstance, BytecodeValue a, ClassDesc type, BytecodeLabel label) -> newInstructions.add(new BytecodeInstruction.InstanceCheckJump(ifIsInstance, remapValue(registerDefinitionTracker, a), type, label));
                case BytecodeInstruction.ConditionalJump(JumpCondition condition, BytecodeValue a, BytecodeValue b, BytecodeLabel label) -> newInstructions.add(new BytecodeInstruction.ConditionalJump(condition, remapValue(registerDefinitionTracker, a), remapValue(registerDefinitionTracker, b), label));
                case BytecodeInstruction.Jump v -> newInstructions.add(v);
                case BytecodeInstruction.Label v -> newInstructions.add(v);
                case BytecodeInstruction.CallTarget(BytecodeRegister result, InstructionTarget target, List<BytecodeValue> arguments) -> {
                    ArrayList<BytecodeValue> args = new ArrayList<>();
                    for(var arg : arguments) {
                        args.add(remapValue(registerDefinitionTracker, arg));
                    }
                    newInstructions.add(new BytecodeInstruction.CallTarget(result, target, args));
                }
                case BytecodeInstruction.New(BytecodeRegister result, InstructionTarget target, List<BytecodeValue> arguments) -> {
                    ArrayList<BytecodeValue> args = new ArrayList<>();
                    for(var arg : arguments) {
                        args.add(remapValue(registerDefinitionTracker, arg));
                    }
                    newInstructions.add(new BytecodeInstruction.New(result, target, args));
                }
                case BytecodeInstruction.Cast(BytecodeRegister result, BytecodeValue source, ClassDesc type) -> newInstructions.add(new BytecodeInstruction.Cast(result, remapValue(registerDefinitionTracker, source), type));
                case BytecodeInstruction.StringConcat(BytecodeRegister result, List<BytecodeValue> values) -> {
                    ArrayList<BytecodeValue> remappedValues = new ArrayList<>();
                    for(var arg : values) {
                        remappedValues.add(remapValue(registerDefinitionTracker, arg));
                    }
                    newInstructions.add(new BytecodeInstruction.StringConcat(result, remappedValues));
                }
                case BytecodeInstruction.Return(BytecodeValue value) -> newInstructions.add(new BytecodeInstruction.Return(remapValue(registerDefinitionTracker, value)));
                case BytecodeInstruction.ReturnVoid v -> newInstructions.add(v);
            }
        }

        return newInstructions;
    }

    /**
     * Removes copies {@code r := s} from {@code copies} unless every read of {@code r} sees the value {@code s} had at
     * the copy. Scanning in order, a copy stays valid until {@code s} (or, for chains, a source further up) is written,
     * or until a label that control can reach without passing the copy since the last time it ran: the target of a
     * backward jump (a loop head), or a label jumped to from before the copy.
     */
    private static void dropUnsafeCopies(List<BytecodeInstruction> instructions, Int2ObjectOpenHashMap<BytecodeValue> copies) {
        // label -> smallest index of a jump to it; labels that are targets of backward jumps get -1 (kill everything)
        var labelIndex = new Object2IntOpenHashMap<BytecodeLabel>();
        for(int i = 0; i < instructions.size(); i++) {
            if(instructions.get(i) instanceof BytecodeInstruction.Label(BytecodeLabel label)) labelIndex.put(label, i);
        }
        var firstJumpSource = new Object2IntOpenHashMap<BytecodeLabel>();
        firstJumpSource.defaultReturnValue(Integer.MAX_VALUE);
        for(int i = 0; i < instructions.size(); i++) {
            BytecodeLabel target = switch(instructions.get(i)) {
                case BytecodeInstruction.Jump(BytecodeLabel label) -> label;
                case BytecodeInstruction.ConditionalJump v -> v.label();
                case BytecodeInstruction.InstanceCheckJump v -> v.label();
                default -> null;
            };
            if(target == null) continue;
            int source = labelIndex.getOrDefault(target, -1) <= i ? -1 : i;
            firstJumpSource.put(target, Math.min(firstJumpSource.getInt(target), source));
        }

        var alive = new Int2IntOpenHashMap();                          // live copy register -> index of the copy
        alive.defaultReturnValue(-1);
        var registerCopies = new IntArrayList();                       // register-sourced copies, ascending copy index
        var dependents = new Int2ObjectOpenHashMap<IntArrayList>();    // source register -> copies reading it
        var unsafe = new IntOpenHashSet();
        for(int i = 0; i < instructions.size(); i++) {
            var instruction = instructions.get(i);
            forEachRead(instruction, reg -> {
                if(copies.containsKey(reg) && !alive.containsKey(reg)) unsafe.add(reg);
            });
            if(instruction instanceof BytecodeInstruction.Label(BytecodeLabel label)) {
                // Register-sourced copies made after the first jump here are not valid on that jump's path.
                int firstSource = firstJumpSource.getInt(label);
                while(!registerCopies.isEmpty()) {
                    int reg = registerCopies.getInt(registerCopies.size() - 1);
                    int copyIndex = alive.get(reg);
                    if(copyIndex >= 0 && copyIndex <= firstSource) break;
                    alive.remove(reg);
                    registerCopies.popInt();
                }
                continue;
            }
            int written = writtenRegister(instruction);
            if(written < 0) continue;
            kill(written, alive, dependents);
            if(copies.containsKey(written)) {
                alive.put(written, i);
                if(copies.get(written) instanceof BytecodeRegister(int source)) {
                    dependents.computeIfAbsent(source, _ -> new IntArrayList()).add(written);
                    registerCopies.add(written);
                }
            }
        }
        for(var it = unsafe.iterator(); it.hasNext(); ) copies.remove(it.nextInt());
    }

    private static void kill(int register, Int2IntOpenHashMap alive, Int2ObjectOpenHashMap<IntArrayList> dependents) {
        var deps = dependents.remove(register);
        if(deps == null) return;
        for(int i = 0; i < deps.size(); i++) {
            int dep = deps.getInt(i);
            if(alive.remove(dep) >= 0) kill(dep, alive, dependents);
        }
    }

    private static BytecodeLabel getLabel(HashMap<BytecodeLabel, BytecodeLabel> labelMap, BytecodeLabel label) {
        HashSet<BytecodeLabel> alreadyFetched = new HashSet<>();
        while(true) {
            if(alreadyFetched.contains(label)) {
                throw new RuntimeException("Label " + label + " is circularly referenced");
            }
            alreadyFetched.add(label);
            var fetched = labelMap.getOrDefault(label, label);
            if(fetched.equals(label)) {
                return label;
            }
            label = fetched;
        }
    }

    private static List<BytecodeInstruction> fixJumps(List<BytecodeInstruction> instructions) {
        HashMap<BytecodeLabel, BytecodeLabel> instantJumps = new HashMap<>();
        var preLabelUseCount = new Object2IntOpenHashMap<BytecodeLabel>();

        for(int i = 0; i < instructions.size(); i++) {
            var instruction = instructions.get(i);
            switch(instruction) {
                case BytecodeInstruction.Jump(BytecodeLabel label) -> {
                    if(i != 0 && instructions.get(i - 1) instanceof BytecodeInstruction.Label(
                        BytecodeLabel prevLabel
                    )) {
                        instantJumps.put(prevLabel, label);
                    }
                    preLabelUseCount.put(label, preLabelUseCount.getOrDefault(label, 0) + 1);
                }
                case BytecodeInstruction.ConditionalJump v -> preLabelUseCount.put(v.label(), preLabelUseCount.getOrDefault(v.label(), 0) + 1);
                case BytecodeInstruction.InstanceCheckJump v -> preLabelUseCount.put(v.label(), preLabelUseCount.getOrDefault(v.label(), 0) + 1);
                case BytecodeInstruction.Label(BytecodeLabel label) -> {
                    if(i != 0 && instructions.get(i - 1) instanceof BytecodeInstruction.Label(
                        BytecodeLabel prevLabel
                    )) {
                        instantJumps.put(prevLabel, label);
                    }
                }
                case BytecodeInstruction.CallTarget _, BytecodeInstruction.Cast _, BytecodeInstruction.CreateArray _,
                     BytecodeInstruction.New _, BytecodeInstruction.RegisterValue _,
                     BytecodeInstruction.RegisterValueValue _, BytecodeInstruction.Return _,
                     BytecodeInstruction.ReturnVoid _, BytecodeInstruction.SetArrayValue _,
                     BytecodeInstruction.StringConcat _ -> {}
            }
        }
        var labelUseCount = new Object2IntOpenHashMap<BytecodeLabel>();
        for(var entry : preLabelUseCount.object2IntEntrySet()) {
            var remappedLabel = getLabel(instantJumps, entry.getKey());
            labelUseCount.put(remappedLabel, labelUseCount.getOrDefault(remappedLabel, 0) + entry.getIntValue());
        }
        ArrayList<BytecodeInstruction> newInstructions = new ArrayList<>();
        var index = -1;
        for(var instruction : instructions) {
            index++;
            switch(instruction) {
                case BytecodeInstruction.Jump(BytecodeLabel label) -> {
                    var mappedLabel = getLabel(instantJumps, label);
                    newInstructions.add(new BytecodeInstruction.Jump(mappedLabel));
                    continue;
                }
                case BytecodeInstruction.ConditionalJump(JumpCondition condition, BytecodeValue a1, BytecodeValue b, BytecodeLabel label) -> {
                    var mappedLabel = getLabel(instantJumps, label);
                    newInstructions.add(new BytecodeInstruction.ConditionalJump(condition, a1, b, mappedLabel));
                    continue;
                }
                case BytecodeInstruction.InstanceCheckJump(boolean ifIsInstance, BytecodeValue a, ClassDesc type, BytecodeLabel label) -> {
                    var mappedLabel = getLabel(instantJumps, label);
                    newInstructions.add(new BytecodeInstruction.InstanceCheckJump(ifIsInstance, a, type, mappedLabel));
                    continue;
                }
                case BytecodeInstruction.Label(BytecodeLabel label) -> {
                    var nextInstruction = instructions.get(index + 1);
                    if(nextInstruction instanceof BytecodeInstruction.Label) {
                        continue;
                    }

                    var mappedLabel = getLabel(instantJumps, label);
                    if(labelUseCount.getOrDefault(mappedLabel, 0) == 0) {
                        continue;
                    }
                }
                case BytecodeInstruction.CallTarget _, BytecodeInstruction.Cast _, BytecodeInstruction.CreateArray _,
                     BytecodeInstruction.New _, BytecodeInstruction.RegisterValue _,
                     BytecodeInstruction.RegisterValueValue _, BytecodeInstruction.Return _,
                     BytecodeInstruction.ReturnVoid _, BytecodeInstruction.SetArrayValue _,
                     BytecodeInstruction.StringConcat _ -> {}
            }

            newInstructions.add(instruction);
        }
        return newInstructions;
    }

    public static List<BytecodeInstruction> optimizeInstructions(List<BytecodeInstruction> instructions) {
        List<BytecodeInstruction> results = instructions;
        while(true) {
            results = cleanUpRegisterValues(fixJumps(results));
            if(results.equals(instructions)) {
                break;
            }
            instructions = results;
        }
        return results;
    }

}

package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.*;

public class BytecodeOptimizer {

    private static final class RegisterTracker {
        private final HashSet<BytecodeValue> readWith = new HashSet<>();
        private int read = 0;
        private int write = 0;
        private boolean requiresEvaluation = false;
    }

    private static void countRegisterRead(Int2ObjectMap<RegisterTracker> registerUseCount, BytecodeValue value) {
        countRegisterRead(registerUseCount, value, null);
    }

    private static void countRegisterRead(Int2ObjectMap<RegisterTracker> registerUseCount, BytecodeValue value, @Nullable BytecodeValue by) {
        if(value instanceof BytecodeRegister(int reg)) {
            var tracker = registerUseCount.computeIfAbsent(reg, _ -> new RegisterTracker());
            if(by != null) {
                tracker.readWith.add(by);
            }
            tracker.read++;
        }
    }

    private static void countRegisterWrite(Int2ObjectMap<RegisterTracker> registerUseCount, BytecodeValue value, boolean requiresEvaluation) {
        if(value instanceof BytecodeRegister(int reg)) {
            var tracker = registerUseCount.computeIfAbsent(reg, _ -> new RegisterTracker());
            tracker.write++;
            tracker.requiresEvaluation |= requiresEvaluation;
        }
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

    // merges registers and does dead code elimination on code that uses registers that are not used
    private static List<BytecodeInstruction> cleanUpRegisterValues(List<BytecodeInstruction> instructions) {
        var registerUseCount = new Int2ObjectOpenHashMap<RegisterTracker>();
        var registerDefinitionTracker = new Int2ObjectOpenHashMap<BytecodeValue>();
        for(BytecodeInstruction instruction : instructions) {
            switch(instruction) {
                case BytecodeInstruction.RegisterValue v -> {
                    countRegisterWrite(registerUseCount, v.result(), false);
                    countRegisterRead(registerUseCount, v.source(), v.result());
                    registerDefinitionTracker.put(v.result().reg(), v.source());
                }
                case BytecodeInstruction.RegisterValueValue v -> {
                    countRegisterWrite(registerUseCount, v.result(), false);
                    countRegisterRead(registerUseCount, v.sourceA(), v.result());
                    countRegisterRead(registerUseCount, v.sourceB(), v.result());
                    registerDefinitionTracker.remove(v.result().reg());
                }
                case BytecodeInstruction.CreateArray v -> {
                    countRegisterWrite(registerUseCount, v.result(), false);
                    countRegisterRead(registerUseCount, v.length(), v.result());
                    registerDefinitionTracker.remove(v.result().reg());
                }
                case BytecodeInstruction.SetArrayValue(BytecodeValue array, BytecodeValue index, BytecodeValue value) -> {
                    countRegisterRead(registerUseCount, array);
                    countRegisterRead(registerUseCount, value);
                    countRegisterRead(registerUseCount, index);
                }
                case BytecodeInstruction.InstanceCheckJump v -> countRegisterRead(registerUseCount, v.a());
                case BytecodeInstruction.ConditionalJump v -> {
                    countRegisterRead(registerUseCount, v.a());
                    countRegisterRead(registerUseCount, v.b());
                }
                case BytecodeInstruction.CallTarget v -> {
                    countRegisterWrite(registerUseCount, v.result(), true);
                    for(var arg : v.arguments()) {
                        countRegisterRead(registerUseCount, arg, v.result());
                    }
                    registerDefinitionTracker.remove(v.result().reg());
                }
                case BytecodeInstruction.New v -> {
                    countRegisterWrite(registerUseCount, v.result(), true);
                    for(var arg : v.arguments()) {
                        countRegisterRead(registerUseCount, arg, v.result());
                    }
                    registerDefinitionTracker.remove(v.result().reg());
                }
                case BytecodeInstruction.Cast v -> {
                    countRegisterWrite(registerUseCount, v.result(), false);
                    countRegisterRead(registerUseCount, v.source(), v.result());
                    registerDefinitionTracker.remove(v.result().reg());
                }
                case BytecodeInstruction.StringConcat(BytecodeRegister result, List<BytecodeValue> values) -> {
                    countRegisterWrite(registerUseCount, result, false);
                    for(var arg : values) {
                        countRegisterRead(registerUseCount, arg, result);
                    }
                    registerDefinitionTracker.remove(result.reg());
                }
                case BytecodeInstruction.Return(BytecodeValue value) -> countRegisterRead(registerUseCount, value);
                case BytecodeInstruction.Jump _, BytecodeInstruction.Label _, BytecodeInstruction.ReturnVoid _ -> {}
            }
        }

        for(var entry : registerUseCount.int2ObjectEntrySet()) {
            if(entry.getValue().write > 1) {
                registerDefinitionTracker.remove(entry.getIntKey());
            }
        }

        var markedEvaluated = new IntOpenHashSet();
        var markEvaluationStack = new IntArrayList();
        for(var entry : registerUseCount.int2ObjectEntrySet()) {
            if(entry.getValue().requiresEvaluation || entry.getValue().read != entry.getValue().readWith.size()) {
                markEvaluationStack.push(entry.getIntKey());
            }
        }
        while(!markEvaluationStack.isEmpty()) {
            var reg = markEvaluationStack.popInt();
            if(!markedEvaluated.add(reg)) {
                continue;
            }
            var tracker = registerUseCount.get(reg);
            tracker.requiresEvaluation = true;
            for(var read : tracker.readWith) {
                if(read instanceof BytecodeRegister(int readReg)) {
                    markEvaluationStack.push(readReg);
                }
            }
        }

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

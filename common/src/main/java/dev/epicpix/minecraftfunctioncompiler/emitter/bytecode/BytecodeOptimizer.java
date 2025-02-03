package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

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

    private static void countRegisterRead(Int2ObjectMap<RegisterTracker> registerUseCount, BytecodeValue value, BytecodeValue by) {
        if(value instanceof BytecodeRegister reg) {
            var tracker = registerUseCount.computeIfAbsent(reg.reg(), x -> new RegisterTracker());
            if(by != null) {
                tracker.readWith.add(by);
            }
            tracker.read++;
        }
    }

    private static void countRegisterWrite(Int2ObjectMap<RegisterTracker> registerUseCount, BytecodeValue value, boolean requiresEvaluation) {
        if(value instanceof BytecodeRegister reg) {
            var tracker = registerUseCount.computeIfAbsent(reg.reg(), x -> new RegisterTracker());
            tracker.write++;
            tracker.requiresEvaluation |= requiresEvaluation;
        }
    }

    private static BytecodeValue remapValue(Int2ObjectMap<BytecodeValue> registerDefinitionTracker, BytecodeValue value) {
        if(value instanceof BytecodeRegister reg) {
            BytecodeValue prevReg = reg;
            while(true) {
                if(prevReg instanceof BytecodeRegister pReg) {
                    var nreg = registerDefinitionTracker.get(pReg.reg());
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
            if(instruction instanceof BytecodeInstruction.RegisterValue v) {
                countRegisterWrite(registerUseCount, v.result(), false);
                countRegisterRead(registerUseCount, v.source(), v.result());
                registerDefinitionTracker.put(v.result().reg(), v.source());
            }else if(instruction instanceof BytecodeInstruction.RegisterValueValue v) {
                countRegisterWrite(registerUseCount, v.result(), false);
                countRegisterRead(registerUseCount, v.sourceA(), v.result());
                countRegisterRead(registerUseCount, v.sourceB(), v.result());
                registerDefinitionTracker.remove(v.result().reg());
            }else if(instruction instanceof BytecodeInstruction.CreateArray v) {
                countRegisterWrite(registerUseCount, v.result(), false);
                countRegisterRead(registerUseCount, v.length(), v.result());
                registerDefinitionTracker.remove(v.result().reg());
            }else if(instruction instanceof BytecodeInstruction.SetArrayValue v) {
                countRegisterRead(registerUseCount, v.array());
                countRegisterRead(registerUseCount, v.value());
                countRegisterRead(registerUseCount, v.index());
            }else if(instruction instanceof BytecodeInstruction.InstanceCheckJump v) {
                countRegisterRead(registerUseCount, v.a());
            }else if(instruction instanceof BytecodeInstruction.ConditionalJump v) {
                countRegisterRead(registerUseCount, v.a());
                countRegisterRead(registerUseCount, v.b());
            }else if(instruction instanceof BytecodeInstruction.CallTarget v) {
                countRegisterWrite(registerUseCount, v.result(), true);
                for(var arg : v.arguments()) {
                    countRegisterRead(registerUseCount, arg, v.result());
                }
                registerDefinitionTracker.remove(v.result().reg());
            }else if(instruction instanceof BytecodeInstruction.New v) {
                countRegisterWrite(registerUseCount, v.result(), true);
                for(var arg : v.arguments()) {
                    countRegisterRead(registerUseCount, arg, v.result());
                }
                registerDefinitionTracker.remove(v.result().reg());
            }else if(instruction instanceof BytecodeInstruction.Cast v) {
                countRegisterWrite(registerUseCount, v.result(), false);
                countRegisterRead(registerUseCount, v.source(), v.result());
                registerDefinitionTracker.remove(v.result().reg());
            }else if(instruction instanceof BytecodeInstruction.StringConcat v) {
                countRegisterWrite(registerUseCount, v.result(), false);
                for(var arg : v.values()) {
                    countRegisterRead(registerUseCount, arg, v.result());
                }
                registerDefinitionTracker.remove(v.result().reg());
            }else if(instruction instanceof BytecodeInstruction.Return v) {
                countRegisterRead(registerUseCount, v.value());
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
                if(read instanceof BytecodeRegister mark) {
                    markEvaluationStack.push(mark.reg());
                }
            }
        }

        ArrayList<BytecodeInstruction> newInstructions = new ArrayList<>();

        for(var instruction : instructions) {
            if(instruction instanceof BytecodeInstruction.RegisterValue v) {
                if(registerDefinitionTracker.containsKey(v.result().reg())) {
                    continue;
                }
                newInstructions.add(new BytecodeInstruction.RegisterValue(v.type(), v.result(), remapValue(registerDefinitionTracker, v.source())));
            }else if(instruction instanceof BytecodeInstruction.RegisterValueValue v) {
                newInstructions.add(new BytecodeInstruction.RegisterValueValue(v.type(), v.result(), remapValue(registerDefinitionTracker, v.sourceA()), remapValue(registerDefinitionTracker, v.sourceB())));
            }else if(instruction instanceof BytecodeInstruction.CreateArray v) {
                newInstructions.add(new BytecodeInstruction.CreateArray(v.result(), remapValue(registerDefinitionTracker, v.length()), v.contentType()));
            }else if(instruction instanceof BytecodeInstruction.SetArrayValue v) {
                newInstructions.add(new BytecodeInstruction.SetArrayValue(remapValue(registerDefinitionTracker, v.array()), remapValue(registerDefinitionTracker, v.index()), remapValue(registerDefinitionTracker, v.value())));
            }else if(instruction instanceof BytecodeInstruction.InstanceCheckJump v) {
                newInstructions.add(new BytecodeInstruction.InstanceCheckJump(v.ifIsInstance(), remapValue(registerDefinitionTracker, v.a()), v.type(), v.label()));
            }else if(instruction instanceof BytecodeInstruction.ConditionalJump v) {
                newInstructions.add(new BytecodeInstruction.ConditionalJump(v.condition(), remapValue(registerDefinitionTracker, v.a()), remapValue(registerDefinitionTracker, v.b()), v.label()));
            }else if(instruction instanceof BytecodeInstruction.Jump v) {
                newInstructions.add(v);
            }else if(instruction instanceof BytecodeInstruction.Label v) {
                newInstructions.add(v);
            }else if(instruction instanceof BytecodeInstruction.CallTarget v) {
                ArrayList<BytecodeValue> args = new ArrayList<>();
                for(var arg : v.arguments()) {
                    args.add(remapValue(registerDefinitionTracker, arg));
                }
                newInstructions.add(new BytecodeInstruction.CallTarget(v.result(), v.target(), args));
            }else if(instruction instanceof BytecodeInstruction.New v) {
                ArrayList<BytecodeValue> args = new ArrayList<>();
                for(var arg : v.arguments()) {
                    args.add(remapValue(registerDefinitionTracker, arg));
                }
                newInstructions.add(new BytecodeInstruction.New(v.result(), v.target(), args));
            }else if(instruction instanceof BytecodeInstruction.Cast v) {
                newInstructions.add(new BytecodeInstruction.Cast(v.result(), remapValue(registerDefinitionTracker, v.source()), v.type()));
            }else if(instruction instanceof BytecodeInstruction.StringConcat v) {
                ArrayList<BytecodeValue> values = new ArrayList<>();
                for(var arg : v.values()) {
                    values.add(remapValue(registerDefinitionTracker, arg));
                }
                newInstructions.add(new BytecodeInstruction.StringConcat(v.result(), values));
            }else if(instruction instanceof BytecodeInstruction.Return v) {
                newInstructions.add(new BytecodeInstruction.Return(remapValue(registerDefinitionTracker, v.value())));
            }else if(instruction instanceof BytecodeInstruction.ReturnVoid v) {
                newInstructions.add(v);
            }else {
                throw new RuntimeException("Cannot remap instruction " + instruction);
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
            if(instruction instanceof BytecodeInstruction.Jump v) {
                if(i != 0 && instructions.get(i - 1) instanceof BytecodeInstruction.Label label) {
                    instantJumps.put(label.label(), v.label());
                }
                preLabelUseCount.put(v.label(), preLabelUseCount.getOrDefault(v.label(), 0) + 1);
            }else if(instruction instanceof BytecodeInstruction.ConditionalJump v) {
                preLabelUseCount.put(v.label(), preLabelUseCount.getOrDefault(v.label(), 0) + 1);
            }else if(instruction instanceof BytecodeInstruction.InstanceCheckJump v) {
                preLabelUseCount.put(v.label(), preLabelUseCount.getOrDefault(v.label(), 0) + 1);
            }else if(instruction instanceof BytecodeInstruction.Label v) {
                if(i != 0 && instructions.get(i - 1) instanceof BytecodeInstruction.Label label) {
                    instantJumps.put(label.label(), v.label());
                }
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
            if(instruction instanceof BytecodeInstruction.Jump v) {
                var label = getLabel(instantJumps, v.label());
                newInstructions.add(new BytecodeInstruction.Jump(label));
                continue;
            }else if(instruction instanceof BytecodeInstruction.ConditionalJump v) {
                var label = getLabel(instantJumps, v.label());
                newInstructions.add(new BytecodeInstruction.ConditionalJump(v.condition(), v.a(), v.b(), label));
                continue;
            }else if(instruction instanceof BytecodeInstruction.InstanceCheckJump v) {
                var label = getLabel(instantJumps, v.label());
                newInstructions.add(new BytecodeInstruction.InstanceCheckJump(v.ifIsInstance(), v.a(), v.type(), label));
                continue;
            }else if(instruction instanceof BytecodeInstruction.Label v) {
                var nextInstruction = instructions.get(index + 1);
                if(nextInstruction instanceof BytecodeInstruction.Label) {
                    continue;
                }

                var label = getLabel(instantJumps, v.label());
                if(labelUseCount.getOrDefault(label, 0) == 0) {
                    continue;
                }
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

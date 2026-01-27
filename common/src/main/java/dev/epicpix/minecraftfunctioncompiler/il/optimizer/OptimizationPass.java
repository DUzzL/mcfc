package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes;
import dev.epicpix.minecraftfunctioncompiler.il.LabelData;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.*;

import java.util.*;

public final class OptimizationPass {

    private int resetIndex = Integer.MAX_VALUE;
    private int thisInstruction;

    private final OptimizationContext context;

    public OptimizationPass(int thisInstruction, OptimizationContext context) {
        this.thisInstruction = thisInstruction;
        this.context = context;
    }

    public int thisInstructionIndex() {
        return thisInstruction;
    }

    public Instruction getInstruction(int index) {
        return context.getInstruction(index);
    }

    public Instruction thisInstruction() {
        return getInstruction(thisInstruction);
    }

    public Instruction getOffsetInstruction(int offset) {
        return getInstruction(thisInstruction + offset);
    }

    public boolean isValueUsed(int start, int end, boolean includeSelf, Object value) {
        for(int i = start; i<end; i++) {
            if(!includeSelf && i == thisInstruction) continue;
            var instr = getInstruction(i);
            if(instr.containsValue(value)) {
                return true;
            }
        }
        return false;
    }

    public IntList findInstructions(int start, int end, boolean includeSelf, List<InstructionType> types, List<Pair<String, Object>> dataPairs) {
        var results = new IntArrayList();
        next: for(int i = start; i < end; i++) {
            if(i == thisInstruction && !includeSelf) continue;
            var instruction = getInstruction(i);
            for(var type : types) {
                if(instruction.type() == type) {
                    for(var data : dataPairs) {
                        if(!instruction.data()[instruction.type().getArgumentIndex(data.first())].equals(data.second())) {
                            continue next;
                        }
                    }
                    results.add(i);
                }
            }
        }
        return results;
    }

    public IntList findInstructions(int start, int end, boolean includeSelf, InstructionType type, List<Pair<String, Object>> dataPairs) {
        var results = new IntArrayList();
        next: for(int i = start; i < end; i++) {
            if(i == thisInstruction && !includeSelf) continue;
            var instruction = getInstruction(i);
            if(instruction.type() == type) {
                for(var data : dataPairs) {
                    if(!Objects.equals(instruction.data()[type.getArgumentIndex(data.first())], data.second())) {
                        continue next;
                    }
                }
                results.add(i);
            }
        }
        return results;
    }

    public Integer findSingleInstruction(int start, int end, boolean includeSelf, InstructionType type, List<Pair<String, Object>> dataPairs) {
        var instrs = findInstructions(start, end, includeSelf, type, dataPairs);
        if(instrs.size() != 1) return null;
        return instrs.getInt(0);
    }

    public IntList findInstructionsIn(IntList instructions, boolean includeSelf, InstructionType type, List<Pair<String, Object>> dataPairs) {
        var results = new IntArrayList();
        next:
        for(IntIterator it = instructions.intIterator(); it.hasNext(); ) {
            var i = it.nextInt();
            if(i == thisInstruction && !includeSelf) continue;
            var instruction = getInstruction(i);
            if(instruction.type() == type) {
                for(var data : dataPairs) {
                    if(!Objects.equals(instruction.data()[type.getArgumentIndex(data.first())], data.second())) {
                        continue next;
                    }
                }
                results.add(i);
            }
        }
        return results;
    }

    public void generateInstructionEdges(Int2ObjectMap<IntSet> forward, Int2ObjectMap<IntSet> backward) {
        var labelLocations = new Int2IntOpenHashMap();
        for(int i = 0; i<context.getInstructionCount(); i++) {
            var instruction = getInstruction(i);
            if(instruction.type() == InstructionTypes.LABEL) {
                labelLocations.put(instruction.<LabelData>get(0).id(), i);
            }
            if(backward != null) backward.put(i, new IntLinkedOpenHashSet(2));
        }

        for(int i = 0; i<context.getInstructionCount(); i++) {
            var set = new IntLinkedOpenHashSet(2);
            var instruction = getInstruction(i);

            if(instruction.type().isConditionJump() || instruction.type() == InstructionTypes.JUMP) {
                for(var value : instruction.data()) {
                    if(value instanceof LabelData(int id)) {
                        var loc = labelLocations.get(id);
                        set.add(loc);
                        if(backward != null) backward.get(loc).add(i);
                    }
                }
            }
            if(instruction.type() != InstructionTypes.JUMP && instruction.type() != InstructionTypes.RETURN_SUCCESS && instruction.type() != InstructionTypes.RETURN_FAIL && i != context.getInstructionCount() - 1) {
                set.add(i + 1);
                if(backward != null) backward.get(i + 1).add(i);
            }
            forward.put(i, set);
        }
    }

    public IntList findExecutedInstructions() {
        return findExecutedInstructionsUntil(thisInstruction);
    }

    public IntList findExecutedInstructionsUntil(int index) {
        if(shouldEvaluateNewInstructionPaths) {
            var forward = new Int2ObjectOpenHashMap<IntSet>(context.getInstructionCount());
            var backward = new Int2ObjectOpenHashMap<IntSet>(context.getInstructionCount());
            generateInstructionEdges(forward, backward);
            context.tarjan.updateData(forward, backward);
            shouldEvaluateNewInstructionPaths = false;
        }
        return context.tarjan.getDominators(index);
    }

    public void replaceInstructionData(boolean includeSelf, Object from, Object to) {
        for(int i = 0; i<context.getInstructionCount(); i++) {
            if(!includeSelf && i == thisInstruction) continue;
            var instr = getInstruction(i);
            var replacedInstructionData = instr.optReplaceData(from, to);
            if(replacedInstructionData != null) {
                modifyInstructionData(i, replacedInstructionData);
            }
        }
    }

    public OptimizationContext context() {
        return context;
    }

    private boolean shouldEvaluateNewInstructionPaths = true;

    public void addInstruction(int index, Instruction instruction) {
        context.instructions.add(index, instruction);
        resetIndex = Math.min(resetIndex, index);
        shouldEvaluateNewInstructionPaths = true;
    }

    public void removeInstruction(int index) {
        context.instructions.remove(index);
        resetIndex = Math.min(resetIndex, index);
        shouldEvaluateNewInstructionPaths = true;
    }

    public void replaceInstruction(int index, Instruction instruction) {
        var prev = context.instructions.set(index, instruction);
        resetIndex = Math.min(resetIndex, index);
        var oldIsEndingInstruction = prev.type() == InstructionTypes.JUMP || prev.type() == InstructionTypes.RETURN_SUCCESS || prev.type() == InstructionTypes.RETURN_FAIL;
        var newIsEndingInstruction = instruction.type() == InstructionTypes.JUMP || instruction.type() == InstructionTypes.RETURN_SUCCESS || instruction.type() == InstructionTypes.RETURN_FAIL;
        if(oldIsEndingInstruction || newIsEndingInstruction || prev.type().isConditionJump() || instruction.type().isConditionJump()) {
            if(!shouldEvaluateNewInstructionPaths && prev.type() == instruction.type()) {
                var oldData = prev.data();
                var newData = instruction.data();
                for(int i = 0; i<newData.length; i++) {
                    if(oldData[i] == newData[i]) continue;
                    if(oldData[i] instanceof LabelData || newData[i] instanceof LabelData) {
                        shouldEvaluateNewInstructionPaths = true;
                        return;
                    }
                }
            }
            shouldEvaluateNewInstructionPaths = true;
        }
    }

    public void modifyInstructionData(int index, Object[] instructionData) {
        var instr = context.instructions.get(index);
        replaceInstruction(index, new Instruction(instr.type(), instructionData));
        resetIndex = Math.min(resetIndex, index);
    }

    public int takeResetIndex() {
        int index = resetIndex;
        resetIndex = Integer.MAX_VALUE;
        return index;
    }

    void setThisInstruction(int index) {
        thisInstruction = index;
    }

}

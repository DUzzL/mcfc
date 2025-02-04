package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
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
                if(instruction.type().equals(type)) {
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
            if(instruction.type().equals(type)) {
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
            if(instruction.type().equals(type)) {
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

    public int[] findExecutedInstructions() {
        return findExecutedInstructionsUntil(thisInstruction);
    }

    public int[] findExecutedInstructionsUntil(int index) {
        var res = findExecutedInstructionsUntil(index, new Int2ObjectOpenHashMap<>(), new IntArrayList());
        if(res == null) return new int[0];
        return res.intStream().sorted().toArray();
    }

    private IntSet findExecutedInstructionsUntil(int index, Int2ObjectMap<IntSet> cache, IntArrayList currentStack) {
        var cached = cache.get(index);
        if(cached != null) return cached;
        var data = findExecutedInstructionsUntilInternal(index);
        if(data.incomplete) {
            currentStack.push(index);
            var prevInstrs = getPreviousInstructions(data.finishIndex);
            IntSet results = null;
            for(var it = prevInstrs.intIterator(); it.hasNext(); ) {
                var p = it.nextInt();
                if(currentStack.contains(p)) {
                    continue;
                }
                var sub = findExecutedInstructionsUntil(p, cache, currentStack);
                if(sub == null) continue;
                if(results == null) {
                    results = new IntOpenHashSet(sub);
                }else {
                    results.retainAll(sub);
                }
                if(results.isEmpty()) break;
            }
            currentStack.popInt();
            if(results == null) {
                return null;
            }
            if(data.partExecuted) {
                results.addAll(data.part);
            }
            var res = results;
            cache.put(index, res);
            return res;
        }else {
            if(!data.partExecuted) {
                cache.put(index, null);
                return null;
            }
            cache.put(index, data.part);
            return data.part;
        }
    }

    private boolean recomputeLabelToInstructions = true;
    private final Int2ObjectMap<IntList> labelToInstructions = new Int2ObjectOpenHashMap<>();

    private IntList getLabelToInstructions(LabelData label) {
        if(recomputeLabelToInstructions) {
            labelToInstructions.clear();
            for(int i = 0; i<context.getInstructionCount(); i++) {
                var instr = context.getInstruction(i);
                for(var data : instr.data()) {
                    if(data instanceof LabelData lData) {
                        labelToInstructions.computeIfAbsent(lData.id(), k -> new IntArrayList()).add(i);
                    }
                }
            }
            recomputeLabelToInstructions = false;
        }
        return labelToInstructions.get(label.id());
    }

    private IntList getPreviousInstructions(int finishIndex) {
        var instr = getInstruction(finishIndex);
        var label = instr.<LabelData>get("label");
        var prevInstrs = new IntArrayList();
        prevInstrs.add(finishIndex - 1);
        prevInstrs.addAll(getLabelToInstructions(label));
        return prevInstrs;
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

    private record ExecutedInstructionResults(IntSet part, boolean partExecuted, int finishIndex, boolean incomplete) {}

    private ExecutedInstructionResults findExecutedInstructionsUntilInternal(int index) {
        var executed = new IntOpenHashSet();
        int i = index;
        while(i >= 0) {
            var instr = getInstruction(i);
            if(instr.type() == InstructionTypes2.RETURN_SUCCESS || instr.type() == InstructionTypes2.RETURN_FAIL || instr.type() == InstructionTypes2.JUMP) {
                return new ExecutedInstructionResults(executed, false, i, false);
            }
            executed.add(i);
            if(instr.type() == InstructionTypes2.LABEL) {
                break;
            }
            i--;
        }
        return new ExecutedInstructionResults(executed, true, i, i != -1);
    }


    public OptimizationContext context() {
        return context;
    }

    public void addInstruction(int index, Instruction instruction) {
        context.instructions.add(index, instruction);
        resetIndex = Math.min(resetIndex, index);
        recomputeLabelToInstructions = true;
    }

    public void removeInstruction(int index) {
        context.instructions.remove(index);
        resetIndex = Math.min(resetIndex, index);
        recomputeLabelToInstructions = true;
    }

    public void replaceInstruction(int index, Instruction instruction) {
        context.instructions.set(index, instruction);
        resetIndex = Math.min(resetIndex, index);
        recomputeLabelToInstructions = true;
    }

    public void modifyInstructionData(int index, Object[] instructionData) {
        var instr = context.instructions.get(index);
        replaceInstruction(index, new Instruction(instr.type(), instructionData));
        resetIndex = Math.min(resetIndex, index);
        recomputeLabelToInstructions = true;
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

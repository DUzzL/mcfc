package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionType;
import dev.epicpix.minecraftfunctioncompiler.il.InstructionTypes2;
import dev.epicpix.minecraftfunctioncompiler.util.Pair;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    public List<Integer> findInstructions(int start, int end, boolean includeSelf, List<InstructionType> types, List<Pair<String, Object>> dataPairs) {
        var results = new ArrayList<Integer>();
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

    public List<Integer> findInstructions(int start, int end, boolean includeSelf, InstructionType type, List<Pair<String, Object>> dataPairs) {
        var results = new ArrayList<Integer>();
        next: for(int i = start; i < end; i++) {
            if(i == thisInstruction && !includeSelf) continue;
            var instruction = getInstruction(i);
            if(instruction.type().equals(type)) {
                for(var data : dataPairs) {
                    if(!instruction.data()[type.getArgumentIndex(data.first())].equals(data.second())) {
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
        return instrs.get(0);
    }

    public List<Integer> findInstructionsIn(List<Integer> instructions, boolean includeSelf, InstructionType type, List<Pair<String, Object>> dataPairs) {
        var results = new ArrayList<Integer>();
        next: for(var i : instructions) {
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

    public List<Integer> findExecutedInstructions() {
        return findExecutedInstructionsUntil(thisInstruction);
    }

    public List<Integer> findExecutedInstructionsUntil(int index) {
        var res = findExecutedInstructionsUntil(index, new HashMap<>(), new Stack<>());
        if(res == null) return List.of();
        return res.stream().sorted().collect(Collectors.toList());
    }

    private HashSet<Integer> findExecutedInstructionsUntil(int index, HashMap<Integer, HashSet<Integer>> cache, Stack<Integer> currentStack) {
        var cached = cache.get(index);
        if(cached != null) return cached;
        var data = findExecutedInstructionsUntilInternal(index);
        if(data.incomplete) {
            currentStack.push(index);
            var instr = getInstruction(data.finishIndex);
            var label = instr.get("label");
            var prevInstrs = new ArrayList<Integer>();
            prevInstrs.add(data.finishIndex - 1);
            for(int i = 0; i<context.getInstructionCount(); i++) {
                if(i == data.finishIndex) continue;
                if(getInstruction(i).containsValue(label)) {
                    prevInstrs.add(i);
                }
            }
            HashSet<Integer> results = null;
            for(var p : prevInstrs) {
                if(currentStack.contains(p)) {
                    continue;
                }
                var data2 = findExecutedInstructionsUntil(p, cache, currentStack);
                if(data2 == null) continue;
                if(results == null) {
                    results = new HashSet<>(data2);
                }else {
                    results.retainAll(data2);
                }
            }
            currentStack.pop();
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
                return null;
            }
            cache.put(index, data.part);
            return data.part;
        }
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

    private record ExecutedInstructionResults(HashSet<Integer> part, boolean partExecuted, int finishIndex, boolean incomplete) {}

    private ExecutedInstructionResults findExecutedInstructionsUntilInternal(int index) {
        HashSet<Integer> executed = new HashSet<>();
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
    }

    public void removeInstruction(int index) {
        context.instructions.remove(index);
        resetIndex = Math.min(resetIndex, index);
    }

    public void replaceInstruction(int index, Instruction instruction) {
        context.instructions.set(index, instruction);
        resetIndex = Math.min(resetIndex, index);
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

package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;
import dev.epicpix.minecraftfunctioncompiler.il.ScopeData;

import java.util.*;

public final class Optimizer {
    private Optimizer() {}

    public static List<Instruction> optimize(List<Instruction> instructions, ArrayList<OptimizationStatistics> statistics) {
        statistics.add(new OptimizationStatistics("start", Instruction.countInstructions(instructions), 0.0));
        var start = System.nanoTime();

        List<Instruction> prev;
        do {
            prev = instructions;
            var startOptimization = System.nanoTime();
            instructions = runOptimizations(instructions);
            var endOptimization = System.nanoTime();
            statistics.add(new OptimizationStatistics("optimization_pass", Instruction.countInstructions(instructions), (endOptimization - startOptimization)/1000000000.0));
        } while(!prev.equals(instructions));

        var end = System.nanoTime();
        statistics.add(new OptimizationStatistics("end", Instruction.countInstructions(instructions), (end - start)/1000000000.0));
        return instructions;
    }

    private static List<Instruction> runOptimizations(List<Instruction> instructions) {
        for(var optimization : Optimizations.OPTIMIZATIONS) {
            instructions = applyOptimization(instructions, optimization);
        }
        return instructions;
    }

    private static List<Instruction> applyOptimization(List<Instruction> instructions, Optimization optimization) {
        ArrayList<Instruction> result = new ArrayList<>();
        for(var instr : instructions) {
            result.addAll(applyOptimizationInner(instr, List.of(), instr, Set.of(), optimization));
        }
        return result;
    }

    private static List<Instruction> applyOptimizationInner(Instruction input, List<Integer> path, Instruction root, Set<Integer> usedLocations, Optimization optimization) {
        ArrayList<Instruction> newChildren = new ArrayList<>();
        usedLocations = new HashSet<>(usedLocations);
        for(var value : input.data().values()) {
            if(value instanceof LocationData<?> loc && loc.local() != null) {
                usedLocations.add(loc.local());
            }else if(value instanceof ScopeData scope) {
                usedLocations.add(scope.id());
            }
        }
        int index = 0;
        for(var child : input.children()) {
            newChildren.addAll(applyOptimizationInner(child, OptimizationInput.addPathIndex(path, index), root, Set.copyOf(usedLocations), optimization));
            index++;
        }
        var preNew = new Instruction(input.type(), input.data(), newChildren);
        var result = optimization.runOptimization(new OptimizationInput(preNew, path, root));
        if(result.isFlagSet(OptimizationResult.REPLACE_SELF_DATA)) {
            preNew = new Instruction(preNew.type(), Map.copyOf(result.newData()), preNew.children());
        }
        if(result.isFlagSet(OptimizationResult.REPLACE_SELF_TYPE)) {
            preNew = new Instruction(result.newType(), preNew.data(), preNew.children());
        }
        if(result.isFlagSet(OptimizationResult.REMAP_VALUES)) {
            ArrayList<Instruction> children = new ArrayList<>();
            for(var child : newChildren) {
                children.add(remapInstruction(child, result.remap()));
            }
            newChildren = children;
        }
        if(result.isFlagSet(OptimizationResult.MODIFY_CHILDREN)) {
            var modifications = new ArrayList<>(result.modifications());
            modifications.sort((a, b) -> {
                int priority = Integer.compare(a.priority(), b.priority());
                if(priority != 0) return -priority;
                int length = Math.min(a.path().size(), b.path().size());
                for(int i = 0; i<length; i++) {
                    int va = a.path().get(i);
                    int vb = b.path().get(i);
                    int c = Integer.compare(va, vb);
                    if(c == 0) continue;
                    return -c;
                }
                return -Integer.compare(a.path().size(), b.path().size());
            });
            for(var modification : modifications) {
                if(modification instanceof InstructionModification.RemoveInstruction) {
                    var beforePath = modification.path().subList(0, modification.path().size() - 1);
                    var instr = preNew;
                    for(var loc : beforePath) {
                        instr = instr.children().get(loc);
                    }
                    instr.children().remove((int) modification.path().get(modification.path().size() - 1));
                }else if(modification instanceof InstructionModification.MoveChildrenFromToNested moveInfo) {
                    var fromInstr = preNew;
                    for(var loc : moveInfo.from()) {
                        fromInstr = fromInstr.children().get(loc);
                    }
                    var fromInstrBottom = fromInstr;
                    while(fromInstrBottom.children().size() > 1) {
                        fromInstrBottom = fromInstrBottom.children().get(0);
                    }
                    var toInstr = preNew;
                    for(var loc : moveInfo.to()) {
                        toInstr = toInstr.children().get(loc);
                    }
                    if(!moveInfo.from().isEmpty()) {
                        var beforePath = moveInfo.from().subList(0, moveInfo.from().size() - 1);
                        var instr = preNew;
                        for(var loc : beforePath) {
                            instr = instr.children().get(loc);
                        }
                        instr.children().remove((int) moveInfo.from().get(moveInfo.from().size() - 1));
                    }
                    var commonReused = new HashSet<>(getUsedLocations(usedLocations, fromInstr));
                    var toUsed = new HashSet<>(getUsedLocations(usedLocations, toInstr));
                    var allUsed = new HashSet<>(usedLocations);
                    allUsed.addAll(commonReused);
                    allUsed.addAll(toUsed);
                    commonReused.retainAll(toUsed);
                    commonReused.removeAll(usedLocations);
                    int nextId = allUsed.stream().max(Comparator.naturalOrder()).orElse(0) + 1;
                    var remap = new HashMap<>(moveInfo.remapData());
                    for(var reused : commonReused) {
                        remap.putIfAbsent(reused, nextId++);
                    }
                    fromInstrBottom.children().addAll(toInstr.children());
                    toInstr.children().clear();
                    if(moveInfo.includeSelf()) {
                        toInstr.children().add(remapInstruction(fromInstr, remap));
                    }else {
                        for(var toRemap : fromInstr.children()) {
                            toInstr.children().add(remapInstruction(toRemap, remap));
                        }
                    }
                }
            }
        }
        if(result.isFlagSet(OptimizationResult.REMOVE_SELF_AND_INLINE_CHILDREN)) {
            if(result.isFlagSet(OptimizationResult.REPLACE_CHILDREN)) {
                return result.instructions();
            }else {
                return newChildren;
            }
        }else if(result.isFlagSet(OptimizationResult.REPLACE_CHILDREN)) {
            return List.of(new Instruction(input.type(), input.data(), result.instructions()));
        }
        return List.of(preNew);
    }

    private static Set<Integer> getUsedLocations(Set<Integer> base, Instruction instr) {
        HashSet<Integer> used = new HashSet<>(base);
        Stack<Instruction> stack = new Stack<>();
        stack.push(instr);
        while(!stack.isEmpty()) {
            var currentInstr = stack.pop();
            for (var value : currentInstr.data().values()) {
                if (value instanceof LocationData<?> loc && loc.local() != null) used.add(loc.local());
                if (value instanceof ScopeData scope) used.add(scope.id());
            }
            for(var child : currentInstr.children()) stack.push(child);
        }
        return used;
    }

    private static Instruction remapInstruction(Instruction instruction, Map<Integer, Integer> mappings) {
        ArrayList<Instruction> children = new ArrayList<>();
        for(var child : instruction.children()) {
            children.add(remapInstruction(child, mappings));
        }
        return new Instruction(instruction.type(), remapInstructionData(instruction.data(), mappings), children);
    }

    private static Map<String, Object> remapInstructionData(Map<String, Object> data, Map<Integer, Integer> mappings) {
        HashMap<String, Object> result = new HashMap<>();
        for(var entry : data.entrySet()) {
            if(entry.getValue() instanceof LocationData<?> value && value.local() != null) {
                result.put(entry.getKey(), new LocationData<>(mappings.getOrDefault(value.local(), value.local()), value.string(), value.integer()));
            }else if(entry.getValue() instanceof ScopeData scope) {
                result.put(entry.getKey(), new ScopeData(mappings.getOrDefault(scope.id(), scope.id())));
            }else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

}

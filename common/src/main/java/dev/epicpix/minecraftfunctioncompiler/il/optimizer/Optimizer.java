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
            result.addAll(applyOptimizationInner(instr, InstructionPath.emptyAbsolute(), instr, Set.of(), optimization));
        }
        return result;
    }

    private static List<Instruction> applyOptimizationInner(Instruction input, InstructionPath path, Instruction root, Set<Integer> usedLocations, Optimization optimization) {
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
            newChildren.addAll(applyOptimizationInner(child, path.addPathNode(index), root, Set.copyOf(usedLocations), optimization));
            index++;
        }
        var preNew = new Instruction(input.type(), input.data(), newChildren);
        var result = optimization.runOptimization(new OptimizationInput(preNew, path, root, usedLocations));
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
                int length = Math.min(a.path().depth(), b.path().depth());
                for(int i = 0; i<length; i++) {
                    int va = a.path().path().get(i);
                    int vb = b.path().path().get(i);
                    int c = Integer.compare(va, vb);
                    if(c == 0) continue;
                    return -c;
                }
                return -Integer.compare(a.path().depth(), b.path().depth());
            });
            for(int modIndex = 0; modIndex<modifications.size(); modIndex++) {
                var modification = modifications.get(modIndex);
                if(modification instanceof InstructionModification.RemoveInstruction) {
                    var instr = modification.path().pathExceptLast().traverse(preNew);
                    instr.children().remove(modification.path().lastIndex());
                    for(int i = modIndex + 1; i<modifications.size(); i++) {
                        modifications.add(i, modifications.remove(i).updatePathsAfterRemoval(modification.path()));
                    }
                }else if(modification instanceof InstructionModification.MoveChildrenFromToNested moveInfo) {
                    var fromInstr = moveInfo.from().traverse(preNew);
                    var toInstr = moveInfo.to().traverse(preNew);
                    if(!moveInfo.from().isEmptyPath()) {
                        var instr = moveInfo.from().pathExceptLast().traverse(preNew);
                        instr.children().remove(moveInfo.from().lastIndex());
                        for(int i = modIndex + 1; i<modifications.size(); i++) {
                            modifications.add(i, modifications.remove(i).updatePathsAfterRemoval(moveInfo.from()));
                        }
                    }
                    var oldToInstr = new ArrayList<>(toInstr.children());
                    toInstr.children().clear();
                    var fromInstrRemapped = new ArrayList<Instruction>();
                    if(moveInfo.includeSelf()) {
                        fromInstrRemapped.add(remapInstruction(fromInstr, moveInfo.remapData()));
                    }else {
                        for(var toRemap : fromInstr.children()) {
                            fromInstrRemapped.add(remapInstruction(toRemap, moveInfo.remapData()));
                        }
                    }
                    var fromInstrBottom = fromInstrRemapped.get(0);
                    if(moveInfo.stack()) {
                        while (fromInstrBottom.children().size() > 1) {
                            fromInstrBottom = fromInstrBottom.children().get(0);
                        }
                    }
                    fromInstrBottom.children().addAll(oldToInstr);
                    toInstr.children().addAll(fromInstrRemapped);
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

    public static Set<Integer> getUsedLocations(Set<Integer> base, InstructionPath path, Instruction instr) {
        HashSet<Integer> used = new HashSet<>(base);
        for(var v : path.path()) {
            for (var value : instr.data().values()) {
                if (value instanceof LocationData<?> loc && loc.local() != null) used.add(loc.local());
                if (value instanceof ScopeData scope) used.add(scope.id());
            }
            instr = instr.children().get(v);
        }
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

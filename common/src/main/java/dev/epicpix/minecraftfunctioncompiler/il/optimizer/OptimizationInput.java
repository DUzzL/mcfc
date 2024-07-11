package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;

public record OptimizationInput(Instruction instruction, List<Integer> thisPath, Instruction root) {

    public boolean anyChildrenIn(LocatedInstruction in, Function<Instruction, Boolean> func) {
        Stack<Instruction> instructions = new Stack<>();
        instructions.push(in.instruction());
        while(!instructions.isEmpty()) {
            var locInstr = instructions.pop();
            if(func.apply(locInstr)) return true;
            for(var child : locInstr.children()) {
                instructions.push(child);
            }
        }
        return false;
    }

    public boolean anyChildren(Function<Instruction, Boolean> func) {
        Stack<Instruction> instructions = new Stack<>();
        for(var child : instruction.children()) {
            instructions.push(child);
        }
        while(!instructions.isEmpty()) {
            var locInstr = instructions.pop();
            if(func.apply(locInstr)) return true;
            for(var child : locInstr.children()) {
                instructions.push(child);
            }
        }
        return false;
    }

    public List<LocatedInstruction> collectChildren(Function<LocatedInstruction, Boolean> func) {
        Stack<LocatedInstruction> instructions = new Stack<>();
        {
            int index = 0;
            for(var child : instruction.children()) {
                instructions.push(new LocatedInstruction(List.of(index), child, false));
                index++;
            }
        }
        List<LocatedInstruction> result = List.of();
        while(!instructions.isEmpty()) {
            var locInstr = instructions.pop();
            if(func.apply(locInstr)) {
                if(result.isEmpty()) {
                    result = new ArrayList<>();
                }
                result.add(locInstr);
            }
            int index = 0;
            for(var child : locInstr.instruction().children()) {
                instructions.push(new LocatedInstruction(addPathIndex(locInstr.path(), index), child, false));
                index++;
            }
        }
        return result;
    }

    public List<LocatedInstruction> collectInstructionsFromPath(Function<LocatedInstruction, Boolean> func) {
        List<LocatedInstruction> result = List.of();
        var locInstr = new LocatedInstruction(List.of(), root, true);
        for(int index : thisPath) {
            if(func.apply(locInstr)) {
                if(result.isEmpty()) {
                    result = new ArrayList<>();
                }
                result.add(locInstr);
            }
            locInstr = new LocatedInstruction(addPathIndex(locInstr.path(), index), locInstr.instruction().children().get(index), true);
        }
        return result;
    }

    public boolean anyInstructionFromPath(Function<LocatedInstruction, Boolean> func) {
        var locInstr = new LocatedInstruction(List.of(), root, true);
        for(int index : thisPath) {
            if (func.apply(locInstr)) {
                return true;
            }
            locInstr = new LocatedInstruction(addPathIndex(locInstr.path(), index), locInstr.instruction().children().get(index), true);
        }
        return false;
    }

    public LocatedInstruction getChild(Function<LocatedInstruction, Boolean> func) {
        Stack<LocatedInstruction> instructions = new Stack<>();
        {
            int index = 0;
            for(var child : instruction.children()) {
                instructions.push(new LocatedInstruction(List.of(index), child, false));
                index++;
            }
        }
        while(!instructions.isEmpty()) {
            var locInstr = instructions.pop();
            if(func.apply(locInstr)) {
                return locInstr;
            }
            int index = 0;
            for(var child : locInstr.instruction().children()) {
                instructions.push(new LocatedInstruction(addPathIndex(locInstr.path(), index), child, false));
                index++;
            }
        }
        return null;
    }

    public LocatedInstruction getInstructionDefinitionInPath(List<Integer> path, LocationData<?> loc) {
        var current = root;
        int index = 0;
        for(var v : thisPath) {
            if(current.data().containsValue(loc)) return new LocatedInstruction(thisPath.subList(0, index), current, true);
            current = current.children().get(v);
            index++;
        }
        index = 0;
        for(var v : path) {
            if(current.data().containsValue(loc)) return new LocatedInstruction(path.subList(0, index), current, false);
            current = current.children().get(v);
            index++;
        }
        if(current.data().containsValue(loc)) return new LocatedInstruction(path, current, false);
        throw new IllegalStateException("Failed to find definition for: " + loc);
    }

    public LocatedInstruction getByPath(List<Integer> path) {
        var current = instruction;
        for(var v : path) {
            current = current.children().get(v);
        }
        return new LocatedInstruction(path, current, false);
    }

    public LocatedInstruction getByAbsolutePath(List<Integer> path) {
        var current = root;
        for(var v : path) {
            current = current.children().get(v);
        }
        return new LocatedInstruction(path, current, true);
    }

    public LocatedInstruction getDirectChild(LocatedInstruction locInstr) {
        if(!locInstr.absolute() && !locInstr.path().isEmpty()) {
            return getByPath(locInstr.path().subList(0, 1));
        }
        throw new IllegalArgumentException("Cannot get direct child of " + locInstr);
    }

    public static List<Integer> addPathIndex(List<Integer> list, int index) {
        var newPath = new ArrayList<>(list);
        newPath.add(index);
        return newPath;
    }

}

package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;
import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import java.util.*;
import java.util.function.Function;

public record OptimizationInput(Instruction instruction, InstructionPath thisPath, Instruction root) {

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
                instructions.push(new LocatedInstruction(InstructionPath.fromSingleRelative(index), child));
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
                instructions.push(new LocatedInstruction(locInstr.path().addPathNode(index), child));
                index++;
            }
        }
        return result;
    }

    public List<LocatedInstruction> collectInstructionsFromPath(Function<LocatedInstruction, Boolean> func) {
        List<LocatedInstruction> result = List.of();
        var locInstr = new LocatedInstruction(InstructionPath.emptyAbsolute(), root);
        for(int index : thisPath.path()) {
            if(func.apply(locInstr)) {
                if(result.isEmpty()) {
                    result = new ArrayList<>();
                }
                result.add(locInstr);
            }
            locInstr = locInstr.getIndexed(index);
        }
        return result;
    }

    public boolean anyInstructionFromPath(Function<LocatedInstruction, Boolean> func) {
        var locInstr = new LocatedInstruction(InstructionPath.emptyAbsolute(), root);
        for(int index : thisPath.path()) {
            if (func.apply(locInstr)) {
                return true;
            }
            locInstr = locInstr.getIndexed(index);
        }
        return false;
    }

    public LocatedInstruction getChild(Function<LocatedInstruction, Boolean> func) {
        Stack<LocatedInstruction> instructions = new Stack<>();
        {
            int index = 0;
            for(var child : instruction.children()) {
                instructions.push(new LocatedInstruction(InstructionPath.fromSingleRelative(index), child));
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
                instructions.push(new LocatedInstruction(locInstr.path().addPathNode(index), child));
                index++;
            }
        }
        return null;
    }

    public LocatedInstruction getInstructionDefinitionInPath(InstructionPath path, LocationData<?> loc) {
        Instruction current;
        if(path.absolute()) {
            current = root;
            int index = 0;
            for(var v : thisPath.path()) {
                if(current.data().containsValue(loc)) return new LocatedInstruction(thisPath.subPath(0, index), current);
                current = current.children().get(v);
                index++;
            }
        }else {
            current = instruction;
        }
        int index = 0;
        for(var v : path.path()) {
            if(current.data().containsValue(loc)) return new LocatedInstruction(path.subPath(0, index), current);
            current = current.children().get(v);
            index++;
        }
        if(current.data().containsValue(loc)) return new LocatedInstruction(path, current);
        throw new IllegalStateException("Failed to find definition for: " + loc);
    }

    public LocatedInstruction getByPath(InstructionPath path) {
        var current = path.absolute() ? root : instruction;
        for(var v : path.path()) {
            current = current.children().get(v);
        }
        return new LocatedInstruction(path, current);
    }

    public LocatedInstruction getDirectChild(LocatedInstruction locInstr) {
        if(!locInstr.path().absolute() && !locInstr.path().path().isEmpty()) {
            return getByPath(locInstr.path().subPath(0, 1));
        }
        throw new IllegalArgumentException("Cannot get direct child of " + locInstr);
    }

}

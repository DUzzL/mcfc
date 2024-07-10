package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;

public record OptimizationInput(Instruction instruction) {

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
                instructions.push(new LocatedInstruction(List.of(index), child));
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
                instructions.push(new LocatedInstruction(addPathIndex(locInstr.path(), index), child));
                index++;
            }
        }
        return result;
    }

    public LocatedInstruction getByPath(List<Integer> path) {
        var current = instruction;
        for(var v : path) {
            current = current.children().get(v);
        }
        return new LocatedInstruction(path, current);
    }

    public static List<Integer> addPathIndex(List<Integer> list, int index) {
        var newPath = new ArrayList<>(list);
        newPath.add(index);
        return newPath;
    }

}

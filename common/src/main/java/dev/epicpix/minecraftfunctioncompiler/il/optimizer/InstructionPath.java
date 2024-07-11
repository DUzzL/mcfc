package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.Instruction;

import java.util.ArrayList;
import java.util.List;

public record InstructionPath(List<Integer> path, boolean absolute) {
    public static InstructionPath fromSingleRelative(int index) {
        return new InstructionPath(List.of(index), false);
    }
    public static InstructionPath emptyRelative() {
        return new InstructionPath(List.of(), false);
    }
    public static InstructionPath emptyAbsolute() {
        return new InstructionPath(List.of(), true);
    }

    public InstructionPath subPath(int start, int end) {
        if(start == 0) return new InstructionPath(path.subList(0, end), absolute);
        return new InstructionPath(path.subList(start, end), false);
    }

    public InstructionPath pathExceptLast() {
        return subPath(0, depth() - 1);
    }

    public InstructionPath addPathNode(int index) {
        var newPath = new ArrayList<Integer>(path.size() + 1);
        newPath.addAll(path);
        newPath.add(index);
        return new InstructionPath(newPath, absolute);
    }

    public int depth() {
        return path.size();
    }

    public boolean isEmptyPath() {
        return path.isEmpty();
    }

    public Instruction traverse(Instruction from) {
        var fromInstr = from;
        for(var loc : path) {
            fromInstr = fromInstr.children().get(loc);
        }
        return fromInstr;
    }

    public int lastIndex() {
        return path.get(path.size() - 1);
    }

    public InstructionPath updatePathAfterRemoval(InstructionPath removedPath) {
        if(removedPath.depth() > depth()) return this;
        for(int i = 0; i<removedPath.depth() - 1; i++) {
            if(!removedPath.path().get(i).equals(path().get(i))) return this;
        }
        int last = removedPath.lastIndex();
        int tLast = path().get(removedPath.depth() - 1);
        if(tLast > last) {
            ArrayList<Integer> newList = new ArrayList<>(path());
            int value = newList.remove(removedPath.depth() - 1);
            newList.add(removedPath.depth() - 1, value - 1);
            return new InstructionPath(newList, absolute);
        }
        return this;
    }
}

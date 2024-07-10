package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import dev.epicpix.minecraftfunctioncompiler.il.LocationData;

import java.util.List;
import java.util.Map;

public interface InstructionModification {
    List<Integer> path();
    int priority();

    record RemoveInstruction(List<Integer> path, int priority) implements InstructionModification {
        public RemoveInstruction(List<Integer> path) {
            this(path, 0);
        }

        public List<Integer> path() {
            return path;
        }

        public int priority() {
            return priority;
        }
    }

    record MoveChildrenFromToNested(List<Integer> from, List<Integer> to, Map<Integer, Integer> remapData, boolean includeSelf, int priority) implements InstructionModification {
        public MoveChildrenFromToNested(List<Integer> from, List<Integer> to, Map<Integer, Integer> remapData, boolean includeSelf) {
            this(from, to, remapData, includeSelf, 0);
        }

        public List<Integer> path() {
            return to;
        }

        public int priority() {
            return priority;
        }
    }

}

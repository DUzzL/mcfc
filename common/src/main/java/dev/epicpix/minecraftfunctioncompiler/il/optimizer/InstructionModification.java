package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import java.util.Map;

public interface InstructionModification {
    InstructionPath path();
    InstructionModification updatePathsAfterRemoval(InstructionPath removed);
    int priority();

    record RemoveInstruction(InstructionPath path, int priority) implements InstructionModification {
        public RemoveInstruction(InstructionPath path) {
            this(path, 0);
        }

        public InstructionPath path() {
            return path;
        }

        public InstructionModification updatePathsAfterRemoval(InstructionPath removed) {
            return new RemoveInstruction(path.updatePathAfterRemoval(removed), priority);
        }

        public int priority() {
            return priority;
        }
    }

    record MoveChildrenFromToNested(InstructionPath from, InstructionPath to, Map<Integer, Integer> remapData, boolean includeSelf, boolean stack, int priority) implements InstructionModification {
        public MoveChildrenFromToNested(InstructionPath from, InstructionPath to, Map<Integer, Integer> remapData, boolean includeSelf, boolean stack) {
            this(from, to, remapData, includeSelf, stack, 0);
        }

        public InstructionPath path() {
            return to;
        }

        public InstructionModification updatePathsAfterRemoval(InstructionPath removed) {
            return new MoveChildrenFromToNested(from.updatePathAfterRemoval(removed), to.updatePathAfterRemoval(removed), remapData, includeSelf, stack, priority);
        }

        public int priority() {
            return priority;
        }
    }

}

package dev.epicpix.minecraftfunctioncompiler.il;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record Instruction(InstructionType type, Map<String, Object> data, ArrayList<Instruction> children) {

    public Instruction(InstructionType type, Map<String, Object> data, List<Instruction> children) {
        this(type, data, new ArrayList<>(children));
    }

    public Instruction(InstructionType type, Map<String, Object> data) {
        this(type, data, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public Instruction with(Instruction instruction) {
        children.add(instruction);
        return this;
    }

    public Instruction withChildren(ArrayList<Instruction> children) {
        return new Instruction(type, data, children);
    }

    public int countInstructions() {
        int res = 1;
        for(var child : children) res += child.countInstructions();
        return res;
    }

    public static int countInstructions(List<Instruction> instructions) {
        int res = 0;
        for(var instr : instructions) res += instr.countInstructions();
        return res;
    }

    public static Map<String, Object> remapInstructionData(Map<String, Object> data, Map<Integer, Integer> mappings) {
        HashMap<String, Object> result = new HashMap<>();
        for(var entry : data.entrySet()) {
            if(entry.getValue() instanceof LocationData<?> value && value.local() != null) {
                result.put(entry.getKey(), new LocationData<>(mappings.getOrDefault(value.local(), value.local()), value.string(), value.integer()));
            }else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public static void printInstructionList(List<Instruction> instructions, Consumer<String> log, int currentDepth) {
        for(var instruction : instructions) {
            log.accept("-".repeat(currentDepth) + instruction.type + " - " + instruction.data);
            printInstructionList(instruction.children, log, currentDepth + 1);
        }
    }
}

package dev.epicpix.minecraftfunctioncompiler.il;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    public static void printInstructionList(List<Instruction> instructions, Consumer<String> lineConsumer) {
        for(var instr : instructions) printInstruction(instr, (data, ignored) -> lineConsumer.accept(data));
    }

    private static void printInstruction(Instruction instruction, BiConsumer<String, Boolean> writer) {
        var entries = new ArrayList<>(instruction.data.entrySet());
        entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
        String args = entries.isEmpty() ? "" : " -> { " + entries.stream().map(it -> it.getKey() + "=" + (it.getValue() instanceof String str ? "\"" + str.translateEscapes() + "\"" : it.getValue())).collect(Collectors.joining(", ")) + " }";

        writer.accept(instruction.type.name() + args, true);

        ArrayList<Instruction> instructions = instruction.children;
        for(int i = 0; i < instructions.size(); i++) {
            var child = instructions.get(i);
            boolean hasNext = i + 1 < instructions.size();
            printInstruction(child, (data, first) -> {
                if(!hasNext) {
                    if(first) {
                        writer.accept("┗━ " + data, false);
                    }else {
                        writer.accept("   " + data, false);
                    }
                }else {
                    if(first) {
                        writer.accept("┣━ " + data, false);
                    }else {
                        writer.accept("┃  " + data, false);
                    }
                }
            });
        }
    }
}

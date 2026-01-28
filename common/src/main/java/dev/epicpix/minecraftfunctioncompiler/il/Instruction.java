package dev.epicpix.minecraftfunctioncompiler.il;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import it.unimi.dsi.fastutil.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record Instruction(InstructionType type, @Nullable Object[] data) {

    @Override
    public String toString() {
        return "Instruction{type=" + type + ", data=" + Arrays.toString(data) + "}";
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if(o == null || getClass() != o.getClass()) return false;
        Instruction that = (Instruction) o;
        return type == that.type && Arrays.deepEquals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, Arrays.hashCode(data));
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(int index) {
        return (T) data[index];
    }

    public <T> @Nullable T get(String key) {
        return get(type.getArgumentIndex(key));
    }

    public List<Pair<String, Object>> namedEntries() {
        var entries = new ArrayList<Pair<String, Object>>(data.length);
        for(int i = 0; i<data.length; i++) {
            entries.add(Pair.of(type.argumentNames()[i], data[i]));
        }
        return entries;
    }

    public boolean containsValue(Object value) {
        for(var d : data) {
            if(Objects.equals(d, value)) return true;
        }
        return false;
    }

    public Optional<@Nullable Object[]> optReplaceData(Object src, Object dst) {
        Optional<@Nullable Object[]> replacedData = Optional.empty();
        for(int d = 0; d<data.length; d++) {
            var value = data[d];
            if(Objects.equals(value, src)) {
                if(replacedData.isPresent()) {
                    replacedData.get()[d] = dst;
                }else {
                    replacedData = Optional.of(data.clone());
                }
            }
        }
        return replacedData;
    }

    public Optional<@Nullable Object[]> optReplaceDataIf(Predicate<@Nullable Object> check, Object dst) {
        Optional<@Nullable Object[]> replacedData = Optional.empty();
        for(int d = 0; d<data.length; d++) {
            if(check.test(data[d])) {
                if(replacedData.isPresent()) {
                    replacedData.get()[d] = dst;
                }else {
                    replacedData = Optional.of(data.clone());
                }
            }
        }
        return replacedData;
    }

    public static List<Instruction> findTyped(List<Instruction> input, InstructionType type) {
        List<Instruction> result = List.of();
        for(var instr : input) {
            if(instr.type == type) {
                if(result.isEmpty()) {
                    result = new ArrayList<>();
                }
                result.add(instr);
            }
        }
        return result;
    }

    public static void printInstructions(List<Instruction> instructions, Consumer<String> lineConsumer) {
        var entries = new ArrayList<Pair<String, Object>>();
        for(var instruction : instructions) {
            for(int i = 0; i<instruction.data.length; i++) {
                entries.add(Pair.of(instruction.type.argumentNames()[i], instruction.data[i]));
            }
            entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            String args = entries.isEmpty() ? "" : " -> { " + entries.stream().map(it -> it.first() + "=" + (it.second() instanceof String str ? "\"" + str.translateEscapes() + "\"" : it.second())).collect(Collectors.joining(", ")) + " }";

            lineConsumer.accept(instruction.type.name() + args);

            entries.clear();
        }
    }

    public static JsonArray generateInstructionsJson(List<Instruction> instructions) {
        var entries = new JsonArray();
        for(var instruction : instructions) {
            var entry = new JsonObject();
            entry.addProperty("name", instruction.type.name());
            var data = new JsonArray();
            for(int i = 0; i<instruction.data.length; i++) {
                var dEntry = new JsonArray();
                dEntry.add(instruction.type.argumentNames()[i]);
                var value = instruction.data[i];
                dEntry.add(serializeType(value));
                data.add(dEntry);
            }
            entry.add("data", data);
            entries.add(entry);
        }
        return entries;
    }

    private static JsonObject serializeType(@Nullable Object value) {
        JsonObject object = new JsonObject();
        switch(value) {
            case null -> object.addProperty("type", "null");
            case String val -> {
                object.addProperty("type", "string");
                object.addProperty("value", val);
            }
            case Boolean val -> {
                object.addProperty("type", "boolean");
                object.addProperty("value", val);
            }
            case Byte val -> {
                object.addProperty("type", "byte");
                object.addProperty("value", val);
            }
            case Short val -> {
                object.addProperty("type", "short");
                object.addProperty("value", val);
            }
            case Integer val -> {
                object.addProperty("type", "int");
                object.addProperty("value", val);
            }
            case Long val -> {
                object.addProperty("type", "long");
                object.addProperty("value", val);
            }
            case Double val -> {
                object.addProperty("type", "double");
                object.addProperty("value", val);
            }
            case Float val -> {
                object.addProperty("type", "float");
                object.addProperty("value", val);
            }
            case LocationData<?>(Integer local, Object contentType) -> {
                object.addProperty("type", "register");
                if(local != null) {
                    object.addProperty("local", local);
                }else {
                    object.add("object", serializeType(contentType));
                }
            }
            case LabelData(int id) -> {
                object.addProperty("type", "label");
                object.addProperty("id", id);
            }
            case List<?> val -> {
                object.addProperty("type", "list");
                JsonArray content = new JsonArray();
                for(var v : val) {
                    content.add(serializeType(v));
                }
                object.add("content", content);
            }
            case DataLocation(String namespace, String path) -> {
                object.addProperty("type", "location");
                object.addProperty("namespace", namespace);
                object.addProperty("path", path);
            }
            default -> {
                object.addProperty("type", "unknown");
                object.addProperty("class", value.getClass().getCanonicalName());
                object.addProperty("value_string", value.toString());
            }
        }
        return object;
    }
}

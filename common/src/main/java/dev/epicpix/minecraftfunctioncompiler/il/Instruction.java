package dev.epicpix.minecraftfunctioncompiler.il;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.data.DataLocation;
import dev.epicpix.minecraftfunctioncompiler.util.Pair;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record Instruction(InstructionType type, Object[] data) {

    @Override
    public String toString() {
        return "Instruction{type=" + type + ", data=" + Arrays.toString(data) + "}";
    }

    @Override
    public boolean equals(Object o) {
        if(o == null || getClass() != o.getClass()) return false;
        Instruction that = (Instruction) o;
        return Objects.equals(type, that.type) && Arrays.deepEquals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, Arrays.hashCode(data));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(int index) {
        return (T) data[index];
    }

    public <T> T get(String key) {
        return get(type.getArgumentIndex(key));
    }

    public List<Pair<String, Object>> namedEntries() {
        var entries = new ArrayList<Pair<String, Object>>(data.length);
        for(int i = 0; i<data.length; i++) {
            entries.add(new Pair<>(type.argumentNames()[i], data[i]));
        }
        return entries;
    }

    public boolean containsValue(Object value) {
        for(var d : data) {
            if(Objects.equals(d, value)) return true;
        }
        return false;
    }

    public Object[] optReplaceData(Object src, Object dst) {
        Object[] replacedData = null;
        for(int d = 0; d<data.length; d++) {
            var value = data[d];
            if(Objects.equals(value, src)) {
                if(replacedData == null) replacedData = data.clone();
                replacedData[d] = dst;
            }
        }
        return replacedData;
    }

    public Object[] optReplaceDataIf(Predicate<Object> check, Object dst) {
        Object[] replacedData = null;
        for(int d = 0; d<data.length; d++) {
            if(check.test(data[d])) {
                if(replacedData == null) replacedData = data.clone();
                replacedData[d] = dst;
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
                entries.add(new Pair<>(instruction.type.argumentNames()[i], instruction.data[i]));
            }
            entries.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            String args = entries.isEmpty() ? "" : " -> { " + entries.stream().map(it -> it.first() + "=" + (it.second() instanceof String str ? "\"" + str.translateEscapes() + "\"" : it.second())).collect(Collectors.joining(", ")) + " }";

            lineConsumer.accept("┗━ " + instruction.type.name() + args);

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

    private static JsonObject serializeType(Object value) {
        JsonObject object = new JsonObject();
        if(value == null) {
            object.addProperty("type", "null");
        }else if(value instanceof String val) {
            object.addProperty("type", "string");
            object.addProperty("value", val);
        }else if(value instanceof Boolean val) {
            object.addProperty("type", "boolean");
            object.addProperty("value", val);
        }else if(value instanceof Byte val) {
            object.addProperty("type", "byte");
            object.addProperty("value", val);
        }else if(value instanceof Short val) {
            object.addProperty("type", "short");
            object.addProperty("value", val);
        }else if(value instanceof Integer val) {
            object.addProperty("type", "int");
            object.addProperty("value", val);
        }else if(value instanceof Long val) {
            object.addProperty("type", "long");
            object.addProperty("value", val);
        }else if(value instanceof Double val) {
            object.addProperty("type", "double");
            object.addProperty("value", val);
        }else if(value instanceof Float val) {
            object.addProperty("type", "float");
            object.addProperty("value", val);
        }else if(value instanceof LocationData<?> val) {
            object.addProperty("type", "register");
            if(val.local() != null) {
                object.addProperty("local", val.local());
            }else {
                object.add("object", serializeType(val.object()));
            }
        }else if(value instanceof LabelData val) {
            object.addProperty("type", "label");
            object.addProperty("id", val.id());
        }else if(value instanceof List<?> val) {
            object.addProperty("type", "list");
            JsonArray content = new JsonArray();
            for(var v : val) {
                content.add(serializeType(v));
            }
            object.add("content", content);
        }else if(value instanceof DataLocation val) {
            object.addProperty("type", "location");
            object.addProperty("namespace", val.namespace());
            object.addProperty("path", val.path());
        }else {
            object.addProperty("type", "unknown");
            object.addProperty("class", value.getClass().getCanonicalName());
            object.addProperty("value_string", value.toString());
        }
        return object;
    }
}

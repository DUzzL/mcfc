package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.stream.Collectors;

public class BytecodeFormatter {

    private static String formatLabel(BytecodeLabel label) {
        return "l" + label.id();
    }

    private static String formatTarget(InstructionTarget instructionTarget, List<BytecodeValue> args) {
        if(instructionTarget instanceof InstructionTarget.FieldTarget target) {
            if(target.isStatic()) {
                return target.className().replace('/', '.') + "." + target.name();
            }else {
                return formatValue(args.getFirst()) + "." + target.name();
            }
        }else if(instructionTarget instanceof InstructionTarget.FunctionTarget target) {
            if(target.isStatic()) {
                var callArgs = args.stream().map(BytecodeFormatter::formatValue).collect(Collectors.joining(", "));
                return target.className().replace('/', '.') + "." + target.name() + "(" + callArgs + ")";
            }else {
                var callArgs = args.stream().skip(1).map(BytecodeFormatter::formatValue).collect(Collectors.joining(", "));
                return formatValue(args.getFirst()) + "." + target.name() + "(" + callArgs + ")";
            }
        }else {
            throw new IllegalArgumentException("Unknown target: " + instructionTarget);
        }
    }

    private static String formatType(Type type) {
        return type.getClassName();
    }

    private static String formatValue(BytecodeValue bytecodeValue) {
        return switch(bytecodeValue) {
            case BytecodeValue.NullValue _ -> "<null>";
            case BytecodeValue.IntegerValue(int value) -> Integer.toString(value);
            case BytecodeValue.LongValue(long value) -> value + "L";
            case BytecodeValue.FloatValue(float value) -> value + "F";
            case BytecodeValue.DoubleValue(double value) -> value + "D";
            case BytecodeValue.StringValue(String value) -> "\"" + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\"";
            case BytecodeValue.Parameter(int index, Type type) -> "parameter<" + index + "," + formatType(type) + ">";
            case BytecodeRegister(int reg) -> "r" + reg;
        };
    }

    public static String formatInstruction(BytecodeInstruction instr) {
        return switch(instr) {
            case BytecodeInstruction.RegisterValue(InstructionRVType type, BytecodeRegister result, BytecodeValue source) ->
                switch(type) {
                    case SET -> formatValue(result) + " = " + formatValue(source);
                };
            case BytecodeInstruction.RegisterValueValue(InstructionRVVType type, BytecodeRegister result, BytecodeValue sourceA, BytecodeValue sourceB) ->
                switch(type) {
                    case ADD -> formatValue(result) + " = " + formatValue(sourceA) + " + " + formatValue(sourceB);
                    case SUB -> formatValue(result) + " = " + formatValue(sourceA) + " - " + formatValue(sourceB);
                    case MUL -> formatValue(result) + " = " + formatValue(sourceA) + " * " + formatValue(sourceB);
                    case DIV -> formatValue(result) + " = " + formatValue(sourceA) + " / " + formatValue(sourceB);
                    case MOD -> formatValue(result) + " = " + formatValue(sourceA) + " % " + formatValue(sourceB);
                    case SHL -> formatValue(result) + " = " + formatValue(sourceA) + " << " + formatValue(sourceB);
                    case SHR -> formatValue(result) + " = " + formatValue(sourceA) + " >> " + formatValue(sourceB);
                };
            case BytecodeInstruction.CreateArray(BytecodeRegister result, BytecodeValue length, Type contentType) -> formatValue(result) + " = new " + formatType(contentType) + "[" + formatValue(length) + "]";
            case BytecodeInstruction.SetArrayValue(BytecodeValue array, BytecodeValue index, BytecodeValue value) -> formatValue(array) + "[" + formatValue(index) + "]" + " = " + formatValue(value);
            case BytecodeInstruction.InstanceCheckJump(boolean ifIsInstance, BytecodeValue a, Type type, BytecodeLabel label) -> formatLabel(label) + " <- " + formatValue(a) + (ifIsInstance ? " " : " !") + "instanceof " + formatType(type);
            case BytecodeInstruction.ConditionalJump(JumpCondition condition, BytecodeValue a, BytecodeValue b, BytecodeLabel label) ->
                formatLabel(label) + " <- " + formatValue(a) + " " + switch(condition) {
                    case EQ -> "==";
                    case NE -> "!=";
                    case LT -> "<";
                    case LE -> "<=";
                    case GT -> ">";
                    case GE -> ">=";
                } + " " + formatValue(b);
            case BytecodeInstruction.Jump(BytecodeLabel label) -> formatLabel(label) + " <- true";
            case BytecodeInstruction.Label(BytecodeLabel label) -> formatLabel(label) + ":";
            case BytecodeInstruction.CallTarget(BytecodeRegister result, InstructionTarget target1, List<BytecodeValue> arguments) -> {
                if(target1 instanceof InstructionTarget.FieldTarget target) {
                    if(target.isWrite()) {
                        yield formatTarget(target, arguments) + " = " + formatValue(arguments.get(target.isStatic() ? 0 : 1));
                    }else {
                        yield formatValue(result) + " = " + formatTarget(target, arguments);
                    }
                }else if(target1 instanceof InstructionTarget.FunctionTarget target) {
                    yield formatValue(result) + " = " + formatTarget(target, arguments);
                }else {
                    throw new IllegalArgumentException("Unknown target: " + target1);
                }
            }
            case BytecodeInstruction.New(BytecodeRegister result, InstructionTarget target, List<BytecodeValue> arguments) -> {
                if(target instanceof InstructionTarget.FunctionTarget functionTarget && !functionTarget.isStatic() && functionTarget.isSpecial() && functionTarget.name().equals("<init>")) {
                    var callArgs = arguments.stream().map(BytecodeFormatter::formatValue).collect(Collectors.joining(", "));
                    yield formatValue(result) + " = new " + functionTarget.className().replace('/', '.') + "(" + callArgs + ")";
                }else {
                    throw new IllegalArgumentException("Unknown target: " + target);
                }
            }
            case BytecodeInstruction.Cast(BytecodeRegister result, BytecodeValue source, Type type) -> formatValue(result) + " = (" + formatType(type) + ") " + formatValue(source);
            case BytecodeInstruction.StringConcat(BytecodeRegister result, List<BytecodeValue> values) -> {
                var strValues = values.stream().map(BytecodeFormatter::formatValue).collect(Collectors.joining(" ++ "));
                yield formatValue(result) + " = ++ " + strValues;
            }
            case BytecodeInstruction.Return(BytecodeValue value) -> "return " + formatValue(value);
            case BytecodeInstruction.ReturnVoid _ -> "return";
        };
    }

    private static JsonObject valueToJson(BytecodeValue bytecodeValue) {
        JsonObject v = new JsonObject();
        switch(bytecodeValue) {
            case BytecodeValue.NullValue _ -> v.addProperty("type", "null");
            case BytecodeValue.IntegerValue(int value) -> {
                v.addProperty("type", "int");
                v.addProperty("value", value);
            }
            case BytecodeValue.LongValue(long value) -> {
                v.addProperty("type", "long");
                v.addProperty("value", value);
            }
            case BytecodeValue.FloatValue(float value) -> {
                v.addProperty("type", "float");
                v.addProperty("value", value);
            }
            case BytecodeValue.DoubleValue(double value) -> {
                v.addProperty("type", "double");
                v.addProperty("value", value);
            }
            case BytecodeValue.StringValue(String value) -> {
                v.addProperty("type", "string");
                v.addProperty("value", value);
            }
            case BytecodeValue.Parameter(int index, Type type) -> {
                v.addProperty("type", "parameter");
                v.addProperty("index", index);
                v.addProperty("descriptor", type.getDescriptor());
            }
            case BytecodeRegister(int reg) -> {
                v.addProperty("type", "register");
                v.addProperty("reg", reg);
            }
        }
        return v;
    }

    public static JsonObject instructionToJson(BytecodeInstruction instr) {
        JsonObject instruction = new JsonObject();
        switch(instr) {
            case BytecodeInstruction.RegisterValue(InstructionRVType type, BytecodeRegister result, BytecodeValue source) -> {
                switch(type) {
                    case SET -> instruction.addProperty("type", "set");
                }
                instruction.add("source", valueToJson(source));
                instruction.add("result", valueToJson(result));
            }
            case BytecodeInstruction.RegisterValueValue(InstructionRVVType type, BytecodeRegister result, BytecodeValue sourceA, BytecodeValue sourceB) -> {
                switch(type) {
                    case ADD -> instruction.addProperty("type", "add");
                    case SUB -> instruction.addProperty("type", "sub");
                    case MUL -> instruction.addProperty("type", "mul");
                    case DIV -> instruction.addProperty("type", "div");
                    case MOD -> instruction.addProperty("type", "mod");
                    case SHL -> instruction.addProperty("type", "shl");
                    case SHR -> instruction.addProperty("type", "shr");
                }
                instruction.add("source_a", valueToJson(sourceA));
                instruction.add("source_b", valueToJson(sourceB));
                instruction.add("result", valueToJson(result));
            }
            case BytecodeInstruction.CreateArray(BytecodeRegister result, BytecodeValue length, Type contentType) -> {
                instruction.addProperty("type", "new_array");
                instruction.addProperty("class", contentType.getDescriptor());
                instruction.add("length", valueToJson(length));
                instruction.add("result", valueToJson(result));
            }
            case BytecodeInstruction.SetArrayValue(BytecodeValue array, BytecodeValue index, BytecodeValue value) -> {
                instruction.addProperty("type", "set_array_value");
                instruction.add("array", valueToJson(array));
                instruction.add("index", valueToJson(index));
                instruction.add("value", valueToJson(value));
            }
            case BytecodeInstruction.InstanceCheckJump(boolean ifIsInstance, BytecodeValue a, Type type, BytecodeLabel label) -> {
                instruction.addProperty("type", "instance_check_jump");
                instruction.addProperty("label", label.id());
                instruction.addProperty("is", ifIsInstance);
                instruction.addProperty("class", type.getDescriptor());
                instruction.add("value", valueToJson(a));
            }
            case BytecodeInstruction.ConditionalJump(JumpCondition condition, BytecodeValue a, BytecodeValue b, BytecodeLabel label) -> {
                instruction.addProperty("type", "conditional_jump");
                instruction.addProperty("label", label.id());
                switch(condition) {
                    case EQ -> instruction.addProperty("condition", "equal");
                    case NE -> instruction.addProperty("condition", "not_equal");
                    case LT -> instruction.addProperty("condition", "less_than");
                    case LE -> instruction.addProperty("condition", "less_equal");
                    case GT -> instruction.addProperty("condition", "greater_than");
                    case GE -> instruction.addProperty("condition", "greater_equal");
                }
                instruction.add("value_a", valueToJson(a));
                instruction.add("value_b", valueToJson(b));
            }
            case BytecodeInstruction.Jump(BytecodeLabel label) -> {
                instruction.addProperty("type", "jump");
                instruction.addProperty("label", label.id());
            }
            case BytecodeInstruction.Label(BytecodeLabel label) -> {
                instruction.addProperty("type", "label");
                instruction.addProperty("label", label.id());
            }
            case BytecodeInstruction.CallTarget(BytecodeRegister result, InstructionTarget target, List<BytecodeValue> arguments) -> {
                instruction.addProperty("type", "call_target");
                if(target instanceof InstructionTarget.FieldTarget(
                    String className, String name, String descriptor, boolean isStatic, boolean isWrite
                )) {
                    instruction.addProperty("target_type", "field");
                    instruction.addProperty("class", className);
                    instruction.addProperty("name", name);
                    instruction.addProperty("descriptor", descriptor);
                    instruction.addProperty("is_static", isStatic);
                    instruction.addProperty("is_write", isWrite);
                    var args = new JsonArray();
                    for(var arg : arguments) {
                        args.add(valueToJson(arg));
                    }
                    instruction.add("args", args);
                    instruction.add("result", valueToJson(result));
                }else if(target instanceof InstructionTarget.FunctionTarget(String className, String name, String descriptor, boolean isStatic, boolean isInterface, boolean isSpecial, boolean noReordering)) {
                    instruction.addProperty("target_type", "method");
                    instruction.addProperty("class", className);
                    instruction.addProperty("name", name);
                    instruction.addProperty("descriptor", descriptor);
                    instruction.addProperty("is_static", isStatic);
                    instruction.addProperty("is_interface", isInterface);
                    instruction.addProperty("is_special", isSpecial);
                    instruction.addProperty("no_reordering", noReordering);
                    var args = new JsonArray();
                    for(var arg : arguments) {
                        args.add(valueToJson(arg));
                    }
                    instruction.add("args", args);
                    instruction.add("result", valueToJson(result));
                }else {
                    throw new IllegalArgumentException("Unknown target: " + target);
                }
            }
            case BytecodeInstruction.New(BytecodeRegister result, InstructionTarget target, List<BytecodeValue> arguments) -> {
                instruction.addProperty("type", "new_object");
                if(target instanceof InstructionTarget.FunctionTarget functionTarget && !functionTarget.isStatic() && functionTarget.isSpecial() && functionTarget.name().equals("<init>")) {
                    instruction.addProperty("class", functionTarget.className());
                    instruction.addProperty("descriptor", functionTarget.descriptor());
                    instruction.addProperty("no_reordering", functionTarget.noReordering());
                    var args = new JsonArray();
                    for(var arg : arguments) {
                        args.add(valueToJson(arg));
                    }
                    instruction.add("args", args);
                    instruction.add("result", valueToJson(result));
                }else {
                    throw new IllegalArgumentException("Unknown target: " + target);
                }
            }
            case BytecodeInstruction.Cast(BytecodeRegister result, BytecodeValue source, Type type) -> {
                instruction.addProperty("type", "cast");
                instruction.addProperty("class", type.getDescriptor());
                instruction.add("source", valueToJson(source));
                instruction.add("result", valueToJson(result));
            }
            case BytecodeInstruction.StringConcat(BytecodeRegister result, List<BytecodeValue> values) -> {
                instruction.addProperty("type", "string_concat");
                var args = new JsonArray();
                for(var arg : values) {
                    args.add(valueToJson(arg));
                }
                instruction.add("args", args);
                instruction.add("result", valueToJson(result));
            }
            case BytecodeInstruction.Return(BytecodeValue value) -> {
                instruction.addProperty("type", "return_value");
                instruction.add("value", valueToJson(value));
            }
            case BytecodeInstruction.ReturnVoid _ -> instruction.addProperty("type", "return_void");
        }
        return instruction;
    }

}

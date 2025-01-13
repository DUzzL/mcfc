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
                return formatValue(args.get(0)) + "." + target.name();
            }
        }else if(instructionTarget instanceof InstructionTarget.FunctionTarget target) {
            if(target.isStatic()) {
                var callArgs = args.stream().map(BytecodeFormatter::formatValue).collect(Collectors.joining(", "));
                return target.className().replace('/', '.') + "." + target.name() + "(" + callArgs + ")";
            }else {
                var callArgs = args.stream().skip(1).map(BytecodeFormatter::formatValue).collect(Collectors.joining(", "));
                return formatValue(args.get(0)) + "." + target.name() + "(" + callArgs + ")";
            }
        }else {
            throw new IllegalArgumentException("Unknown target: " + instructionTarget);
        }
    }

    private static String formatType(Type type) {
        return type.getClassName();
    }

    private static String formatValue(BytecodeValue value) {
        if(value instanceof BytecodeValue.NullValue) {
            return "<null>";
        }else if(value instanceof BytecodeValue.IntegerValue intVal) {
            return Integer.toString(intVal.value());
        }else if(value instanceof BytecodeValue.LongValue longVal) {
            return longVal.value() + "L";
        }else if(value instanceof BytecodeValue.FloatValue floatVal) {
            return floatVal.value() + "F";
        }else if(value instanceof BytecodeValue.DoubleValue doubleVal) {
            return doubleVal.value() + "D";
        }else if(value instanceof BytecodeValue.StringValue stringValue) {
            return "\"" + stringValue.value()
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\"", "\\\"") + "\"";
        }else if(value instanceof BytecodeValue.Parameter parameter) {
            return "parameter<" + parameter.index() + "," + formatType(parameter.type()) + ">";
        }else if(value instanceof BytecodeRegister register) {
            return "r" + register.reg();
        }else {
            throw new IllegalArgumentException("Unknown value: " + value);
        }
    }

    public static String formatInstruction(BytecodeInstruction instr) {
        if(instr instanceof BytecodeInstruction.RegisterValue v) {
            return switch(v.type()) {
                case SET -> formatValue(v.result()) + " = " + formatValue(v.source());
            };
        }else if(instr instanceof BytecodeInstruction.RegisterValueValue v) {
            return switch(v.type()) {
                case ADD -> formatValue(v.result()) + " = " + formatValue(v.sourceA()) + " + " + formatValue(v.sourceB());
                case SUB -> formatValue(v.result()) + " = " + formatValue(v.sourceA()) + " - " + formatValue(v.sourceB());
                case MUL -> formatValue(v.result()) + " = " + formatValue(v.sourceA()) + " * " + formatValue(v.sourceB());
                case SHL -> formatValue(v.result()) + " = " + formatValue(v.sourceA()) + " << " + formatValue(v.sourceB());
                case SHR -> formatValue(v.result()) + " = " + formatValue(v.sourceA()) + " >> " + formatValue(v.sourceB());
            };
        }else if(instr instanceof BytecodeInstruction.CreateArray v) {
            return formatValue(v.result()) + " = new " + formatType(v.contentType()) + "[" + formatValue(v.length()) + "]";
        }else if(instr instanceof BytecodeInstruction.SetArrayValue v) {
            return formatValue(v.array()) + "[" + formatValue(v.index()) + "]" + " = " + formatValue(v.value());
        }else if(instr instanceof BytecodeInstruction.InstanceCheckJump v) {
            return formatLabel(v.label()) + " <- " + formatValue(v.a()) + (v.ifIsInstance() ? " " : " !") + "instanceof " + formatType(v.type());
        }else if(instr instanceof BytecodeInstruction.ConditionalJump v) {
            return formatLabel(v.label()) + " <- " + formatValue(v.a()) + " " + switch(v.condition()) {
                case EQ -> "==";
                case NE -> "!=";
                case LT -> "<";
                case LE -> "<=";
                case GT -> ">";
                case GE -> ">=";
            } + " " + formatValue(v.b());
        }else if(instr instanceof BytecodeInstruction.Jump v) {
            return formatLabel(v.label()) + " <- true";
        }else if(instr instanceof BytecodeInstruction.Label v) {
            return formatLabel(v.label()) + ":";
        }else if(instr instanceof BytecodeInstruction.CallTarget v) {
            if(v.target() instanceof InstructionTarget.FieldTarget target) {
                if(target.isWrite()) {
                    return formatTarget(target, v.arguments()) + " = " + formatValue(v.arguments().get(target.isStatic() ? 0 : 1));
                }else {
                    return formatValue(v.result()) + " = " + formatTarget(target, v.arguments());
                }
            }else if(v.target() instanceof InstructionTarget.FunctionTarget target) {
                return formatValue(v.result()) + " = " + formatTarget(target, v.arguments());
            }else {
                throw new IllegalArgumentException("Unknown target: " + v.target());
            }
        }else if(instr instanceof BytecodeInstruction.New v) {
            if(v.target() instanceof InstructionTarget.FunctionTarget target && !target.isStatic() && target.isSpecial() && target.name().equals("<init>")) {
                var callArgs = v.arguments().stream().map(BytecodeFormatter::formatValue).collect(Collectors.joining(", "));
                return formatValue(v.result()) + " = new " + target.className().replace('/', '.') + "(" + callArgs + ")";
            }else {
                throw new IllegalArgumentException("Unknown target: " + v.target());
            }
        }else if(instr instanceof BytecodeInstruction.Cast v) {
            return formatValue(v.result()) + " = (" + formatType(v.type()) + ") " + formatValue(v.source());
        }else if(instr instanceof BytecodeInstruction.StringConcat v) {
            var values = v.values().stream().map(BytecodeFormatter::formatValue).collect(Collectors.joining(" ++ "));
            return formatValue(v.result()) + " = ++ " + values;
        }else if(instr instanceof BytecodeInstruction.Return v) {
            return "return " + formatValue(v.value());
        }else if(instr instanceof BytecodeInstruction.ReturnVoid) {
            return "return";
        }else {
            throw new IllegalArgumentException("Unknown instruction: " + instr);
        }
    }

    private static JsonObject valueToJson(BytecodeValue value) {
        JsonObject v = new JsonObject();
        if(value instanceof BytecodeValue.NullValue) {
            v.addProperty("type", "null");
        }else if(value instanceof BytecodeValue.IntegerValue intVal) {
            v.addProperty("type", "int");
            v.addProperty("value", intVal.value());
        }else if(value instanceof BytecodeValue.LongValue longVal) {
            v.addProperty("type", "long");
            v.addProperty("value", longVal.value());
        }else if(value instanceof BytecodeValue.FloatValue floatVal) {
            v.addProperty("type", "float");
            v.addProperty("value", floatVal.value());
        }else if(value instanceof BytecodeValue.DoubleValue doubleVal) {
            v.addProperty("type", "double");
            v.addProperty("value", doubleVal.value());
        }else if(value instanceof BytecodeValue.StringValue stringValue) {
            v.addProperty("type", "string");
            v.addProperty("value", stringValue.value());
        }else if(value instanceof BytecodeValue.Parameter parameter) {
            v.addProperty("type", "parameter");
            v.addProperty("index", parameter.index());
            v.addProperty("descriptor", parameter.type().getDescriptor());
        }else if(value instanceof BytecodeRegister register) {
            v.addProperty("type", "register");
            v.addProperty("reg", register.reg());
        }else {
            throw new IllegalArgumentException("Unknown value: " + value);
        }
        return v;
    }

    public static JsonObject instructionToJson(BytecodeInstruction instr) {
        JsonObject instruction = new JsonObject();
        if(instr instanceof BytecodeInstruction.RegisterValue v) {
            switch(v.type()) {
                case SET -> instruction.addProperty("type", "set");
            }
            instruction.add("source", valueToJson(v.source()));
            instruction.add("result", valueToJson(v.result()));
        }else if(instr instanceof BytecodeInstruction.RegisterValueValue v) {
            switch(v.type()) {
                case ADD -> instruction.addProperty("type", "add");
                case SUB -> instruction.addProperty("type", "sub");
                case MUL -> instruction.addProperty("type", "mul");
                case SHL -> instruction.addProperty("type", "shl");
                case SHR -> instruction.addProperty("type", "shr");
            }
            instruction.add("source_a", valueToJson(v.sourceA()));
            instruction.add("source_b", valueToJson(v.sourceB()));
            instruction.add("result", valueToJson(v.result()));
        }else if(instr instanceof BytecodeInstruction.CreateArray v) {
            instruction.addProperty("type", "new_array");
            instruction.addProperty("class", v.contentType().getDescriptor());
            instruction.add("length", valueToJson(v.length()));
            instruction.add("result", valueToJson(v.result()));
        }else if(instr instanceof BytecodeInstruction.SetArrayValue v) {
            instruction.addProperty("type", "set_array_value");
            instruction.add("array", valueToJson(v.array()));
            instruction.add("index", valueToJson(v.index()));
            instruction.add("value", valueToJson(v.value()));
        }else if(instr instanceof BytecodeInstruction.InstanceCheckJump v) {
            instruction.addProperty("type", "instance_check_jump");
            instruction.addProperty("label", v.label().id());
            instruction.addProperty("is", v.ifIsInstance());
            instruction.addProperty("class", v.type().getDescriptor());
            instruction.add("value", valueToJson(v.a()));
        }else if(instr instanceof BytecodeInstruction.ConditionalJump v) {
            instruction.addProperty("type", "conditional_jump");
            instruction.addProperty("label", v.label().id());
            switch(v.condition()) {
                case EQ -> instruction.addProperty("condition", "equal");
                case NE -> instruction.addProperty("condition", "not_equal");
                case LT -> instruction.addProperty("condition", "less_than");
                case LE -> instruction.addProperty("condition", "less_equal");
                case GT -> instruction.addProperty("condition", "greater_than");
                case GE -> instruction.addProperty("condition", "greater_equal");
            }
            instruction.add("value_a", valueToJson(v.a()));
            instruction.add("value_b", valueToJson(v.b()));
        }else if(instr instanceof BytecodeInstruction.Jump v) {
            instruction.addProperty("type", "jump");
            instruction.addProperty("label", v.label().id());
        }else if(instr instanceof BytecodeInstruction.Label v) {
            instruction.addProperty("type", "label");
            instruction.addProperty("label", v.label().id());
        }else if(instr instanceof BytecodeInstruction.CallTarget v) {
            instruction.addProperty("type", "call_target");
            if(v.target() instanceof InstructionTarget.FieldTarget target) {
                instruction.addProperty("target_type", "field");
                instruction.addProperty("class", target.className());
                instruction.addProperty("name", target.name());
                instruction.addProperty("descriptor", target.descriptor());
                instruction.addProperty("is_static", target.isStatic());
                instruction.addProperty("is_write", target.isWrite());
                var args = new JsonArray();
                for(var arg : v.arguments()) {
                    args.add(valueToJson(arg));
                }
                instruction.add("args", args);
                instruction.add("result", valueToJson(v.result()));
            }else if(v.target() instanceof InstructionTarget.FunctionTarget target) {
                instruction.addProperty("target_type", "method");
                instruction.addProperty("class", target.className());
                instruction.addProperty("name", target.name());
                instruction.addProperty("descriptor", target.descriptor());
                instruction.addProperty("is_static", target.isStatic());
                instruction.addProperty("is_interface", target.isInterface());
                instruction.addProperty("is_special", target.isSpecial());
                instruction.addProperty("no_reordering", target.noReordering());
                var args = new JsonArray();
                for(var arg : v.arguments()) {
                    args.add(valueToJson(arg));
                }
                instruction.add("args", args);
                instruction.add("result", valueToJson(v.result()));
            }else {
                throw new IllegalArgumentException("Unknown target: " + v.target());
            }
        }else if(instr instanceof BytecodeInstruction.New v) {
            instruction.addProperty("type", "new_object");
            if(v.target() instanceof InstructionTarget.FunctionTarget target && !target.isStatic() && target.isSpecial() && target.name().equals("<init>")) {
                instruction.addProperty("class", target.className());
                instruction.addProperty("descriptor", target.descriptor());
                instruction.addProperty("no_reordering", target.noReordering());
                var args = new JsonArray();
                for(var arg : v.arguments()) {
                    args.add(valueToJson(arg));
                }
                instruction.add("args", args);
                instruction.add("result", valueToJson(v.result()));
            }else {
                throw new IllegalArgumentException("Unknown target: " + v.target());
            }
        }else if(instr instanceof BytecodeInstruction.Cast v) {
            instruction.addProperty("type", "cast");
            instruction.addProperty("class", v.type().getDescriptor());
            instruction.add("source", valueToJson(v.source()));
            instruction.add("result", valueToJson(v.result()));
        }else if(instr instanceof BytecodeInstruction.StringConcat v) {
            instruction.addProperty("type", "string_concat");
            var args = new JsonArray();
            for(var arg : v.values()) {
                args.add(valueToJson(arg));
            }
            instruction.add("args", args);
            instruction.add("result", valueToJson(v.result()));
        }else if(instr instanceof BytecodeInstruction.Return v) {
            instruction.addProperty("type", "return_value");
            instruction.add("value", valueToJson(v.value()));
        }else if(instr instanceof BytecodeInstruction.ReturnVoid) {
            instruction.addProperty("type", "return_void");
        }else {
            throw new IllegalArgumentException("Unknown instruction: " + instr);
        }
        return instruction;
    }

}

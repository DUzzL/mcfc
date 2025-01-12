package dev.epicpix.minecraftfunctioncompiler.emitter.bytecode;

import dev.epicpix.minecraftfunctioncompiler.emitter.InstructionTarget;

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

    private static String formatClass(Class<?> clazz) {
        if(clazz.isArray()) {
            return formatClass(clazz.getComponentType()) + "[]";
        }else {
            return clazz.getName();
        }
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
            return "parameter<" + parameter.index() + "," + formatClass(parameter.clazz()) + ">";
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
            };
        }else if(instr instanceof BytecodeInstruction.CreateArray v) {
            return formatValue(v.result()) + " = new " + formatClass(v.contentType()) + "[" + formatValue(v.length()) + "]";
        }else if(instr instanceof BytecodeInstruction.SetArrayValue v) {
            return formatValue(v.array()) + "[" + formatValue(v.index()) + "]" + " = " + formatValue(v.value());
        }else if(instr instanceof BytecodeInstruction.InstanceCheckJump v) {
            return formatLabel(v.label()) + " <- " + formatValue(v.a()) + (v.ifIsInstance() ? " " : " !") + "instanceof " + formatClass(v.clazz());
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
            return formatValue(v.result()) + " = (" + formatClass(v.clazz()) + ") " + formatValue(v.source());
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

}
